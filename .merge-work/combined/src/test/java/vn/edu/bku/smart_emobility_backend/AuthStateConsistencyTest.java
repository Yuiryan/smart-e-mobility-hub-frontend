package vn.edu.bku.smart_emobility_backend;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import tools.jackson.databind.json.JsonMapper;

import vn.edu.bku.smart_emobility_backend.common.domain.Account;
import vn.edu.bku.smart_emobility_backend.common.domain.AccountRole;
import vn.edu.bku.smart_emobility_backend.common.domain.Session;
import vn.edu.bku.smart_emobility_backend.common.exception.LoginException;
import vn.edu.bku.smart_emobility_backend.common.exception.SessionException;
import vn.edu.bku.smart_emobility_backend.common.mail.RecoveryMailSender;
import vn.edu.bku.smart_emobility_backend.common.security.SessionTokenService;
import vn.edu.bku.smart_emobility_backend.common.store.InMemoryAccountDataStore;
import vn.edu.bku.smart_emobility_backend.common.store.InMemoryDataStore;
import vn.edu.bku.smart_emobility_backend.common.store.JsonStore;
import vn.edu.bku.smart_emobility_backend.common.store.StateCoordinator;
import vn.edu.bku.smart_emobility_backend.common.store.StoreSnapshot;
import vn.edu.bku.smart_emobility_backend.dto.LoginRequestDTO;
import vn.edu.bku.smart_emobility_backend.dto.LoginResponseDTO;
import vn.edu.bku.smart_emobility_backend.dto.RegisterStudentRequestDTO;
import vn.edu.bku.smart_emobility_backend.service.AuthService;
import vn.edu.bku.smart_emobility_backend.service.SessionService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest(properties = {
        "app.data-file=./target/test-data/consistency-context-${random.uuid}.json"
})
class AuthStateConsistencyTest {

    @Autowired
    private JsonMapper mapper;

    @TempDir
    Path directory;

    private Path file;
    private InMemoryDataStore data;
    private InMemoryAccountDataStore accounts;
    private JsonStore jsonStore;
    private StateCoordinator coordinator;
    private BCryptPasswordEncoder passwords;
    private SessionTokenService tokens;
    private AuthService auth;
    private SessionService sessions;

    @BeforeEach
    void setUp() {
        file = directory.resolve("data.json");
        data = new InMemoryDataStore();
        accounts = new InMemoryAccountDataStore(data);
        jsonStore = spy(new JsonStore(mapper, file.toString()));
        coordinator = new StateCoordinator(data, jsonStore);
        // Low BCrypt cost keeps concurrent tests fast. Production keeps its own
        // encoder.
        passwords = spy(new BCryptPasswordEncoder(4));
        tokens = new SessionTokenService();
        auth = new AuthService(
                accounts, coordinator, passwords, tokens,
                Duration.ofMinutes(30), Clock.systemUTC(),
                mock(RecoveryMailSender.class), Duration.ofMinutes(10));
        sessions = new SessionService(
                accounts, coordinator, tokens, Duration.ofMinutes(30), Clock.systemUTC());
    }

    @Test
    void secondLoginReplacesOldSessionAndPersistsOnlyTokenHash() throws Exception {
        Account account = register();
        var first = auth.login(loginRequest());
        var second = auth.login(loginRequest());

        assertNotEquals(first.sessionToken(), second.sessionToken());
        var rejection = assertThrows(
                SessionException.class, () -> sessions.authenticate(first.sessionToken()));
        assertEquals(SessionException.Reason.INVALID_SESSION, rejection.reason());
        assertEquals(account, sessions.authenticate(second.sessionToken()));
        assertEquals(1, accounts.findSessionsByAccountId(account.id()).size());

        var persisted = jsonStore.load().orElseThrow();
        assertEquals(tokens.hashToken(second.sessionToken()), persisted.sessions().getFirst().id());
        String json = Files.readString(file);
        assertFalse(json.contains(first.sessionToken()));
        assertFalse(json.contains(second.sessionToken()));
    }

    @Test
    void failedLoginSaveRestoresOldSessionVersionAndSnapshot() throws Exception {
        register();
        var first = auth.login(loginRequest());
        StoreSnapshot before = data.snapshot();
        String previousFile = Files.readString(file);
        var failure = new IllegalStateException("Simulated storage failure");
        doThrow(failure).when(jsonStore).save(any());

        var thrown = assertThrows(IllegalStateException.class, () -> auth.login(loginRequest()));

        assertSame(failure, thrown);
        assertEquals(before, data.snapshot());
        assertEquals(previousFile, Files.readString(file));
        assertTrue(accounts.findSession(tokens.hashToken(first.sessionToken())).isPresent());

        doCallRealMethod().when(jsonStore).save(any());
        assertEquals(first.user().accountId(), sessions.authenticate(first.sessionToken()).id());
    }

