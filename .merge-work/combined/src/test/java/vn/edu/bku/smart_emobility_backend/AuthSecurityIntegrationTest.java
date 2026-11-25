package vn.edu.bku.smart_emobility_backend;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.JsonNode;

import vn.edu.bku.smart_emobility_backend.common.domain.Account;
import vn.edu.bku.smart_emobility_backend.common.domain.AccountRole;
import vn.edu.bku.smart_emobility_backend.common.domain.AccountStatus;
import vn.edu.bku.smart_emobility_backend.common.domain.RecoveryCode;
import vn.edu.bku.smart_emobility_backend.common.domain.RecoverySession;
import vn.edu.bku.smart_emobility_backend.common.domain.Session;
import vn.edu.bku.smart_emobility_backend.common.exception.AuthorizationException;
import vn.edu.bku.smart_emobility_backend.common.exception.RecoveryDeliveryException;
import vn.edu.bku.smart_emobility_backend.common.mail.RecoveryMailSender;
import vn.edu.bku.smart_emobility_backend.common.security.SessionTokenService;
import vn.edu.bku.smart_emobility_backend.common.store.InMemoryDataStore;
import vn.edu.bku.smart_emobility_backend.common.store.JsonStore;
import vn.edu.bku.smart_emobility_backend.common.store.StateCoordinator;
import vn.edu.bku.smart_emobility_backend.common.store.StoreSnapshot;
import vn.edu.bku.smart_emobility_backend.service.SessionService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
                "app.data-file=./target/test-data/http-security-${random.uuid}.json",
                "app.cors.allowed-origins=http://localhost:5173",
                "app.auth.recovery.code-timeout=PT10M"
})
@Import(AuthSecurityIntegrationTest.ProbeController.class)
class AuthSecurityIntegrationTest {

        @Value("${local.server.port}")
        private int port;

        @Autowired
        private JsonMapper mapper;
        @MockitoSpyBean
        private StateCoordinator coordinator;
        @Autowired
        private InMemoryDataStore data;
        @Autowired
        private SessionTokenService tokens;
        @Autowired
        private PasswordEncoder passwords;
        @Autowired
        private SessionService sessions;
        @MockitoSpyBean
        private JsonStore jsonStore;
        @MockitoBean
        private RecoveryMailSender recoveryMailSender;

