package vn.edu.bku.smart_emobility_backend;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import vn.edu.bku.smart_emobility_backend.common.domain.Session;
import vn.edu.bku.smart_emobility_backend.common.exception.SessionException;
import vn.edu.bku.smart_emobility_backend.common.security.SessionTokenService;
import vn.edu.bku.smart_emobility_backend.common.store.AccountDataStore;
import vn.edu.bku.smart_emobility_backend.common.store.InMemoryDataStore;
import vn.edu.bku.smart_emobility_backend.common.store.StateCoordinator;
import vn.edu.bku.smart_emobility_backend.service.SessionService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneOffset;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import vn.edu.bku.smart_emobility_backend.common.domain.Account;
import vn.edu.bku.smart_emobility_backend.common.domain.AccountRole;
import vn.edu.bku.smart_emobility_backend.common.domain.AccountStatus;

import static org.mockito.Mockito.doReturn;

class SessionServiceTest {

        private final AccountDataStore accounts = mock(AccountDataStore.class);
        private final StateCoordinator coordinator = mock(StateCoordinator.class);
        private final SessionTokenService tokens = new SessionTokenService();
        private static final Instant NOW = Instant.parse("2026-11-24T10:00:00Z");

        private SessionService service;

        @BeforeEach
        void setUp() {
                service = new SessionService(
                                accounts,
                                coordinator,
                                tokens,
                                Duration.ofMinutes(30),
                                Clock.fixed(NOW, ZoneOffset.UTC));

                doAnswer(invocation -> {
                        Consumer<InMemoryDataStore> change = invocation.getArgument(1);
                        change.accept(mock(InMemoryDataStore.class));
                        return true;
                }).when(coordinator).commit(anyLong(), any());
        }

        @Test
        void expiredSessionIsRejectedWithoutRenewal() {
                String token = "A".repeat(43);
                String sessionId = tokens.hashToken(token);

                Session expired = new Session(
                                sessionId,
                                "account-1",
                                Instant.EPOCH,
                                Instant.EPOCH.plusSeconds(1));

                when(accounts.findSession(sessionId))
                                .thenReturn(Optional.of(expired));

                SessionException exception = assertThrows(
                                SessionException.class,
                                () -> service.authenticate(token));

                assertEquals("SESSION_EXPIRED", exception.reason().name());
                verify(accounts, never()).saveSession(any());
        }

        @Test
        void missingSessionIsRejectedWithoutRenewal() {
                String token = "B".repeat(43);
                String sessionId = tokens.hashToken(token);

                when(accounts.findSession(sessionId))
                                .thenReturn(Optional.empty());

                SessionException exception = assertThrows(
                                SessionException.class,
                                () -> service.authenticate(token));

                assertEquals(SessionException.Reason.INVALID_SESSION, exception.reason());
                verify(accounts, never()).saveSession(any());
        }

        @Test
        void sessionExpiringExactlyNowIsRejected() {
                String token = "C".repeat(43);
                prepareSession(token, NOW);

                var exception = assertThrows(
                                SessionException.class,
                                () -> service.authenticate(token));

                assertEquals(SessionException.Reason.SESSION_EXPIRED, exception.reason());
                verify(accounts, never()).saveSession(any());
        }

        @Test
        void sessionStillValidIsRenewedFromCurrentTime() {
                String token = "D".repeat(43);
                Session original = prepareSession(token, NOW.plusNanos(1));
                Account account = createAccount(AccountStatus.ACTIVE);

                when(accounts.findById(account.id())).thenReturn(Optional.of(account));

                Account authenticated = service.authenticate(token);

                assertEquals(account, authenticated);
                verify(accounts).saveSession(new Session(
                                original.id(),
                                original.accountId(),
                                original.createdAt(),
                                NOW.plus(Duration.ofMinutes(30))));
        }

        @ParameterizedTest
        @EnumSource(value = AccountStatus.class, names = { "SUSPENDED", "UNVERIFIED" })
        void inactiveAccountCannotRenewSession(AccountStatus status) {
                String token = "E".repeat(43);
                prepareSession(token, NOW.plusSeconds(60));

                Account account = createAccount(status);
                when(accounts.findById(account.id())).thenReturn(Optional.of(account));

                var exception = assertThrows(
                                SessionException.class,
                                () -> service.authenticate(token));

                assertEquals(SessionException.Reason.ACCOUNT_INACTIVE, exception.reason());
                verify(accounts, never()).saveSession(any());
        }

        @Test
        void sessionWithMissingAccountCannotBeRenewed() {
                String token = "F".repeat(43);
                prepareSession(token, NOW.plusSeconds(60));

                when(accounts.findById("account-1")).thenReturn(Optional.empty());

                var exception = assertThrows(
                                SessionException.class,
                                () -> service.authenticate(token));

                assertEquals(SessionException.Reason.INVALID_SESSION, exception.reason());
                verify(accounts, never()).saveSession(any());
        }

        @Test
        void staleVersionRejectsAuthenticationWithoutRenewal() {
                doReturn(false).when(coordinator).commit(anyLong(), any());

                var exception = assertThrows(
                                SessionException.class,
                                () -> service.authenticate("G".repeat(43)));

                assertEquals(SessionException.Reason.STATE_CONFLICT, exception.reason());
                verify(accounts, never()).saveSession(any());
        }

        private Session prepareSession(String token, Instant expiresAt) {
                Session session = new Session(
                                tokens.hashToken(token),
                                "account-1",
                                NOW.minusSeconds(60),
                                expiresAt);

                when(accounts.findSession(session.id())).thenReturn(Optional.of(session));
                return session;
        }

        private Account createAccount(AccountStatus status) {
                Instant createdAt = NOW.minusSeconds(120);

                return new Account(
                                "account-1",
                                "Session Test Student",
                                "2210008",
                                "session-test@example.com",
                                "test-hash",
                                AccountRole.STUDENT,
                                status,
                                createdAt,
                                createdAt);
        }
}