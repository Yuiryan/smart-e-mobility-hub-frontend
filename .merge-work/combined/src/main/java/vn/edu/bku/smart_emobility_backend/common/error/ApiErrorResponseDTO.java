package vn.edu.bku.smart_emobility_backend.common.error;

import java.util.List;
import vn.edu.bku.smart_emobility_backend.common.error.ApiException.FieldErrorDetail;

public record ApiErrorResponseDTO(ErrorBody error) {
    public record ErrorBody(
            String code,
            String message,
            List<FieldErrorDetail> details,
            String requestId,
            String timestamp) {
        public ErrorBody {
            details = details == null ? List.of() : List.copyOf(details);
        }
    }
}
