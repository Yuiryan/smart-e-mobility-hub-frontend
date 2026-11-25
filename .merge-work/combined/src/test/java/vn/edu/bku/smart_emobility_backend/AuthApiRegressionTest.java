package vn.edu.bku.smart_emobility_backend;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import vn.edu.bku.smart_emobility_backend.common.store.StateCoordinator;
import static org.mockito.Mockito.doReturn;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
                "app.data-file=./target/test-data/auth-regression-${random.uuid}.json"
})
class AuthApiRegressionTest {

        @Value("${local.server.port}")
        private int port;
        @MockitoSpyBean
        private StateCoordinator stateCoordinator;

        @Test
        void rejectsNumericRoleBeforeAuthentication() throws Exception {
                String json = """
                                {
                                  "email": "unknown@example.com",
                                  "password": "example-password",
                                  "role": 0
                                }
                                """;

                HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(
                                                "http://localhost:" + port + "/api/auth/login"))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(json))
                                .build();

                try (HttpClient client = HttpClient.newHttpClient()) {
                        HttpResponse<String> response = client.send(
                                        request,
                                        HttpResponse.BodyHandlers.ofString());

                        assertEquals(
                                        400,
                                        response.statusCode(),
                                        "Numeric role must be rejected before authentication");
                }
        }

        @Test
        void unknownEmailReturnsInvalidCredentialsCode() throws Exception {
                String json = """
                                {
                                  "email": "unknown@example.com",
                                  "password": "example-password",
                                  "role": "STUDENT"
                                }
                                """;

                HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create("http://localhost:" + port + "/api/auth/login"))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(json))
                                .build();

                try (HttpClient client = HttpClient.newHttpClient()) {
                        HttpResponse<String> response = client.send(
                                        request, HttpResponse.BodyHandlers.ofString());

                        assertEquals(401, response.statusCode());

                        var body = JsonMapper.builder().build().readTree(response.body());
                        assertEquals("AUTH_INVALID_CREDENTIALS",
                                        body.at("/error/code").asString());
                }
        }

        @Test
        void invalidEmailReturnsFieldIssue() throws Exception {
                String json = """
                                {
                                  "email": "invalid-email",
                                  "password": "example-password",
                                  "role": "STUDENT"
                                }
                                """;

                HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create("http://localhost:" + port + "/api/auth/login"))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(json))
                                .build();

                try (HttpClient client = HttpClient.newHttpClient()) {
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        assertEquals(400, response.statusCode());

                        var body = JsonMapper.builder().build().readTree(response.body());
                        assertEquals("VALIDATION_ERROR", body.at("/error/code").asString());

                        var detail = body.at("/error/details/0");
                        assertEquals("email", detail.path("field").asString());
                        assertEquals("email must be valid", detail.path("issue").asString(""));
                }
        }

        @Test
        void duplicateRegistrationReturnsResourceAlreadyExists() throws Exception {
                String json = """
                                {
                                  "fullName": "Test Student",
                                  "studentId": "2210001",
                                  "email": "duplicate-registration@example.com",
                                  "password": "example-password"
                                }
                                """;

                HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create("http://localhost:" + port + "/api/auth/register"))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(json))
                                .build();

                try (HttpClient client = HttpClient.newHttpClient()) {
                        var first = client.send(request, HttpResponse.BodyHandlers.ofString());
                        assertEquals(201, first.statusCode(), "First registration must succeed");

                        var duplicate = client.send(request, HttpResponse.BodyHandlers.ofString());
                        assertEquals(409, duplicate.statusCode());

                        var body = JsonMapper.builder().build().readTree(duplicate.body());
                        assertEquals("RESOURCE_ALREADY_EXISTS", body.at("/error/code").asString());
                }
        }

        @Test
        void staleRegistrationReturnsOptimisticLockConflict() throws Exception {
                doReturn(-1L).when(stateCoordinator).currentVersion();

                String json = """
                                {
                                  "fullName": "Test Student",
                                  "studentId": "2210002",
                                  "email": "stale-registration@example.com",
                                  "password": "example-password"
                                }
                                """;

                HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create("http://localhost:" + port + "/api/auth/register"))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(json))
                                .build();

                try (HttpClient client = HttpClient.newHttpClient()) {
                        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        assertEquals(409, response.statusCode());

                        var body = JsonMapper.builder().build().readTree(response.body());
                        assertEquals("OPTIMISTIC_LOCK_CONFLICT", body.at("/error/code").asString());
                }
        }

        @Test
        void staleLoginReturnsOptimisticLockConflict() throws Exception {
                String registerJson = """
                                {
                                  "fullName": "Login Conflict Student",
                                  "studentId": "2210003",
                                  "email": "stale-login@example.com",
                                  "password": "example-password"
                                }
                                """;

                String loginJson = """
                                {
                                  "email": "stale-login@example.com",
                                  "password": "example-password",
                                  "role": "STUDENT"
                                }
                                """;

                URI authBase = URI.create("http://localhost:" + port + "/api/auth/");
                HttpRequest register = HttpRequest.newBuilder(authBase.resolve("register"))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(registerJson))
                                .build();
                HttpRequest login = HttpRequest.newBuilder(authBase.resolve("login"))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(loginJson))
                                .build();

                try (HttpClient client = HttpClient.newHttpClient()) {
                        var registered = client.send(register, HttpResponse.BodyHandlers.ofString());
                        assertEquals(201, registered.statusCode());

                        doReturn(-1L).when(stateCoordinator).currentVersion();

                        var response = client.send(login, HttpResponse.BodyHandlers.ofString());
                        assertEquals(409, response.statusCode());

                        var body = JsonMapper.builder().build().readTree(response.body());
                        assertEquals("OPTIMISTIC_LOCK_CONFLICT", body.at("/error/code").asString());
                }
        }

        @Test
        void unknownPathReturnsCommonNotFoundError() throws Exception {
                HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create("http://localhost:" + port + "/api/vehicles/unknown-path"))
                                .GET()
                                .build();

                assertHttpError(request, 404, "RESOURCE_NOT_FOUND");
        }

        @Test
        void unsupportedMethodReturnsCommonErrorAndAllowHeader() throws Exception {
                HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create("http://localhost:" + port + "/api/auth/login"))
                                .GET()
                                .build();

                var response = assertHttpError(request, 405, "METHOD_NOT_ALLOWED");
                String allow = response.headers().firstValue("Allow").orElse("");
                assertTrue(allow.contains("POST"), "Allow must include POST");
        }

        @Test
        void unsupportedContentTypeReturnsCommonError() throws Exception {
                HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create("http://localhost:" + port + "/api/auth/login"))
                                .header("Content-Type", "text/plain")
                                .POST(HttpRequest.BodyPublishers.ofString("plain text"))
                                .build();

                assertHttpError(request, 415, "UNSUPPORTED_MEDIA_TYPE");
        }

        private HttpResponse<String> assertHttpError(
                        HttpRequest request,
                        int expectedStatus,
                        String expectedCode) throws Exception {

                try (HttpClient client = HttpClient.newHttpClient()) {
                        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                        assertEquals(expectedStatus, response.statusCode());

                        var body = JsonMapper.builder().build().readTree(response.body());
                        var error = body.path("error");

                        assertEquals(expectedCode, error.path("code").asString(""));
                        assertTrue(error.path("details").isArray());
                        assertTrue(!error.path("message").asString("").isBlank());
                        assertTrue(!error.path("requestId").asString("").isBlank());
                        assertTrue(!error.path("timestamp").asString("").isBlank());

                        return response;
                }
        }

        @Test
        void serverFailureReturnsCommonErrorWithoutInternalMessage() throws Exception {
                doThrow(new IllegalStateException("private-storage-path"))
                                .when(stateCoordinator).currentVersion();

                String json = """
                                {
                                  "fullName": "Server Error Student",
                                  "studentId": "2210004",
                                  "email": "server-error@example.com",
                                  "password": "example-password"
                                }
                                """;

                HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create("http://localhost:" + port + "/api/auth/register"))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(json))
                                .build();

                var response = assertHttpError(request, 500, "INTERNAL_ERROR");
                assertTrue(!response.body().contains("private-storage-path"));
        }

        @Test
        void registrationAndLoginReturnAccountFieldsFromContract() throws Exception {
                String registerJson = """
                                {
                                  "fullName": "Response Test Student",
                                  "studentId": "2210005",
                                  "email": "response-test@example.com",
                                  "password": "example-password"
                                }
                                """;

                String loginJson = """
                                {
                                  "email": "response-test@example.com",
                                  "password": "example-password",
                                  "role": "STUDENT"
                                }
                                """;

                URI base = URI.create("http://localhost:" + port + "/api/auth/");

                HttpRequest registerRequest = HttpRequest.newBuilder(base.resolve("register"))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(registerJson))
                                .build();

                HttpRequest loginRequest = HttpRequest.newBuilder(base.resolve("login"))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(loginJson))
                                .build();

                try (HttpClient client = HttpClient.newHttpClient()) {
                        var mapper = JsonMapper.builder().build();

                        var registered = client.send(
                                        registerRequest, HttpResponse.BodyHandlers.ofString());
                        assertEquals(201, registered.statusCode());

                        var account = mapper.readTree(registered.body());
                        String accountId = account.path("accountId").asString("");

                        assertTrue(!accountId.isBlank());
                        assertEquals("2210005", account.path("studentId").asString(""));
                        assertEquals("response-test@example.com", account.path("email").asString(""));
                        assertEquals("STUDENT", account.path("role").asString(""));
                        assertEquals("ACTIVE", account.path("status").asString(""));
                        java.time.Instant.parse(account.path("createdAt").asString(""));
                        assertEquals(6, account.size());

                        var loggedIn = client.send(
                                        loginRequest, HttpResponse.BodyHandlers.ofString());
                        assertEquals(200, loggedIn.statusCode());

                        var login = mapper.readTree(loggedIn.body());
                        var user = login.path("user");

                        assertEquals(accountId, user.path("accountId").asString(""));
                        assertEquals("2210005", user.path("studentId").asString(""));
                        assertEquals("response-test@example.com", user.path("email").asString(""));
                        assertEquals("STUDENT", user.path("role").asString(""));
                        assertEquals(4, user.size());

                        assertEquals("Bearer", login.path("tokenType").asString(""));
                        assertTrue(login.path("sessionToken").asString("")
                                        .matches("[A-Za-z0-9_-]{43}"));
                        java.time.Instant.parse(login.path("expiresAt").asString(""));
                        assertEquals(4, login.size());

                        assertEquals("no-store",
                                        loggedIn.headers().firstValue("Cache-Control").orElse(""));
                }
        }
}
