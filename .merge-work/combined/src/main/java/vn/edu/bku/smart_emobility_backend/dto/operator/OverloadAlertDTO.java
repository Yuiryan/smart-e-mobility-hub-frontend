package vn.edu.bku.smart_emobility_backend.dto.operator;

import java.util.List;
import vn.edu.bku.smart_emobility_backend.common.domain.DataQuality;

public record OverloadAlertDTO(
        String alertId,
        String hubId,
        AlertDimension dimension,
        AlertSeverity severity,
        Double value,
        Double threshold,
        String unit,
        AlertReasonCode reasonCode,
        String message,
        String observedAt,
        DataQuality dataQuality) {

    public record AlertListResponseDTO(
            String generatedAt,
            long stateVersion,
            List<OverloadAlertDTO> items) {
        public AlertListResponseDTO {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }
}
