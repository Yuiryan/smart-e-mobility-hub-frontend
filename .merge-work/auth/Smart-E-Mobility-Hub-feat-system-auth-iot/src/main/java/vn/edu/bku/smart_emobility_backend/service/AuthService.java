package vn.edu.bku.smart_emobility_backend.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import vn.edu.bku.smart_emobility_backend.dto.RecoveryGrantDTO;
import vn.edu.bku.smart_emobility_backend.dto.RecoveryResetRequestDTO;
import vn.edu.bku.smart_emobility_backend.dto.RecoveryVerifyRequestDTO;
import vn.edu.bku.smart_emobility_backend.common.exception.InvalidRecoveryException;
import vn.edu.bku.smart_emobility_backend.common.domain.Account;
import vn.edu.bku.smart_emobility_backend.common.domain.AccountRole;
import vn.edu.bku.smart_emobility_backend.common.domain.AccountStatus;
import vn.edu.bku.smart_emobility_backend.common.domain.PasswordPolicy;
import vn.edu.bku.smart_emobility_backend.common.domain.RecoveryCode;
import vn.edu.bku.smart_emobility_backend.common.domain.RecoveryPolicy;
import vn.edu.bku.smart_emobility_backend.common.domain.RecoverySession;
import vn.edu.bku.smart_emobility_backend.common.domain.Session;
import vn.edu.bku.smart_emobility_backend.common.exception.AuthorizationException;
import vn.edu.bku.smart_emobility_backend.common.exception.InvalidPasswordInputException;
import vn.edu.bku.smart_emobility_backend.common.exception.InvalidRegistrationInputException;
import vn.edu.bku.smart_emobility_backend.common.exception.LoginException;
import vn.edu.bku.smart_emobility_backend.common.exception.RegistrationConflictException;
import vn.edu.bku.smart_emobility_backend.common.exception.SessionException;
import vn.edu.bku.smart_emobility_backend.common.mail.RecoveryMailSender;
import vn.edu.bku.smart_emobility_backend.common.security.SessionTokenService;
import vn.edu.bku.smart_emobility_backend.common.store.AccountDataStore;
import vn.edu.bku.smart_emobility_backend.common.store.StateCoordinator;
import vn.edu.bku.smart_emobility_backend.dto.ChangePasswordRequestDTO;
import vn.edu.bku.smart_emobility_backend.dto.LoginRequestDTO;
import vn.edu.bku.smart_emobility_backend.dto.LoginResponseDTO;
import vn.edu.bku.smart_emobility_backend.dto.RegisterStudentRequestDTO;

@Service
@Validated
public class AuthService {

        private final AccountDataStore accountDataStore;
        private final StateCoordinator stateCoordinator;
        private final PasswordEncoder passwordEncoder;
        private final SessionTokenService sessionTokenService;
        private final Duration sessionTimeout;
        private final String dummyPasswordHash;
        private final Clock clock;
        private final RecoveryMailSender recoveryMailSender;
        private final Duration recoveryCodeTimeout;
        private final SecureRandom recoveryRandom = new SecureRandom();

        public AuthService(
                        AccountDataStore accountDataStore,
                        StateCoordinator stateCoordinator,
                        PasswordEncoder passwordEncoder,
                        SessionTokenService sessionTokenService,
                        @Value("${app.auth.session-timeout:PT30M}") Duration sessionTimeout,
                        Clock clock,
                        RecoveryMailSender recoveryMailSender,
                        @Value("${app.auth.recovery.code-timeout:PT10M}") Duration recoveryCodeTimeout) {

                if (sessionTimeout.isZero() || sessionTimeout.isNegative()) {
                        throw new IllegalStateException(
                                        "Session timeout must be positive");
                }

                this.accountDataStore = accountDataStore;
                this.stateCoordinator = stateCoordinator;
                this.passwordEncoder = passwordEncoder;
                this.sessionTokenService = sessionTokenService;
                this.sessionTimeout = sessionTimeout;
                this.clock = clock;
                if (recoveryCodeTimeout.isZero() || recoveryCodeTimeout.isNegative()) {
                        throw new IllegalStateException("Recovery code timeout must be positive");
                }
                this.recoveryMailSender = recoveryMailSender;
                this.recoveryCodeTimeout = recoveryCodeTimeout;

                this.dummyPasswordHash = passwordEncoder.encode(
                                UUID.randomUUID().toString());
        }

