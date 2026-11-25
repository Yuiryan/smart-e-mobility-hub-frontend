package vn.edu.bku.smart_emobility_backend.dto.operator;

import java.util.List;
import vn.edu.bku.smart_emobility_backend.common.domain.DataQuality;

public record OperatorChargingPortDTO(
        String portId,
        String hubId,
        OperatorPortStatus status,
        double ratedPowerW,
        Double measuredPowerW,
        Boolean powerEnabled,
        String activeChargingRequestId,
        String allocationId,
        String vehicleId,
        String observedAt,
        DataQuality dataQuality,
        ConnectionStatus connectionStatus,
        long version,
        String pendingOperationId,
        List<InterventionAction> allowedActions) {
    public OperatorChargingPortDTO {
        allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions);
    }
}
