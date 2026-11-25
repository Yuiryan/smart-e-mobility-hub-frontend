package vn.edu.bku.smart_emobility_backend.common.error;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.bku.smart_emobility_backend.common.error.ApiErrorResponseDTO.ErrorBody;
import vn.edu.bku.smart_emobility_backend.common.error.ApiException.FieldErrorDetail;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final ZoneOffset HCM_OFFSET = ZoneOffset.ofHours(7);
    private static final DateTimeFormatter OFFSET_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final Clock clock;

    public GlobalExceptionHandler(ObjectProvider<Clock> clocks) {
        this.clock = clocks.orderedStream().findFirst().orElseGet(Clock::systemUTC);
    }

    public static String formatTimestamp(Instant instant) {
        if (instant == null) {
            return null;
        }
        return instant.truncatedTo(ChronoUnit.SECONDS).atOffset(HCM_OFFSET).format(OFFSET_FORMATTER);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponseDTO> handleApiException(ApiException ex) {
        return buildResponse(ex.status(), ex.code(), ex.getMessage(), ex.details(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponseDTO> handleValidation(MethodArgumentNotValidException ex) {
        List<FieldErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> new FieldErrorDetail(err.getField(),
                        err.getDefaultMessage() == null ? "Giá trị không hợp lệ" : err.getDefaultMessage()))
                .toList();
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Dữ liệu không hợp lệ", details, null);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<ApiErrorResponseDTO> handleBadRequest(Exception ex) {
        String field = null;
        if (ex instanceof MethodArgumentTypeMismatchException mismatch) {
            field = mismatch.getName();
        } else if (ex instanceof MissingServletRequestParameterException missingParam) {
            field = missingParam.getParameterName();
        } else if (ex instanceof MissingRequestHeaderException missingHeader) {
            field = missingHeader.getHeaderName();
        }
        List<FieldErrorDetail> details = field != null
                ? List.of(new FieldErrorDetail(field, ex.getMessage() == null ? "Không hợp lệ" : ex.getMessage()))
                : List.of();
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                ex.getMessage() == null ? "Dữ liệu không hợp lệ" : ex.getMessage(), details, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponseDTO> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        HttpHeaders headers = new HttpHeaders();
        if (ex.getSupportedHttpMethods() != null && !ex.getSupportedHttpMethods().isEmpty()) {
            headers.setAllow(ex.getSupportedHttpMethods());
        }
        return buildResponse(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "Phương thức HTTP không được hỗ trợ", List.of(), headers);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponseDTO> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        return buildResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE",
                "Định dạng dữ liệu không được hỗ trợ", List.of(), null);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponseDTO> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String code = switch (status) {
            case BAD_REQUEST -> "VALIDATION_ERROR";
            case UNAUTHORIZED -> "UNAUTHORIZED";
            case FORBIDDEN -> "FORBIDDEN";
            case NOT_FOUND -> "NOT_FOUND";
            case CONFLICT -> "INVALID_STATE";
            case UNPROCESSABLE_ENTITY -> "BUSINESS_RULE_VIOLATION";
            case SERVICE_UNAVAILABLE -> "DEPENDENCY_UNAVAILABLE";
            default -> "INTERNAL_ERROR";
        };
        String message = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        return buildResponse(status, code, message, List.of(), null);
    }

    private ResponseEntity<ApiErrorResponseDTO> buildResponse(
            HttpStatus status,
            String code,
            String message,
            List<FieldErrorDetail> details,
            HttpHeaders extraHeaders) {
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl(CacheControl.noStore());
        if (extraHeaders != null) {
            headers.putAll(extraHeaders);
        }
        ErrorBody body = new ErrorBody(
                code,
                message,
                details,
                UUID.randomUUID().toString(),
                formatTimestamp(clock.instant()));
        return ResponseEntity.status(status).headers(headers).body(new ApiErrorResponseDTO(body));
    }
}