        public Account reg(@NotNull @Valid RegisterStudentRequestDTO request) {
                String email = request.email().strip().toLowerCase(Locale.ROOT);

                if (!PasswordPolicy.isValidNewPassword(request.password())) {
                        throw new InvalidRegistrationInputException(
                                        "password must contain at least 8 characters and not exceed 72 UTF-8 bytes");
                }

                String passwordHash = passwordEncoder.encode(request.password());

                Instant now = clock.instant();

                Account account = new Account(
                                UUID.randomUUID().toString(),
                                request.fullName().strip(),
                                request.studentId().strip(),
                                email,
                                passwordHash,
                                AccountRole.STUDENT,
                                AccountStatus.ACTIVE,
                                now,
                                now);

                long expectedVersion = stateCoordinator.currentVersion();

                boolean committed = stateCoordinator.commit(expectedVersion, store -> {
                        if (accountDataStore.findByEmail(email).isPresent()) {
                                throw new RegistrationConflictException(
                                                RegistrationConflictException.Reason.EMAIL_ALREADY_REGISTERED,
                                                "Email is already registered");
                        }

                        accountDataStore.saveAccount(account);
                });

                if (!committed) {
                        throw new RegistrationConflictException(
                                        RegistrationConflictException.Reason.STATE_CONFLICT,
                                        "State changed during registration; please retry");
                }

                return account;
        }

        public LoginResponseDTO login(
                        @NotNull @Valid LoginRequestDTO request) {

                if (request.password()
                                .getBytes(StandardCharsets.UTF_8).length > PasswordPolicy.MAX_BCRYPT_BYTES) {

                        throw new LoginException(
                                        LoginException.Reason.INVALID_CREDENTIALS,
                                        "Invalid email, password or role");
                }

                String email = request.email()
                                .strip()
                                .toLowerCase(Locale.ROOT);

                long expectedVersion = stateCoordinator.currentVersion();

                Account account = accountDataStore.findByEmail(email)
                                .orElse(null);

                String storedHash = account == null
                                ? dummyPasswordHash
                                : account.passwordHash();

                boolean passwordMatches = passwordEncoder.matches(
                                request.password(),
                                storedHash);

                if (!passwordMatches
                                || account == null
                                || account.role() != request.role()) {

                        throw new LoginException(
                                        LoginException.Reason.INVALID_CREDENTIALS,
                                        "Invalid email, password or role");
                }

                if (!account.isActive()) {
                        throw new LoginException(
                                        LoginException.Reason.ACCOUNT_INACTIVE,
                                        "Account is not active");
                }

                String token = sessionTokenService.generateToken();
                String sessionId = sessionTokenService.hashToken(token);

                Instant now = clock.instant();

                Session session = new Session(
                                sessionId,
                                account.id(),
                                now,
                                now.plus(sessionTimeout));

                boolean committed = stateCoordinator.commit(
                                expectedVersion,
                                store -> {
                                        for (Session oldSession : accountDataStore.findSessionsByAccountId(
                                                        account.id())) {

                                                accountDataStore.deleteSession(oldSession.id());
                                        }

                                        accountDataStore.saveSession(session);
                                });

                if (!committed) {
                        throw new LoginException(
                                        LoginException.Reason.STATE_CONFLICT,
                                        "State changed during login; please retry");
                }

                return new LoginResponseDTO(
                                token,
                                "Bearer",
                                session.expiresAt(),
                                new LoginResponseDTO.UserSummary(
                                                account.id(),
                                                account.studentId(),
                                                account.email(),
                                                account.role()));
        }

