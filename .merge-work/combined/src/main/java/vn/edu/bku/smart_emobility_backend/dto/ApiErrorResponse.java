package vn.edu.bku.smart_emobility_backend.dto;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(ErrorBody error) {

    public record ErrorBody(
            String code,
            String message,
            List<FieldDetail> details,
            String requestId,
            Instant timestamp
    ) {
    }

    public record FieldDetail(
            String field,
            String issue
    ) {
    }
}
