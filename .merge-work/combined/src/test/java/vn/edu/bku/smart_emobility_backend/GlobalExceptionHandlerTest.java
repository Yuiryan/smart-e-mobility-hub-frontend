package vn.edu.bku.smart_emobility_backend;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;

import vn.edu.bku.smart_emobility_backend.common.exception.LoginException;
import vn.edu.bku.smart_emobility_backend.common.exception.SessionException;
import vn.edu.bku.smart_emobility_backend.common.web.GlobalExceptionHandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.springframework.web.server.ResponseStatusException;

class GlobalExceptionHandlerTest {

        private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

        @Test
        void inactiveAccountAtLoginReturnsAccessDenied() {
                var exception = new LoginException(
                                LoginException.Reason.ACCOUNT_INACTIVE, "Account is inactive");

                var response = handler.loginFailure(exception);

                assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
                var body = response.getBody();
                assertNotNull(body);
                assertEquals("ACCESS_DENIED", body.error().code());
        }

        @Test
        void inactiveAccountDuringSessionReturnsAccessDenied() {
                var exception = new SessionException(
                                SessionException.Reason.ACCOUNT_INACTIVE, "Account is inactive");

                var response = handler.sessionFailure(exception);

                assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
                var body = response.getBody();
                assertNotNull(body);
                assertEquals("ACCESS_DENIED", body.error().code());
        }

        @Test
        void sessionConflictReturnsOptimisticLockConflict() {
                var exception = new SessionException(
                                SessionException.Reason.STATE_CONFLICT,
                                "State changed during authentication");

                var response = handler.sessionFailure(exception);

                assertEquals(HttpStatus.CONFLICT, response.getStatusCode());

                var body = response.getBody();
                assertNotNull(body);
                assertEquals("OPTIMISTIC_LOCK_CONFLICT", body.error().code());
        }

        @Test
        void expiredSessionReturnsAuthTokenExpired() {
                var exception = new SessionException(
                                SessionException.Reason.SESSION_EXPIRED,
                                "Session has expired");

                var response = handler.sessionFailure(exception);

                assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());

                var body = response.getBody();
                assertNotNull(body);
                assertEquals("AUTH_TOKEN_EXPIRED", body.error().code());
        }

        @Test
        void invalidSessionReturnsAuthInvalidToken() {
                var exception = new SessionException(
                                SessionException.Reason.INVALID_SESSION,
                                "Invalid session token");

                var response = handler.sessionFailure(exception);

                assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());

                var body = response.getBody();
                assertNotNull(body);
                assertEquals("AUTH_INVALID_TOKEN", body.error().code());
        }

        @Test
        void fallbackPreservesFrameworkStatusAndHeaders() {
                var headers = new HttpHeaders();
                headers.set("Retry-After", "60");

                var exception = new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS) {
                        @Override
                        public HttpHeaders getHeaders() {
                                return headers;
                        }
                };

                var response = handler.unexpectedFailure(exception);

                assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
                assertEquals("60", response.getHeaders().getFirst("Retry-After"));

                var body = response.getBody();
                assertNotNull(body);
                assertEquals("HTTP_ERROR", body.error().code());
        }
}