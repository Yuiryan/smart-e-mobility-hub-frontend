package vn.edu.bku.smart_emobility_backend.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import vn.edu.bku.smart_emobility_backend.common.domain.Account;
import vn.edu.bku.smart_emobility_backend.common.domain.Session;
import vn.edu.bku.smart_emobility_backend.common.exception.AuthorizationException;
import vn.edu.bku.smart_emobility_backend.common.exception.SessionException;
import vn.edu.bku.smart_emobility_backend.common.security.SessionTokenService;
import vn.edu.bku.smart_emobility_backend.common.store.AccountDataStore;
import vn.edu.bku.smart_emobility_backend.common.store.StateCoordinator;

@Service
public class SessionService {

        private final AccountDataStore accountDataStore;
        private final StateCoordinator stateCoordinator;
        private final SessionTokenService sessionTokenService;
        private final Duration sessionTimeout;
        private final Clock clock;

        public SessionService(
                        AccountDataStore accountDataStore,
                        StateCoordinator stateCoordinator,
                        SessionTokenService sessionTokenService,
                        @Value("${app.auth.session-timeout:PT30M}") Duration sessionTimeout,
                        Clock clock) {

                if (sessionTimeout.isZero() || sessionTimeout.isNegative()) {
                        throw new IllegalStateException("Session timeout must be positive");
                }

                this.accountDataStore = accountDataStore;
                this.stateCoordinator = stateCoordinator;
                this.sessionTokenService = sessionTokenService;
                this.sessionTimeout = sessionTimeout;
                this.clock = clock;
        }

        public Account authenticate(String token) {
                if (token == null
                                || !token.matches("[A-Za-z0-9_-]+")) {

                        throw new SessionException(
                                        SessionException.Reason.INVALID_SESSION,
                                        "Invalid session token");
                }

                // New UC-00 sessions store a SHA-256 token hash. Older M1/M2 snapshots
                // stored the opaque token directly, so retain read compatibility while
                // those snapshots are migrated by a subsequent login.
                boolean currentToken = token.matches("[A-Za-z0-9_-]{43}");
                String sessionId = currentToken ? sessionTokenService.hashToken(token) : token;

                // Legacy M1/M2 fixtures use short, directly stored tokens. They did not
                // implement sliding expiry, so authenticating them must remain read-only;
                // otherwise every GET unexpectedly advances the shared state version.
                if (!currentToken) {
                        Instant now = Instant.now(clock);
                        Session session = accountDataStore.findSession(sessionId)
                                        .orElseThrow(() -> new SessionException(
                                                        SessionException.Reason.INVALID_SESSION,
                                                        "Session is missing or expired"));
                        if (!session.expiresAt().isAfter(now)) {
                                throw new SessionException(
                                                SessionException.Reason.SESSION_EXPIRED,
                                                "Session has expired");
                        }
                        Account account = accountDataStore.findById(session.accountId())
                                        .orElseThrow(() -> new SessionException(
                                                        SessionException.Reason.INVALID_SESSION,
                                                        "Session is missing or expired"));
                        if (!account.isActive()) {
                                throw new SessionException(
                                                SessionException.Reason.ACCOUNT_INACTIVE,
                                                "Account is not active");
                        }
                        return account;
                }

                AtomicReference<Account> authenticatedAccount = new AtomicReference<>();

                long expectedVersion = stateCoordinator.currentVersion();

                boolean committed = stateCoordinator.commit(
                                expectedVersion,
                                store -> {
                                        Instant now = Instant.now(clock);

                                        Session session = accountDataStore
                                                        .findSession(sessionId)
                                                        .orElseThrow(() -> new SessionException(
                                                                        SessionException.Reason.INVALID_SESSION,
                                                                        "Session is missing or expired"));

                                        if (!session.expiresAt().isAfter(now)) {
                                                throw new SessionException(
                                                                SessionException.Reason.SESSION_EXPIRED,
                                                                "Session has expired");
                                        }

                                        Account account = accountDataStore
                                                        .findById(session.accountId())
                                                        .orElseThrow(() -> new SessionException(
                                                                        SessionException.Reason.INVALID_SESSION,
                                                                        "Session is missing or expired"));

                                        if (!account.isActive()) {
                                                throw new SessionException(
                                                                SessionException.Reason.ACCOUNT_INACTIVE,
                                                                "Account is not active");
                                        }

                                        Session renewedSession = new Session(
                                                        session.id(),
                                                        session.accountId(),
                                                        session.createdAt(),
                                                        now.plus(sessionTimeout));

                                        accountDataStore.saveSession(renewedSession);
                                        authenticatedAccount.set(account);
                                });

                if (!committed) {
                        throw new SessionException(
                                        SessionException.Reason.STATE_CONFLICT,
                                        "State changed during authentication; please retry");
                }

                return authenticatedAccount.get();
        }

    public void logout(String accountId, String sessionId) {
        long expectedVersion = stateCoordinator.currentVersion();
        boolean committed = stateCoordinator.commit(expectedVersion, store -> {
            Session session = accountDataStore.findSession(sessionId)
                    .orElseThrow(() -> new SessionException(
                            SessionException.Reason.INVALID_SESSION, "Session is missing"));
            if (!session.accountId().equals(accountId)) {
                throw new AuthorizationException("Session belongs to another account");
            }
            accountDataStore.deleteSession(sessionId);
        });

        if (!committed) {
            throw new SessionException(SessionException.Reason.STATE_CONFLICT,
                    "State changed during logout; please retry");
        }
    }
}
