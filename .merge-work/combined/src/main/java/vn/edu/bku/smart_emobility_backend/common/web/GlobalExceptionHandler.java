package vn.edu.bku.smart_emobility_backend.common.web;

import java.util.List;

import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;

import vn.edu.bku.smart_emobility_backend.common.exception.ResourceNotFoundException;
import vn.edu.bku.smart_emobility_backend.common.exception.SimulationException;
import vn.edu.bku.smart_emobility_backend.common.exception.SchedulingException;
import vn.edu.bku.smart_emobility_backend.common.exception.InvalidRecoveryException;
import vn.edu.bku.smart_emobility_backend.common.exception.AuthorizationException;
import vn.edu.bku.smart_emobility_backend.common.exception.InvalidPasswordInputException;
import vn.edu.bku.smart_emobility_backend.common.exception.InvalidRegistrationInputException;
import vn.edu.bku.smart_emobility_backend.common.exception.LoginException;
import vn.edu.bku.smart_emobility_backend.common.exception.RecoveryDeliveryException;
import vn.edu.bku.smart_emobility_backend.common.exception.RegistrationConflictException;
import vn.edu.bku.smart_emobility_backend.common.exception.SessionException;
import vn.edu.bku.smart_emobility_backend.common.error.ApiException;
import vn.edu.bku.smart_emobility_backend.dto.ApiErrorResponse;

@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RestControllerAdvice(name = "authGlobalExceptionHandler")
public class GlobalExceptionHandler {

        private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

        @ExceptionHandler(ApiException.class)
        public ResponseEntity<ApiErrorResponse> domainFailure(ApiException exception) {
                List<ApiErrorResponse.FieldDetail> details = exception.details().stream()
                                .map(detail -> new ApiErrorResponse.FieldDetail(
                                                detail.field(), detail.reason()))
                                .toList();
                return error(exception.status(), exception.code(), exception.getMessage(), details);
        }

        @ExceptionHandler(ResponseStatusException.class)
        public ResponseEntity<ApiErrorResponse> responseStatusFailure(ResponseStatusException exception) {
                HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
                String code = switch (status) {
                        case BAD_REQUEST -> "VALIDATION_ERROR";
                        case UNAUTHORIZED -> "UNAUTHORIZED";
                        case FORBIDDEN -> "FORBIDDEN";
                        case NOT_FOUND -> "RESOURCE_NOT_FOUND";
                        case CONFLICT -> "INVALID_STATE";
                        case UNPROCESSABLE_ENTITY -> "BUSINESS_RULE_VIOLATION";
                        case SERVICE_UNAVAILABLE -> "DEPENDENCY_UNAVAILABLE";
                        default -> "INTERNAL_ERROR";
                };
                String message = exception.getReason() == null
                                ? status.getReasonPhrase() : exception.getReason();
                return error(status, code, message, List.of());
        }

        @ExceptionHandler(RegistrationConflictException.class)
        public ResponseEntity<ApiErrorResponse> registrationConflict(
                        RegistrationConflictException exception) {

                String code = switch (exception.reason()) {
                        case EMAIL_ALREADY_REGISTERED -> "RESOURCE_ALREADY_EXISTS";
                        case STATE_CONFLICT -> "OPTIMISTIC_LOCK_CONFLICT";
                };

                return error(
                                HttpStatus.CONFLICT,
                                code,
                                exception.getMessage(),
                                List.of());
        }

        @ExceptionHandler(InvalidRegistrationInputException.class)
        public ResponseEntity<ApiErrorResponse> invalidRegistration(
                        InvalidRegistrationInputException exception) {

                return error(
                                HttpStatus.BAD_REQUEST,
                                "VALIDATION_ERROR",
                                exception.getMessage(),
                                List.of());
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ApiErrorResponse> invalidRequest(
                        MethodArgumentNotValidException exception) {

                List<ApiErrorResponse.FieldDetail> details = exception.getBindingResult()
                                .getFieldErrors()
                                .stream()
                                .map(fieldError -> new ApiErrorResponse.FieldDetail(
                                                fieldError.getField(),
                                                fieldError.getDefaultMessage()))
                                .toList();

                return error(
                                HttpStatus.BAD_REQUEST,
                                "VALIDATION_ERROR",
                                "Invalid request data",
                                details);
        }

        @ExceptionHandler(ConstraintViolationException.class)
        public ResponseEntity<ApiErrorResponse> invalidServiceInput(
                        ConstraintViolationException exception) {

                List<ApiErrorResponse.FieldDetail> details = exception.getConstraintViolations()
                                .stream()
                                .map(violation -> new ApiErrorResponse.FieldDetail(
                                                violation.getPropertyPath().toString(),
                                                violation.getMessage()))
                                .toList();

                return error(
                                HttpStatus.BAD_REQUEST,
                                "VALIDATION_ERROR",
                                "Invalid request data",
                                details);
        }

        @ExceptionHandler({
                        MissingRequestHeaderException.class,
                        MissingServletRequestParameterException.class,
                        MethodArgumentTypeMismatchException.class,
                        IllegalArgumentException.class
        })
        public ResponseEntity<ApiErrorResponse> invalidRequestShape(Exception exception) {
                return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                                exception.getMessage() == null ? "Invalid request data" : exception.getMessage(),
                                List.of());
        }

        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<ApiErrorResponse> malformedJson(
                        HttpMessageNotReadableException exception) {

                return error(
                                HttpStatus.BAD_REQUEST,
                                "VALIDATION_ERROR",
                                "Malformed JSON or invalid field type",
                                List.of());
        }