        private HttpResponse<String> changePassword(
                        Identity identity, String current, String next, String confirmation)
                        throws Exception {

                String json = mapper.writeValueAsString(Map.of(
                                "currentPassword", current,
                                "newPassword", next,
                                "confirmPassword", confirmation));

                return send(authorized("/api/auth/password/change", identity)
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(json))
                                .build());
        }

        private HttpResponse<String> loginWithPassword(
                        Identity identity, String password) throws Exception {

                String json = mapper.writeValueAsString(Map.of(
                                "email", identity.account().email(),
                                "password", password,
                                "role", identity.account().role().name()));

                return send(request("/api/auth/login")
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(json))
                                .build());
        }

        @Test
        void anonymousIdentityRequiresBearerToken() throws Exception {
                var response = send(request("/api/auth/me").GET().build());
                assertError(response, 401, "AUTH_INVALID_TOKEN");
                assertEquals("Bearer", response.headers().firstValue("WWW-Authenticate").orElse(""));
        }

        @ParameterizedTest
        @ValueSource(strings = {
                        "Basic dXNlcjpwYXNz", "Bearer bad", "Bearer null",
                        "Bearer AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                        "Bearer AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA extra"
        })
        void malformedOrUnknownTokenIsRejected(String header) throws Exception {
                var response = send(request("/api/auth/me").header("Authorization", header).GET().build());
                assertError(response, 401, "AUTH_INVALID_TOKEN");
        }

        @Test
        void duplicateAuthorizationHeadersAreRejected() throws Exception {
                var identity = seed(AccountRole.STUDENT);
                var response = send(request("/api/auth/me")
                                .header("Authorization", "Bearer " + identity.token())
                                .header("Authorization", "Bearer " + identity.token()).GET().build());
                assertError(response, 401, "AUTH_INVALID_TOKEN");
        }

        @Test
        void identityUsesCurrentAccountAndRenewsExactlyOnce() throws Exception {
                var identity = seed(AccountRole.STUDENT);
                String hash = tokens.hashToken(identity.token());
                var original = data.findById(Session.class, hash).orElseThrow();
                Instant oldExpiry = original.expiresAt();
                long version = coordinator.currentVersion();

                var response = send(authorized("/api/auth/me", identity).GET().build());
                assertEquals(200, response.statusCode());
                JsonNode body = mapper.readTree(response.body());
                assertEquals(identity.account().id(), body.path("accountId").asString());
                assertEquals(identity.account().studentId(), body.path("studentId").asString());
                assertEquals(identity.account().email(), body.path("email").asString());
                assertEquals("STUDENT", body.path("role").asString());
                assertEquals(4, body.size());
                assertFalse(response.body().contains(identity.token()));
                assertFalse(response.body().contains(identity.account().passwordHash()));
                assertTrue(response.headers().allValues("Set-Cookie").isEmpty());
                assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""));

                var renewed = data.findById(Session.class, hash).orElseThrow();
                assertEquals(original.createdAt(), renewed.createdAt());
                assertTrue(renewed.expiresAt().isAfter(oldExpiry));
                assertEquals(version + 1, coordinator.currentVersion());
        }

        @Test
        void authenticationDoesNotLeakToNextRequestOrCookie() throws Exception {
                var identity = seed(AccountRole.STUDENT);
                try (var client = HttpClient.newHttpClient()) {
                        assertEquals(200, client.send(
                                        authorized("/api/auth/me", identity).GET().build(),
                                        HttpResponse.BodyHandlers.ofString()).statusCode());
                        var anonymous = client.send(request("/api/auth/me")
                                        .header("Cookie", "JSESSIONID=made-up-session").GET().build(),
                                        HttpResponse.BodyHandlers.ofString());
                        assertError(anonymous, 401, "AUTH_INVALID_TOKEN");
                }
        }

        @Test
        void expiredSessionReturnsTokenExpired() throws Exception {
                var identity = seed(AccountRole.STUDENT);
                String hash = tokens.hashToken(identity.token());
                assertTrue(coordinator.commit(coordinator.currentVersion(),
                                store -> store.put(new Session(hash, identity.account().id(),
                                                Instant.EPOCH, Instant.EPOCH.plusSeconds(1)))));
                assertError(send(authorized("/api/auth/me", identity).GET().build()),
                                401, "AUTH_TOKEN_EXPIRED");
        }

        @ParameterizedTest
        @EnumSource(value = AccountStatus.class, names = { "SUSPENDED", "UNVERIFIED" })
        void currentAccountStatusIsAppliedToExistingToken(AccountStatus status) throws Exception {
                var identity = seed(AccountRole.STUDENT);
                updateAccount(identity, AccountRole.STUDENT, status);
                assertError(send(authorized("/api/auth/me", identity).GET().build()),
                                403, "ACCESS_DENIED");
        }

        @Test
        void currentAccountRoleControlsOperatorRoute() throws Exception {
                var identity = seed(AccountRole.STUDENT);
                assertError(send(authorized("/api/operator/security-probe", identity).GET().build()),
                                403, "ACCESS_DENIED");
                updateAccount(identity, AccountRole.OPERATOR, AccountStatus.ACTIVE);
                assertEquals(200, send(authorized(
                                "/api/operator/security-probe", identity).GET().build()).statusCode());
                updateAccount(identity, AccountRole.STUDENT, AccountStatus.ACTIVE);
                assertError(send(authorized("/api/operator/security-probe", identity).GET().build()),
                                403, "ACCESS_DENIED");
        }

        @Test
        void operatorCannotCallStudentMutationAndStudentCan() throws Exception {
                var operator = seed(AccountRole.OPERATOR);
                var student = seed(AccountRole.STUDENT);
                assertError(send(authorized("/api/reservations", operator)
                                .POST(HttpRequest.BodyPublishers.noBody()).build()), 403, "ACCESS_DENIED");
                assertEquals(200, send(authorized("/api/reservations", student)
                                .POST(HttpRequest.BodyPublishers.noBody()).build()).statusCode());
                assertError(send(request("/api/reservations")
                                .POST(HttpRequest.BodyPublishers.noBody()).build()), 401, "AUTH_INVALID_TOKEN");
        }

        @Test
        void unlistedRouteIsDenied() throws Exception {
                var identity = seed(AccountRole.OPERATOR);
                assertError(send(authorized("/api/unlisted-security-probe", identity).GET().build()),
                                403, "ACCESS_DENIED");
        }

        @Test
        void loginAcceptsStaleAuthorizationAndReplacesSession() throws Exception {
                var identity = seed(AccountRole.STUDENT);
                String json = mapper.writeValueAsString(
                                new vn.edu.bku.smart_emobility_backend.dto.LoginRequestDTO(
                                                identity.account().email(), "example-password", AccountRole.STUDENT));
                var response = send(request("/api/auth/login")
                                .header("Authorization", "Bearer expired")
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(json)).build());
                assertEquals(200, response.statusCode());
                assertError(send(authorized("/api/auth/me", identity).GET().build()),
                                401, "AUTH_INVALID_TOKEN");
        }

        @Test
        void logoutPersistsOnlyCallerSessionRevocation() throws Exception {
                var first = seed(AccountRole.STUDENT);
                var second = seed(AccountRole.STUDENT);
                var response = send(authorized("/api/auth/logout", first)
                                .POST(HttpRequest.BodyPublishers.noBody()).build());
                assertEquals(204, response.statusCode());
                assertTrue(response.body().isEmpty());
                assertTrue(response.headers().allValues("Set-Cookie").isEmpty());
                assertTrue(data.findById(Session.class, tokens.hashToken(first.token())).isEmpty());
                assertTrue(jsonStore.load().orElseThrow().sessions().stream()
                                .noneMatch(session -> session.id().equals(tokens.hashToken(first.token()))));
                assertEquals(200, send(authorized("/api/auth/me", second).GET().build()).statusCode());
                assertError(send(authorized("/api/auth/logout", first)
                                .POST(HttpRequest.BodyPublishers.noBody()).build()), 401, "AUTH_INVALID_TOKEN");
        }

        @Test
        void logoutServiceRejectsAnotherAccountsSession() {
                var first = seed(AccountRole.STUDENT);
                var second = seed(AccountRole.STUDENT);
                var before = data.snapshot();

                assertThrows(AuthorizationException.class,
                                () -> sessions.logout(first.account().id(), tokens.hashToken(second.token())));
                assertAuthStateEquals(before, data.snapshot());
        }

        @Test
        void filterStorageFailureReturnsCommonErrorAndRollsBack() throws Exception {
                var identity = seed(AccountRole.STUDENT);
                var before = data.snapshot();
                doThrow(new IllegalStateException("private-auth-storage"))
                                .when(jsonStore).save(any());

                var response = send(authorized("/api/auth/me", identity).GET().build());

                assertError(response, 500, "INTERNAL_ERROR");
                assertFalse(response.body().contains("private-auth-storage"));
                assertAuthStateEquals(before, data.snapshot());
        }

        @Test
        void logoutSaveFailureKeepsSessionAfterSuccessfulFilterRenewal() throws Exception {
                var identity = seed(AccountRole.STUDENT);
                long version = coordinator.currentVersion();
                var saves = new AtomicInteger();
                doAnswer(invocation -> {
                        if (saves.incrementAndGet() == 2) {
                                throw new IllegalStateException("Simulated logout persistence failure");
                        }
                        return invocation.callRealMethod();
                }).when(jsonStore).save(any());

                assertError(send(authorized("/api/auth/logout", identity)
                                .POST(HttpRequest.BodyPublishers.noBody()).build()), 500, "INTERNAL_ERROR");
                assertTrue(data.findById(Session.class, tokens.hashToken(identity.token())).isPresent());
                assertEquals(version + 1, coordinator.currentVersion());
                assertAuthStateEquals(data.snapshot(), jsonStore.load().orElseThrow());
        }

        @Test
        void allowedPreflightSucceedsWithoutAuthentication() throws Exception {
                var response = send(request("/api/auth/me")
                                .header("Origin", "http://localhost:5173")
                                .header("Access-Control-Request-Method", "GET")
                                .header("Access-Control-Request-Headers", "Authorization")
                                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build());
                assertEquals(200, response.statusCode());
                assertEquals("http://localhost:5173",
                                response.headers().firstValue("Access-Control-Allow-Origin").orElse(""));
                assertTrue(response.headers().firstValue("Access-Control-Allow-Headers")
                                .orElse("").toLowerCase().contains("authorization"));
                assertTrue(response.headers().firstValue("Access-Control-Allow-Credentials").isEmpty());
        }

        @ParameterizedTest
        @ValueSource(strings = { "https://untrusted.example", "null" })
        void disallowedOriginReturnsCommonCorsError(String origin) throws Exception {
                var response = send(request("/api/auth/me")
                                .header("Origin", origin)
                                .header("Access-Control-Request-Method", "GET")
                                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build());
                assertError(response, 403, "ACCESS_DENIED");
                assertTrue(response.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
        }

        @Test
        void unsupportedPreflightHeaderIsRejected() throws Exception {
                var response = send(request("/api/auth/me")
                                .header("Origin", "http://localhost:5173")
                                .header("Access-Control-Request-Method", "GET")
                                .header("Access-Control-Request-Headers", "X-Unapproved-Header")
                                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build());
                assertError(response, 403, "ACCESS_DENIED");
        }

        @Test
        void allowedOriginReceivesCorsHeadersOnAuthenticationFailure() throws Exception {
                var response = send(request("/api/auth/me")
                                .header("Origin", "http://localhost:5173").GET().build());
                assertError(response, 401, "AUTH_INVALID_TOKEN");
                assertEquals("http://localhost:5173",
                                response.headers().firstValue("Access-Control-Allow-Origin").orElse(""));
        }

        @Test
        void identityCannotBeChangedByClientAccountId() throws Exception {
                var first = seed(AccountRole.STUDENT);
                var second = seed(AccountRole.STUDENT);
                var response = send(authorized(
                                "/api/auth/me?accountId=" + second.account().id(), first).GET().build());
                assertEquals(200, response.statusCode());
                assertEquals(first.account().id(),
                                mapper.readTree(response.body()).path("accountId").asString());
        }

        @Test
        void missingAccountInvalidatesItsExistingToken() throws Exception {
                var identity = seed(AccountRole.STUDENT);
                assertTrue(coordinator.commit(coordinator.currentVersion(),
                                store -> store.remove(Account.class, identity.account().id())));
                assertError(send(authorized("/api/auth/me", identity).GET().build()),
                                401, "AUTH_INVALID_TOKEN");
        }

        @Test
        void filterVersionConflictReturnsCommon409() throws Exception {
                var identity = seed(AccountRole.STUDENT);
                var before = data.snapshot();
                doReturn(-1L).when(coordinator).currentVersion();
                assertError(send(authorized("/api/auth/me", identity).GET().build()),
                                409, "OPTIMISTIC_LOCK_CONFLICT");
                assertAuthStateEquals(before, data.snapshot());
        }

        @Test
        void logoutVersionConflictKeepsSession() throws Exception {
                var identity = seed(AccountRole.STUDENT);
                long version = coordinator.currentVersion();
                doReturn(version, -1L).when(coordinator).currentVersion();
                assertError(send(authorized("/api/auth/logout", identity)
                                .POST(HttpRequest.BodyPublishers.noBody()).build()),
                                409, "OPTIMISTIC_LOCK_CONFLICT");
                assertTrue(data.findById(Session.class, tokens.hashToken(identity.token())).isPresent());
                assertAuthStateEquals(data.snapshot(), jsonStore.load().orElseThrow());
        }

        @Test
        void unsupportedCorsMethodIsRejected() throws Exception {
                var response = send(request("/api/auth/me")
                                .header("Origin", "http://localhost:5173")
                                .header("Access-Control-Request-Method", "TRACE")
                                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build());
                assertError(response, 403, "ACCESS_DENIED");
        }

        @Test
        void disallowedOriginIsRejectedBeforeSessionRenewal() throws Exception {
                var identity = seed(AccountRole.STUDENT);
                var before = data.snapshot();
                var response = send(authorized("/api/auth/me", identity)
                                .header("Origin", "https://untrusted.example").GET().build());
                assertError(response, 403, "ACCESS_DENIED");
                assertAuthStateEquals(before, data.snapshot());
        }

        private void assertAuthStateEquals(StoreSnapshot expected, StoreSnapshot actual) {
                assertEquals(expected.version(), actual.version());
                assertEquals(Set.copyOf(expected.accounts()), Set.copyOf(actual.accounts()));
                assertEquals(Set.copyOf(expected.sessions()), Set.copyOf(actual.sessions()));
                assertEquals(Set.copyOf(expected.recoveryCodes()), Set.copyOf(actual.recoveryCodes()));
                assertEquals(Set.copyOf(expected.recoverySessions()), Set.copyOf(actual.recoverySessions()));
        }

        private Identity seed(AccountRole role) {
                String id = UUID.randomUUID().toString();
                Instant now = Instant.now();
                var account = new Account(id, "Security Student", "student-" + id,
                                id + "@example.com", passwords.encode("example-password"),
                                role, AccountStatus.ACTIVE, now, now);
                String token = tokens.generateToken();
                var session = new Session(tokens.hashToken(token), id,
                                now.minusSeconds(60), now.plusSeconds(120));
                assertTrue(coordinator.commit(coordinator.currentVersion(), store -> {
                        store.put(account);
                        store.put(session);
                }));
                return new Identity(account, token);
        }

        private void updateAccount(Identity identity, AccountRole role, AccountStatus status) {
                Account old = identity.account();
                assertTrue(coordinator.commit(coordinator.currentVersion(),
                                store -> store.put(new Account(old.id(), old.fullName(), old.studentId(),
                                                old.email(), old.passwordHash(), role, status,
                                                old.createdAt(), Instant.now()))));
        }

        private HttpRequest.Builder request(String path) {
                return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        }

        private HttpRequest.Builder authorized(String path, Identity identity) {
                return request(path).header("Authorization", "Bearer " + identity.token());
        }

        private HttpResponse<String> send(HttpRequest request) throws Exception {
                try (var client = HttpClient.newHttpClient()) {
                        return client.send(request, HttpResponse.BodyHandlers.ofString());
                }
        }

        private void assertError(HttpResponse<String> response, int status, String code)
                        throws Exception {
                assertEquals(status, response.statusCode(), response.body());
                var error = mapper.readTree(response.body()).path("error");
                assertEquals(code, error.path("code").asString(""));
                assertTrue(error.path("details").isArray());
                UUID.fromString(error.path("requestId").asString(""));
                Instant.parse(error.path("timestamp").asString(""));
        }

        private record Identity(Account account, String token) {
        }

        @TestConfiguration(proxyBeanMethods = false)
        @RestController
        static class ProbeController {

                @GetMapping("/api/operator/security-probe")
                public String operator() {
                        return "operator";
                }

                @PostMapping("/api/reservations")
                public String student() {
                        return "student";
                }

                @GetMapping("/api/unlisted-security-probe")
                public String unlisted() {
                        return "unlisted";
                }
        }

        @Test
        void passwordChangeReplacesHashAndRevokesSession() throws Exception {
                var identity = seed(AccountRole.STUDENT);
                String next = "new-example-password";

                var response = changePassword(
                                identity, "example-password", next, next);

                assertEquals(204, response.statusCode());
                assertTrue(response.body().isEmpty());

                Account updated = data.findById(
                                Account.class, identity.account().id()).orElseThrow();

                assertTrue(passwords.matches(next, updated.passwordHash()));
                assertEquals(identity.account().createdAt(), updated.createdAt());
                assertTrue(!updated.updatedAt().isBefore(identity.account().updatedAt()));

                assertError(send(authorized("/api/auth/me", identity).GET().build()),
                                401, "AUTH_INVALID_TOKEN");
                assertError(loginWithPassword(identity, "example-password"),
                                401, "AUTH_INVALID_CREDENTIALS");
                assertEquals(200, loginWithPassword(identity, next).statusCode());
        }

        @Test
        void mismatchedConfirmationKeepsOldPassword() throws Exception {
                var identity = seed(AccountRole.STUDENT);

                assertError(changePassword(
                                identity, "example-password",
                                "new-example-password", "different-password"),
                                400, "VALIDATION_ERROR");

                Account current = data.findById(
                                Account.class, identity.account().id()).orElseThrow();
                assertEquals(identity.account().passwordHash(), current.passwordHash());
                assertEquals(200,
                                send(authorized("/api/auth/me", identity).GET().build()).statusCode());
        }

        @Test
        void wrongCurrentPasswordKeepsOldPassword() throws Exception {
                var identity = seed(AccountRole.STUDENT);

                assertError(changePassword(
                                identity, "wrong-password",
                                "new-example-password", "new-example-password"),
                                401, "AUTH_INVALID_CREDENTIALS");

                Account current = data.findById(
                                Account.class, identity.account().id()).orElseThrow();
                assertEquals(identity.account().passwordHash(), current.passwordHash());
        }

        @ParameterizedTest
        @ValueSource(ints = { 7, 8, 9, 71, 72, 73 })
        void newPasswordLengthBoundaries(int length) throws Exception {
                var identity = seed(AccountRole.STUDENT);
                String next = "N".repeat(length);

                var response = changePassword(identity, "example-password", next, next);

                if (length < 8 || length > 72) {
                        assertError(response, 400, "VALIDATION_ERROR");
                } else {
                        assertEquals(204, response.statusCode());
                }
        }

        @ParameterizedTest
        @ValueSource(ints = { 36, 37 })
        void newPasswordUtf8ByteBoundary(int count) throws Exception {
                var identity = seed(AccountRole.STUDENT);
                String next = "á".repeat(count);

                var response = changePassword(identity, "example-password", next, next);

                if (count == 36) {
                        assertEquals(204, response.statusCode());
                } else {
                        assertError(response, 400, "VALIDATION_ERROR");
                }
        }

        @Test
        void anonymousPasswordChangeRequiresAuthentication() throws Exception {
                var response = send(request("/api/auth/password/change")
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString("""
                                                {
                                                  "currentPassword": "example-password",
                                                  "newPassword": "new-example-password",
                                                  "confirmPassword": "new-example-password"
                                                }
                                                """))
                                .build());

                assertError(response, 401, "AUTH_INVALID_TOKEN");
        }

        @Test
        void failedPasswordSaveKeepsOldHashAndSession() throws Exception {
                var identity = seed(AccountRole.STUDENT);
                var saves = new AtomicInteger();

                doAnswer(invocation -> {
                        if (saves.incrementAndGet() == 2) {
                                throw new IllegalStateException("Simulated password save failure");
                        }
                        return invocation.callRealMethod();
                }).when(jsonStore).save(any());

                assertError(changePassword(
                                identity, "example-password",
                                "new-example-password", "new-example-password"),
                                500, "INTERNAL_ERROR");

                Account current = data.findById(
                                Account.class, identity.account().id()).orElseThrow();
                assertEquals(identity.account().passwordHash(), current.passwordHash());
                assertTrue(data.findById(
                                Session.class, tokens.hashToken(identity.token())).isPresent());
                assertAuthStateEquals(data.snapshot(), jsonStore.load().orElseThrow());
        }

        @Test
        void passwordVersionConflictKeepsOldHash() throws Exception {
                var identity = seed(AccountRole.STUDENT);
                long version = coordinator.currentVersion();

                // First read is the filter renewal; the second is the password mutation.
                doReturn(version, -1L).when(coordinator).currentVersion();

                assertError(changePassword(
                                identity, "example-password",
                                "new-example-password", "new-example-password"),
                                409, "OPTIMISTIC_LOCK_CONFLICT");

                Account current = data.findById(
                                Account.class, identity.account().id()).orElseThrow();
                assertEquals(identity.account().passwordHash(), current.passwordHash());
                assertTrue(data.findById(
                                Session.class, tokens.hashToken(identity.token())).isPresent());
        }

        private HttpRequest.Builder recoveryRequest(String email) throws Exception {
                String json = mapper.writeValueAsString(Map.of("email", email));
                return request("/api/auth/password/recovery/request")
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(json));
        }

        @Test
        void recoveryRequestPersistsHashAndSendsCode() throws Exception {
                var identity = seed(AccountRole.STUDENT);
                doAnswer(invocation -> {
                        assertTrue(jsonStore.load().orElseThrow().recoveryCodes().stream()
                                        .anyMatch(code -> code.accountId().equals(identity.account().id())));
                        return null;
                }).when(recoveryMailSender)
                                .sendRecoveryCode(anyString(), anyString(), any(Instant.class));
                Instant before = Instant.now();

                var response = send(recoveryRequest(identity.account().email()).build());
                Instant after = Instant.now();

                assertEquals(202, response.statusCode());
                assertTrue(response.body().isEmpty());
                assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""));
                RecoveryCode saved = data.findAll(RecoveryCode.class).stream()
                                .filter(code -> code.accountId().equals(identity.account().id()))
                                .findFirst().orElseThrow();
                var captured = ArgumentCaptor.forClass(String.class);
                verify(recoveryMailSender).sendRecoveryCode(
                                eq(identity.account().email()), captured.capture(), eq(saved.expiresAt()));

                assertTrue(captured.getValue().matches("[0-9]{6}"));
                assertTrue(passwords.matches(captured.getValue(), saved.codeHash()));
                assertFalse(saved.used());
                assertEquals(0, saved.failedAttempts());
                assertFalse(saved.expiresAt().isBefore(before.plusSeconds(600)));
                assertFalse(saved.expiresAt().isAfter(after.plusSeconds(600)));
        }

        @Test
        void unknownEmailReturnsAcceptedWithoutMutation() throws Exception {
                var before = data.snapshot();
                var response = send(recoveryRequest(UUID.randomUUID() + "@example.com").build());

                assertEquals(202, response.statusCode());
                assertTrue(response.body().isEmpty());
                assertAuthStateEquals(before, data.snapshot());
                verifyNoInteractions(recoveryMailSender);
        }

        @Test
        void newRecoveryRequestRevokesOldCodeAndGrant() throws Exception {
                var identity = seed(AccountRole.STUDENT);
                String oldCodeId = UUID.randomUUID().toString();
                String grantHash = tokens.hashToken(tokens.generateToken());
                Instant now = Instant.now();

                assertTrue(coordinator.commit(coordinator.currentVersion(), store -> {
                        store.put(new RecoveryCode(
                                        oldCodeId, identity.account().id(),
                                        passwords.encode("123456"), now.plusSeconds(600), false, 0));
                        store.put(new RecoverySession(
                                        grantHash, identity.account().id(), oldCodeId,
                                        now, now.plusSeconds(300)));
                }));

                assertEquals(202, send(recoveryRequest(identity.account().email()).build()).statusCode());
                assertTrue(data.findById(RecoveryCode.class, oldCodeId).isEmpty());
                assertTrue(data.findById(RecoverySession.class, grantHash).isEmpty());
                assertEquals(1, data.findAll(RecoveryCode.class).stream()
                                .filter(code -> code.accountId().equals(identity.account().id())).count());
        }

        @Test
        void recoveryDeliveryFailureReturns503WithPersistedCode() throws Exception {
                var identity = seed(AccountRole.STUDENT);
                doThrow(new RecoveryDeliveryException(new IllegalStateException("SMTP unavailable")))
                                .when(recoveryMailSender)
                                .sendRecoveryCode(anyString(), anyString(), any(Instant.class));

                assertError(send(recoveryRequest(identity.account().email()).build()),
                                503, "RECOVERY_DELIVERY_FAILED");
                assertTrue(data.findAll(RecoveryCode.class).stream()
                                .anyMatch(code -> code.accountId().equals(identity.account().id())));
        }

        @Test
        void recoverySaveFailurePreventsEmailDelivery() throws Exception {
                var identity = seed(AccountRole.STUDENT);
                var before = data.snapshot();
                doThrow(new IllegalStateException("Simulated recovery persistence failure"))
                                .when(jsonStore).save(any());

                assertError(send(recoveryRequest(identity.account().email()).build()),
                                500, "INTERNAL_ERROR");
                assertAuthStateEquals(before, data.snapshot());
                verifyNoInteractions(recoveryMailSender);
        }

        @Test
        void recoveryVersionConflictPreventsEmailDelivery() throws Exception {
                var identity = seed(AccountRole.STUDENT);
                var before = data.snapshot();
                doReturn(-1L).when(coordinator).currentVersion();

                assertError(send(recoveryRequest(identity.account().email()).build()),
                                409, "OPTIMISTIC_LOCK_CONFLICT");
                assertAuthStateEquals(before, data.snapshot());
                verifyNoInteractions(recoveryMailSender);
        }

        @Test
        void invalidRecoveryEmailIsRejected() throws Exception {
                assertError(send(recoveryRequest("invalid-email").build()), 400, "VALIDATION_ERROR");
                verifyNoInteractions(recoveryMailSender);
        }

        @Test
        void staleAuthorizationDoesNotBlockRecoveryRequest() throws Exception {
                var identity = seed(AccountRole.STUDENT);
                var response = send(recoveryRequest(identity.account().email())
                                .header("Authorization", "Bearer expired")
                                .build());

                assertEquals(202, response.statusCode());
        }


        private HttpResponse<String> recoveryPost(String action, Map<String, String> body) throws Exception {
                return send(request("/api/auth/password/recovery/" + action)
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build());
        }

        private Map<String, String> requestEmailCode(Identity identity) throws Exception {
                String email = identity.account().email();
                assertEquals(202, send(recoveryRequest(email).build()).statusCode());
                var captured = ArgumentCaptor.forClass(String.class);
                verify(recoveryMailSender).sendRecoveryCode(eq(email), captured.capture(), any(Instant.class));
                return Map.of("email", email, "code", captured.getValue());
        }

        private String verifyEmailCode(Map<String, String> proof) throws Exception {
                var response = recoveryPost("verify", proof);
                assertEquals(200, response.statusCode());
                assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
                return mapper.readTree(response.body()).path("recoveryToken").asString();
        }

        private Map<String, String> resetBody(String token) {
                return Map.of("recoveryToken", token, "newPassword", "NewPass123", "confirmPassword", "NewPass123");
        }

        @Test
        void verifiedRecoveryResetsPasswordAndRevokesSessionsOnce() throws Exception {
                var identity = seed(AccountRole.STUDENT);
                var proof = requestEmailCode(identity);
                String token = verifyEmailCode(proof);
                var grant = data.findById(RecoverySession.class, tokens.hashToken(token)).orElseThrow();
                assertEquals(identity.account().id(), grant.accountId());
                assertError(recoveryPost("verify", proof), 400, "INVALID_RECOVERY_CREDENTIAL");
                assertError(recoveryPost("reset", resetBody(identity.token())), 400, "INVALID_RECOVERY_CREDENTIAL");
                assertError(recoveryPost("reset", Map.of("recoveryToken", token, "newPassword", "NewPass123",
                                "confirmPassword", "different")), 400, "VALIDATION_ERROR");
                assertEquals(204, recoveryPost("reset", resetBody(token)).statusCode());
                assertTrue(passwords.matches("NewPass123", data.findById(Account.class, grant.accountId())
                                .orElseThrow().passwordHash()));
                assertTrue(data.findById(RecoveryCode.class, grant.recoveryCodeId()).orElseThrow().used());
                assertTrue(data.findById(Session.class, tokens.hashToken(identity.token())).isEmpty());
                assertTrue(data.findById(RecoverySession.class, grant.id()).isEmpty());
                assertError(recoveryPost("reset", resetBody(token)), 400, "INVALID_RECOVERY_CREDENTIAL");
        }

        @Test
        void fifthFailedAttemptLocksTheIssuedCode() throws Exception {
                var identity = seed(AccountRole.STUDENT);
                var proof = requestEmailCode(identity);
                String wrong = proof.get("code").equals("000000") ? "000001" : "000000";
                var wrongProof = Map.of("email", proof.get("email"), "code", wrong);
                for (int attempt = 1; attempt <= 5; attempt++) {
                        assertError(recoveryPost("verify", wrongProof), 400, "INVALID_RECOVERY_CREDENTIAL");
                        var saved = jsonStore.load().orElseThrow().recoveryCodes().stream()
                                        .filter(code -> code.accountId().equals(identity.account().id())).findFirst().orElseThrow();
                        assertEquals(attempt, saved.failedAttempts());
                }
                assertError(recoveryPost("verify", proof), 400, "INVALID_RECOVERY_CREDENTIAL");
        }

        @Test
        void invalidOrExpiredCodeCannotIssueGrant() throws Exception {
                var identity = seed(AccountRole.STUDENT);
                String email = identity.account().email();
                assertError(recoveryPost("verify", Map.of("email", email, "code", "12345")), 400, "VALIDATION_ERROR");
                var expired = new RecoveryCode(UUID.randomUUID().toString(), identity.account().id(),
                                passwords.encode("123456"), Instant.now(), false, 0);
                assertTrue(coordinator.commit(coordinator.currentVersion(), store -> store.put(expired)));
                assertError(recoveryPost("verify", Map.of("email", email, "code", "123456")), 400, "INVALID_RECOVERY_CREDENTIAL");
                assertTrue(data.findAll(RecoverySession.class).stream()
                                .noneMatch(grant -> grant.accountId().equals(identity.account().id())));
        }

        @Test
        void expiredGrantCannotReplacePassword() throws Exception {
                var identity = seed(AccountRole.STUDENT);
                String token = verifyEmailCode(requestEmailCode(identity));
                var grant = data.findById(RecoverySession.class, tokens.hashToken(token)).orElseThrow();
                assertTrue(coordinator.commit(coordinator.currentVersion(), store -> store.put(new RecoverySession(
                                grant.id(), grant.accountId(), grant.recoveryCodeId(), grant.createdAt(), Instant.now()))));
                var before = data.snapshot();
                assertError(recoveryPost("reset", resetBody(token)), 400, "INVALID_RECOVERY_CREDENTIAL");
                assertAuthStateEquals(before, data.snapshot());
        }
}
