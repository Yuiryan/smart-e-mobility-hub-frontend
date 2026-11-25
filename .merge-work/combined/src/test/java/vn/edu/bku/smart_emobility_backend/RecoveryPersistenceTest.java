package vn.edu.bku.smart_emobility_backend;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import tools.jackson.databind.json.JsonMapper;

import vn.edu.bku.smart_emobility_backend.common.domain.RecoveryCode;
import vn.edu.bku.smart_emobility_backend.common.domain.RecoverySession;
import vn.edu.bku.smart_emobility_backend.common.security.SessionTokenService;
import vn.edu.bku.smart_emobility_backend.common.store.InMemoryAccountDataStore;
import vn.edu.bku.smart_emobility_backend.common.store.InMemoryDataStore;
import vn.edu.bku.smart_emobility_backend.common.store.JsonStore;
import vn.edu.bku.smart_emobility_backend.common.store.StateCoordinator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

@SpringBootTest(properties = {
        "app.data-file=./target/test-data/recovery-context-${random.uuid}.json"
})
class RecoveryPersistenceTest {

    @Autowired
    private JsonMapper mapper;

    @TempDir
    Path directory;

    private Path file;
    private InMemoryDataStore data;
    private InMemoryAccountDataStore accounts;
    private JsonStore jsonStore;
    private StateCoordinator coordinator;

    private RecoveryCode code;
    private RecoverySession grant;

    @BeforeEach
    void setUp() {
        file = directory.resolve("data.json");
        data = new InMemoryDataStore();
        accounts = new InMemoryAccountDataStore(data);
        jsonStore = spy(new JsonStore(mapper, file.toString()));
        coordinator = new StateCoordinator(data, jsonStore);

        Instant now = Instant.parse("2026-11-25T00:00:00Z");
        String codeHash = new BCryptPasswordEncoder(4).encode("123456");
        String tokenHash = new SessionTokenService()
                .hashToken("A".repeat(43));

        code = new RecoveryCode(
                "recovery-1", "account-1", codeHash,
                now.plusSeconds(600), false, 0);

        grant = new RecoverySession(
                tokenHash, "account-1", code.id(),
                now, now.plusSeconds(300));
    }

    @Test
    void legacySnapshotDefaultsRecoveryListsToEmpty() throws Exception {
        Files.writeString(file, """
                {
                  "version": 7,
                  "accounts": [],
                  "sessions": []
                }
                """);

        var loaded = jsonStore.load().orElseThrow();

        assertEquals(7, loaded.version());
        assertTrue(loaded.recoveryCodes().isEmpty());
        assertTrue(loaded.recoverySessions().isEmpty());
    }

    @Test
    void recoveryDataSurvivesSaveLoadAndRestore() throws Exception {
        saveInitialRecovery();

        var snapshot = jsonStore.load().orElseThrow();
        var restored = new InMemoryDataStore();
        restored.restore(snapshot);
        var restoredAccounts = new InMemoryAccountDataStore(restored);

        assertEquals(code,
                restoredAccounts.findRecoveryCode("account-1").orElseThrow());
        assertEquals(grant,
                restoredAccounts.findRecoverySession(grant.id()).orElseThrow());

        var json = mapper.readTree(Files.readString(file));
        assertEquals(code.id(),
                json.at("/recoveryCodes/0/tokenId").asString());
        assertEquals(code.codeHash(),
                json.at("/recoveryCodes/0/hashCode").asString());
        assertFalse(json.at("/recoveryCodes/0").has("code"));
        assertEquals(grant.id(),
                json.at("/recoverySessions/0/tokenHash").asString());
        assertFalse(Files.readString(file).contains("A".repeat(43)));
    }

    @Test
    void failedSaveRestoresRecoveryCodeAndGrant() throws Exception {
        saveInitialRecovery();
        var before = data.snapshot();
        String previousFile = Files.readString(file);

        doThrow(new IllegalStateException("Simulated recovery save failure"))
                .when(jsonStore).save(any());

        assertThrows(IllegalStateException.class, () ->
                coordinator.commit(coordinator.currentVersion(), store -> {
                    accounts.deleteRecoveryCode(code.id());
                    accounts.deleteRecoverySession(grant.id());
                }));

        assertEquals(before, data.snapshot());
        assertEquals(previousFile, Files.readString(file));
        assertEquals(code,
                accounts.findRecoveryCode("account-1").orElseThrow());
        assertEquals(grant,
                accounts.findRecoverySession(grant.id()).orElseThrow());
    }

    @Test
    void consumedCodeAndRevokedGrantArePersisted() {
        saveInitialRecovery();

        var consumed = new RecoveryCode(
                code.id(), code.accountId(), code.codeHash(),
                code.expiresAt(), true, code.failedAttempts());

        assertTrue(coordinator.commit(coordinator.currentVersion(), store -> {
            accounts.saveRecoveryCode(consumed);
            accounts.deleteRecoverySession(grant.id());
        }));

        var snapshot = jsonStore.load().orElseThrow();
        assertTrue(snapshot.recoveryCodes().getFirst().used());
        assertTrue(snapshot.recoverySessions().isEmpty());
    }

    private void saveInitialRecovery() {
        assertTrue(coordinator.commit(coordinator.currentVersion(), store -> {
            accounts.saveRecoveryCode(code);
            accounts.saveRecoverySession(grant);
        }));
    }
}