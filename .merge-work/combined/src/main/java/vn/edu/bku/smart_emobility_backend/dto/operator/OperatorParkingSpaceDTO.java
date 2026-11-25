package vn.edu.bku.smart_emobility_backend.dto.operator;

import java.util.List;

public record OperatorParkingSpaceDTO(
        String spaceId,
        String hubId,
        String status,
        boolean blocked,
        SpaceReservationKind reservationKind,
        String vehicleId,
        String parkingReservationId,
        String dispatchOrderId,
        long version,
        String pendingOperationId,
        List<InterventionAction> allowedActions) {
    public OperatorParkingSpaceDTO {
        allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions);
    }
}
