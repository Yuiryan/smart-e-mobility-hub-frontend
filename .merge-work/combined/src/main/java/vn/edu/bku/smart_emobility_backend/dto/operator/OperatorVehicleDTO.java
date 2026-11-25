package vn.edu.bku.smart_emobility_backend.dto.operator;

import java.util.List;
import vn.edu.bku.smart_emobility_backend.common.domain.DataQuality;
import vn.edu.bku.smart_emobility_backend.common.domain.VehicleStatus;

public record OperatorVehicleDTO(
        String vehicleId,
        String hubId,
        String spaceId,
        String ownership,
        VehicleStatus status,
        Double batteryPct,
        BatterySource batterySource,
        String batteryUpdatedAt,
        String observedAt,
        DataQuality dataQuality,
        ConnectionStatus connectionStatus,
        GeoLocationDTO location,
        Boolean isLocked,
        long version,
        String pendingOperationId,
        List<InterventionAction> allowedActions,
        List<TransportMode> dispatchModes,
        ActiveUsageDTO activeUsage) {
    public OperatorVehicleDTO {
        allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions);
        dispatchModes = dispatchModes == null ? List.of() : List.copyOf(dispatchModes);
    }

    public record GeoLocationDTO(double latitude, double longitude) {
    }

    public record ActiveUsageDTO(
            String rideId,
            String reservationId,
            String parkingReservationId,
            String chargingRequestId,
            String allocationId) {
    }
}