        public void changePassword(
                        String accountId,
                        String sessionId,
                        @NotNull @Valid ChangePasswordRequestDTO request) {

                validateNewPassword(request.newPassword(), request.confirmPassword());

                long expectedVersion = stateCoordinator.currentVersion();

                Account account = accountDataStore.findById(accountId)
                                .orElseThrow(() -> new SessionException(
                                                SessionException.Reason.INVALID_SESSION,
                                                "Account is missing"));

                Session session = accountDataStore.findSession(sessionId)
                                .orElseThrow(() -> new SessionException(
                                                SessionException.Reason.INVALID_SESSION,
                                                "Session is missing"));

                if (!session.accountId().equals(accountId)) {
                        throw new AuthorizationException("Session belongs to another account");
                }

                if (!session.expiresAt().isAfter(clock.instant())) {
                        throw new SessionException(
                                        SessionException.Reason.SESSION_EXPIRED,
                                        "Session has expired");
                }

                if (!account.isActive()) {
                        throw new SessionException(
                                        SessionException.Reason.ACCOUNT_INACTIVE,
                                        "Account is not active");
                }

                if (request.currentPassword().getBytes(StandardCharsets.UTF_8).length > PasswordPolicy.MAX_BCRYPT_BYTES
                                || !passwordEncoder.matches(
                                                request.currentPassword(), account.passwordHash())) {

                        throw new LoginException(
                                        LoginException.Reason.INVALID_CREDENTIALS,
                                        "Current password is invalid");
                }

                String passwordHash = passwordEncoder.encode(request.newPassword());

                boolean committed = stateCoordinator.commit(expectedVersion, store -> {
                        savePasswordAndRevokeSessions(account, passwordHash);
                        accountDataStore.findRecoveryCode(accountId)
                                        .ifPresent(code -> accountDataStore.deleteRecoveryCode(code.id()));
                });

                if (!committed) {
                        throw new SessionException(
                                        SessionException.Reason.STATE_CONFLICT,
                                        "State changed during password update; please retry");
                }
        }

        public void sendRecovery(@NotBlank @Email String email) {
                String normalizedEmail = email.strip().toLowerCase(Locale.ROOT);
                long expectedVersion = stateCoordinator.currentVersion();
                Account account = accountDataStore.findByEmail(normalizedEmail).orElse(null);
                if (account == null) {
                        return;
                }

                String rawCode = String.format(
                                Locale.ROOT,
                                "%0" + RecoveryPolicy.CODE_LENGTH + "d",
                                recoveryRandom.nextInt(RecoveryPolicy.CODE_BOUND));
                String codeHash = passwordEncoder.encode(rawCode);
                RecoveryCode recovery = new RecoveryCode(
                                UUID.randomUUID().toString(),
                                account.id(),
                                codeHash,
                                clock.instant().plus(recoveryCodeTimeout),
                                false,
                                0);

                boolean committed = stateCoordinator.commit(expectedVersion, store -> {
                        accountDataStore.findRecoveryCode(account.id())
                                        .ifPresent(previous ->
                                                accountDataStore.deleteRecoveryCode(previous.id()));
                        for (RecoverySession previous :
                                        accountDataStore.findRecoverySessionsByAccountId(account.id())) {
                                accountDataStore.deleteRecoverySession(previous.id());
                        }
                        accountDataStore.saveRecoveryCode(recovery);
                });

                if (!committed) {
                        throw new SessionException(
                                        SessionException.Reason.STATE_CONFLICT,
                                        "State changed during recovery request; please retry");
                }

                recoveryMailSender.sendRecoveryCode(account.email(), rawCode, recovery.expiresAt());
        }

        private void validateNewPassword(String password, String confirmation) {
                if (!password.equals(confirmation)) {
                        throw new InvalidPasswordInputException(
                                        "New password and confirmation must match");
                }

                if (!PasswordPolicy.isValidNewPassword(password)) {
                        throw new InvalidPasswordInputException(
                                        "New password must contain at least 8 characters and not exceed 72 UTF-8 bytes");
                }
        }

