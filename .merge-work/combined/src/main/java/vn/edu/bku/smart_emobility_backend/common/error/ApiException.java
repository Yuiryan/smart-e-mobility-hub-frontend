package vn.edu.bku.smart_emobility_backend.common.error;

import java.util.List;
import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final List<FieldErrorDetail> details;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, List.of());
    }

    public ApiException(HttpStatus status, String code, String message, List<FieldErrorDetail> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public List<FieldErrorDetail> details() {
        return details;
    }

    public record FieldErrorDetail(String field, String reason) {
    }
}
