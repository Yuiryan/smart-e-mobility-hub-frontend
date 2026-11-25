package vn.edu.bku.smart_emobility_backend.common.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import vn.edu.bku.smart_emobility_backend.common.exception.SessionException;
import vn.edu.bku.smart_emobility_backend.dto.ApiErrorResponse;

public final class ApiErrors {

    private ApiErrors() {
    }

    public static ResponseEntity<ApiErrorResponse> response(
            HttpStatusCode status, String code, String message,
            List<ApiErrorResponse.FieldDetail> details) {
        var body = new ApiErrorResponse.ErrorBody(
                code, message, details, UUID.randomUUID().toString(), Instant.now());
        return ResponseEntity.status(status).body(new ApiErrorResponse(body));
    }

    public static ResponseEntity<ApiErrorResponse> sessionFailure(SessionException exception) {
        return switch (exception.reason()) {
            case INVALID_SESSION -> response(HttpStatus.UNAUTHORIZED,
                    "AUTH_INVALID_TOKEN", exception.getMessage(), List.of());
            case SESSION_EXPIRED -> response(HttpStatus.UNAUTHORIZED,
                    "AUTH_TOKEN_EXPIRED", exception.getMessage(), List.of());
            case ACCOUNT_INACTIVE -> response(HttpStatus.FORBIDDEN,
                    "ACCESS_DENIED", exception.getMessage(), List.of());
            case STATE_CONFLICT -> response(HttpStatus.CONFLICT,
                    "OPTIMISTIC_LOCK_CONFLICT", exception.getMessage(), List.of());
        };
    }
}