    @Test
    void failedRenewalRestoresExpiryVersionAndSnapshot() throws Exception {
        register();
        var login = auth.login(loginRequest());
        StoreSnapshot before = data.snapshot();
        String previousFile = Files.readString(file);
        doThrow(new IllegalStateException("Simulated storage failure"))
                .when(jsonStore).save(any());

        assertThrows(IllegalStateException.class,
                () -> sessions.authenticate(login.sessionToken()));

        assertEquals(before, data.snapshot());
        assertEquals(previousFile, Files.readString(file));
    }

    @Test
    void concurrentLoginsWithSameVersionLeaveOnlyWinningSession() throws Exception {
        Account account = register();
        CyclicBarrier checkedPasswords = new CyclicBarrier(2);

        doAnswer(invocation -> {
            boolean matches = (boolean) invocation.callRealMethod();
            checkedPasswords.await(10, TimeUnit.SECONDS);
            return matches;
        }).when(passwords).matches(any(CharSequence.class), anyString());

        Callable<Object> attempt = () -> {
            try {
                return auth.login(loginRequest());
            } catch (LoginException exception) {
                return exception;
            }
        };

        List<Object> outcomes;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(attempt);
            var second = executor.submit(attempt);
            outcomes = List.of(
                    first.get(15, TimeUnit.SECONDS),
                    second.get(15, TimeUnit.SECONDS));
        }

        var successes = outcomes.stream()
                .filter(LoginResponseDTO.class::isInstance)
                .map(LoginResponseDTO.class::cast).toList();
        var conflicts = outcomes.stream()
                .filter(LoginException.class::isInstance)
                .map(LoginException.class::cast).toList();

        assertEquals(1, successes.size());
        assertEquals(1, conflicts.size());
        assertEquals(LoginException.Reason.STATE_CONFLICT, conflicts.getFirst().reason());
        assertEquals(1, accounts.findSessionsByAccountId(account.id()).size());
        assertEquals(tokens.hashToken(successes.getFirst().sessionToken()),
                jsonStore.load().orElseThrow().sessions().getFirst().id());
    }

    @Test
    void concurrentCommitsWithSameVersionPersistOnlyOneMutation() throws Exception {
        long expectedVersion = coordinator.currentVersion();
        CyclicBarrier start = new CyclicBarrier(2);
        Instant now = Instant.now();

        Callable<Boolean> firstAttempt = () -> {
            start.await(10, TimeUnit.SECONDS);
            return coordinator.commit(expectedVersion, store -> store.put(
                    new Session("hash-one", "account-one", now, now.plusSeconds(60))));
        };
        Callable<Boolean> secondAttempt = () -> {
            start.await(10, TimeUnit.SECONDS);
            return coordinator.commit(expectedVersion, store -> store.put(
                    new Session("hash-two", "account-two", now, now.plusSeconds(60))));
        };

        boolean firstResult;
        boolean secondResult;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(firstAttempt);
            var second = executor.submit(secondAttempt);
            firstResult = first.get(15, TimeUnit.SECONDS);
            secondResult = second.get(15, TimeUnit.SECONDS);
        }

        assertNotEquals(firstResult, secondResult);
        assertEquals(expectedVersion + 1, coordinator.currentVersion());
        assertEquals(1, data.findAll(Session.class).size());
        assertEquals(data.snapshot(), jsonStore.load().orElseThrow());
        verify(jsonStore, times(1)).save(any());
    }

    private Account register() {
        return auth.reg(new RegisterStudentRequestDTO(
                "Consistency Student", "2210009",
                "consistency@example.com", "example-password"));
    }

    private LoginRequestDTO loginRequest() {
        return new LoginRequestDTO(
                "consistency@example.com", "example-password", AccountRole.STUDENT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PT0S", "-PT1S"})
    void recoveryCodeTimeoutMustBePositive(String configuredTimeout) {
        assertThrows(IllegalStateException.class, () -> new AuthService(
                accounts, coordinator, passwords, tokens,
                Duration.ofMinutes(30), Clock.systemUTC(),
                mock(RecoveryMailSender.class), Duration.parse(configuredTimeout)));
    }
}