        @ExceptionHandler({
                        ResourceNotFoundException.class,
                        NoResourceFoundException.class,
                        NoHandlerFoundException.class
        })
        public ResponseEntity<ApiErrorResponse> resourceNotFound(Exception exception) {
                return error(
                                HttpStatus.NOT_FOUND,
                                "RESOURCE_NOT_FOUND",
                                "Requested resource was not found",
                                List.of());
        }

        @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
        public ResponseEntity<ApiErrorResponse> methodNotAllowed(
                        HttpRequestMethodNotSupportedException exception) {

                var response = error(
                                HttpStatus.METHOD_NOT_ALLOWED,
                                "METHOD_NOT_ALLOWED",
                                "HTTP method is not supported for this resource",
                                List.of());

                return ResponseEntity
                                .status(HttpStatus.METHOD_NOT_ALLOWED)
                                .headers(exception.getHeaders())
                                .body(response.getBody());
        }

        @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
        public ResponseEntity<ApiErrorResponse> unsupportedMediaType(
                        HttpMediaTypeNotSupportedException exception) {

                var response = error(
                                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                                "UNSUPPORTED_MEDIA_TYPE",
                                "Request Content-Type is not supported",
                                List.of());

                return ResponseEntity
                                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                                .headers(exception.getHeaders())
                                .body(response.getBody());
        }

        private ResponseEntity<ApiErrorResponse> error(
                        HttpStatusCode status,
                        String code,
                        String message,
                        List<ApiErrorResponse.FieldDetail> details) {

                return ApiErrors.response(status, code, message, details);
        }

        @ExceptionHandler(LoginException.class)
        public ResponseEntity<ApiErrorResponse> loginFailure(
                        LoginException exception) {

                return switch (exception.reason()) {
                        case INVALID_CREDENTIALS -> error(
                                        HttpStatus.UNAUTHORIZED,
                                        "AUTH_INVALID_CREDENTIALS",
                                        exception.getMessage(),
                                        List.of());

                        case ACCOUNT_INACTIVE -> error(
                                        HttpStatus.FORBIDDEN,
                                        "ACCESS_DENIED",
                                        exception.getMessage(),
                                        List.of());

                        case STATE_CONFLICT -> error(
                                        HttpStatus.CONFLICT,
                                        "OPTIMISTIC_LOCK_CONFLICT",
                                        exception.getMessage(),
                                        List.of());
                };
        }

        @ExceptionHandler(SessionException.class)
        public ResponseEntity<ApiErrorResponse> sessionFailure(SessionException exception) {
                return ApiErrors.sessionFailure(exception);
        }

        @ExceptionHandler(AuthorizationException.class)
        public ResponseEntity<ApiErrorResponse> authorizationFailure(AuthorizationException exception) {
                return error(HttpStatus.FORBIDDEN, "ACCESS_DENIED", exception.getMessage(), List.of());
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ApiErrorResponse> unexpectedFailure(Exception exception) {
                if (exception instanceof ErrorResponse frameworkError
                                && !frameworkError.getStatusCode().is5xxServerError()) {

                        var response = error(
                                        frameworkError.getStatusCode(),
                                        "HTTP_ERROR",
                                        "HTTP request could not be processed",
                                        List.of());

                        return ResponseEntity
                                        .status(frameworkError.getStatusCode())
                                        .headers(frameworkError.getHeaders())
                                        .body(response.getBody());
                }

                var response = error(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "INTERNAL_ERROR",
                                "An unexpected server error occurred",
                                List.of());

                log.error(
                                "Unhandled server error, requestId={}",
                                response.getBody().error().requestId(),
                                exception);

                return response;
        }

        @ExceptionHandler(InvalidPasswordInputException.class)
        public ResponseEntity<ApiErrorResponse> invalidPasswordInput(
                        InvalidPasswordInputException exception) {

                return error(
                                HttpStatus.BAD_REQUEST,
                                "VALIDATION_ERROR",
                                exception.getMessage(),
                                List.of());
        }


        @ExceptionHandler(RecoveryDeliveryException.class)
        public ResponseEntity<ApiErrorResponse> recoveryDeliveryFailure(
                        RecoveryDeliveryException exception) {
                var response = error(
                                HttpStatus.SERVICE_UNAVAILABLE,
                                "RECOVERY_DELIVERY_FAILED",
                                "Unable to send recovery email. Please retry.",
                                List.of());
                log.error(
                                "Recovery delivery failed, requestId={}, causeType={}",
                                response.getBody().error().requestId(),
                                exception.getCause().getClass().getName());
                return response;
        }

        @ExceptionHandler(InvalidRecoveryException.class)
        public ResponseEntity<ApiErrorResponse> invalidRecovery(InvalidRecoveryException exception) {
                return error(HttpStatus.BAD_REQUEST, "INVALID_RECOVERY_CREDENTIAL", exception.getMessage(), List.of());
        }
        @ExceptionHandler(SchedulingException.class)
        public ResponseEntity<ApiErrorResponse> schedulingFailure(SchedulingException exception) {
                return error(HttpStatus.valueOf(exception.status()), exception.code(),
                                String.join(", ", exception.violations()), List.of());
        }

        @ExceptionHandler(SimulationException.class)
        public ResponseEntity<ApiErrorResponse> simulationFailure(SimulationException exception) {
                return error(HttpStatus.valueOf(exception.status()), exception.code(),
                                exception.getMessage(), List.of());
        }

}
