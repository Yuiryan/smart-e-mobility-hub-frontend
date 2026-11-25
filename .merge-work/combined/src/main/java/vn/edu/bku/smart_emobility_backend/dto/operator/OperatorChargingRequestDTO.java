package vn.edu.bku.smart_emobility_backend.dto.operator;

public record OperatorChargingRequestDTO(
        String chargingRequestId,
        String vehicleId,
        String hubId,
        String portId,
        String allocationId,
        OperatorChargingRequestStatus status,
        double targetBatteryPct,
        String createdAt,
        long version) {
}
