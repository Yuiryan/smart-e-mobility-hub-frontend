package vn.edu.bku.smart_emobility_backend;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import tools.jackson.databind.json.JsonMapper;

import vn.edu.bku.smart_emobility_backend.common.domain.Account;
import vn.edu.bku.smart_emobility_backend.common.security.SessionTokenService;
import vn.edu.bku.smart_emobility_backend.common.store.JsonStore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest(properties = {
        "app.data-file=./target/test-data/snapshot-context-${random.uuid}.json"
})
class AuthSnapshotCompatibilityTest {

    @Autowired
    private JsonMapper mapper;

    @TempDir
    Path directory;

    @Test
    void legacySnapshotCanBeLoadedSavedAndLoadedAgain() throws Exception {
        String rawToken = "A".repeat(43);
        String tokenHash = new SessionTokenService().hashToken(rawToken);
        Path file = directory.resolve("data.json");

        String legacyJson = """
                {
                  "version": 7,
                  "accounts": [{
                    "id": "account-legacy",
                    "fullName": "Legacy Student",
                    "studentId": "2210006",
                    "email": "legacy@example.com",
                    "passwordHash": "test-hash",
                    "role": "STUDENT",
                    "status": "ACTIVE",
                    "createdAt": "2026-11-01T00:00:00Z"
                  }],
                  "sessions": [{
                    "id": "%s",
                    "accountId": "account-legacy",
                    "createdAt": "2026-11-01T00:00:00Z",
                    "expiresAt": "2026-11-01T00:30:00Z"
                  }]
                }
                """.formatted(tokenHash);

        Files.writeString(file, legacyJson);
        JsonStore store = new JsonStore(mapper, file.toString());

        var loaded = store.load().orElseThrow();
        var account = loaded.accounts().getFirst();

        assertEquals("account-legacy", account.id());
        assertEquals(account.createdAt(), account.updatedAt());
        assertEquals(tokenHash, loaded.sessions().getFirst().id());

        store.save(loaded);

        String savedJson = Files.readString(file);
        var saved = mapper.readTree(savedJson);
        var savedAccount = saved.at("/accounts/0");
        var savedSession = saved.at("/sessions/0");

        assertEquals("account-legacy",
                savedAccount.path("accountId").asString());
        assertFalse(savedAccount.has("id"));
        assertEquals(tokenHash, savedSession.path("tokenHash").asString());
        assertFalse(savedSession.has("id"));
        assertFalse(savedJson.contains(rawToken));

        var reloaded = new JsonStore(mapper, file.toString())
                .load().orElseThrow();

        assertEquals(loaded, reloaded);
    }

    @Test
    void explicitUpdatedAtIsPreserved() throws Exception {
        String json = """
                {
                  "accountId": "account-current",
                  "fullName": "Current Student",
                  "studentId": "2210007",
                  "email": "current@example.com",
                  "passwordHash": "test-hash",
                  "role": "STUDENT",
                  "status": "ACTIVE",
                  "createdAt": "2026-11-01T00:00:00Z",
                  "updatedAt": "2026-11-02T00:00:00Z"
                }
                """;

        Account account = mapper.readValue(json, Account.class);
        Account reloaded = mapper.readValue(
                mapper.writeValueAsString(account), Account.class);

        assertEquals("account-current", account.id());
        assertEquals(
                Instant.parse("2026-11-02T00:00:00Z"),
                reloaded.updatedAt());
        assertEquals(account, reloaded);
    }
}