        private void savePasswordAndRevokeSessions(Account account, String passwordHash) {
                accountDataStore.saveAccount(new Account(
                                account.id(), account.fullName(), account.studentId(), account.email(),
                                passwordHash, account.role(), account.status(), account.createdAt(), clock.instant()));
                for (Session session : accountDataStore.findSessionsByAccountId(account.id())) {
                        accountDataStore.deleteSession(session.id());
                }
                for (RecoverySession grant : accountDataStore.findRecoverySessionsByAccountId(account.id())) {
                        accountDataStore.deleteRecoverySession(grant.id());
                }
        }

        public RecoveryGrantDTO verifyCode(@NotNull @Valid RecoveryVerifyRequestDTO request) {
                long version = stateCoordinator.currentVersion();
                Account account = accountDataStore.findByEmail(request.email().strip().toLowerCase(Locale.ROOT))
                                .orElseThrow(InvalidRecoveryException::new);
                RecoveryCode code = accountDataStore.findRecoveryCode(account.id())
                                .orElseThrow(InvalidRecoveryException::new);
                if (code.used() || code.failedAttempts() >= RecoveryPolicy.MAX_FAILED_ATTEMPTS
                                || !code.expiresAt().isAfter(clock.instant())
                                || !accountDataStore.findRecoverySessionsByAccountId(account.id()).isEmpty()) {
                        throw new InvalidRecoveryException();
                }

                boolean matches = passwordEncoder.matches(request.code(), code.codeHash());
                String token = matches ? sessionTokenService.generateToken() : null;
                boolean committed = stateCoordinator.commit(version, store -> {
                        Instant now = clock.instant();
                        if (!code.expiresAt().isAfter(now)) {
                                throw new InvalidRecoveryException();
                        }
                        if (matches) {
                                accountDataStore.saveRecoverySession(new RecoverySession(
                                                sessionTokenService.hashToken(token), account.id(), code.id(),
                                                now, code.expiresAt()));
                        } else {
                                accountDataStore.saveRecoveryCode(new RecoveryCode(code.id(), account.id(),
                                                code.codeHash(), code.expiresAt(), false, code.failedAttempts() + 1));
                        }
                });
                requireRecoveryCommit(committed);
                // Persist the failed attempt before returning the validation error.
                if (!matches) {
                        throw new InvalidRecoveryException();
                }
                return new RecoveryGrantDTO(token, code.expiresAt());
        }

        public void updatePass(@NotNull @Valid RecoveryResetRequestDTO request) {
                validateNewPassword(request.newPassword(), request.confirmPassword());
                long version = stateCoordinator.currentVersion();
                RecoverySession grant = accountDataStore.findRecoverySession(
                                sessionTokenService.hashToken(request.recoveryToken()))
                                .orElseThrow(InvalidRecoveryException::new);
                Account account = accountDataStore.findById(grant.accountId()).orElseThrow(InvalidRecoveryException::new);
                RecoveryCode code = accountDataStore.findRecoveryCode(account.id()).orElseThrow(InvalidRecoveryException::new);
                if (code.used() || !code.id().equals(grant.recoveryCodeId())) {
                        throw new InvalidRecoveryException();
                }

                String hash = passwordEncoder.encode(request.newPassword());
                boolean committed = stateCoordinator.commit(version, store -> {
                        Instant now = clock.instant();
                        if (!grant.expiresAt().isAfter(now) || !code.expiresAt().isAfter(now)) {
                                throw new InvalidRecoveryException();
                        }
                        savePasswordAndRevokeSessions(account, hash);
                        accountDataStore.saveRecoveryCode(new RecoveryCode(code.id(), account.id(),
                                        code.codeHash(), code.expiresAt(), true, code.failedAttempts()));
                });
                requireRecoveryCommit(committed);
        }

        private void requireRecoveryCommit(boolean committed) {
                if (!committed) {
                        throw new SessionException(SessionException.Reason.STATE_CONFLICT,
                                        "State changed during recovery; please retry");
                }
        }
}
