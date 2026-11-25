package vn.edu.bku.smart_emobility_backend.common.domain;

import java.time.Instant;
public record ChargingRequest(String id, String accountId, String vehicleId, String hubId,
        double targetBatteryPct, Instant requestedAt, ChargingRequestStatus status, String portId,
        Instant startAt, Instant endAt, Integer estimatedMinutes, Instant nextUsageAt) implements IdentifiedEntity {
    public ChargingRequest(String id, String accountId, String vehicleId, String hubId,
            double targetBatteryPct, Instant requestedAt, ChargingRequestStatus status) {
        this(id, accountId, vehicleId, hubId, targetBatteryPct, requestedAt, status,
                null, null, null, null, null);
    }

    public ChargingRequest(String id, String accountId, String vehicleId, String hubId,
            double targetBatteryPct, Instant requestedAt, ChargingRequestStatus status,
            String portId, Instant startAt, Instant endAt, Integer estimatedMinutes) {
        this(id, accountId, vehicleId, hubId, targetBatteryPct, requestedAt, status,
                portId, startAt, endAt, estimatedMinutes, null);
    }

    public ChargingRequest(String id, String accountId, String vehicleId, String hubId,
            double targetBatteryPct, Instant requestedAt, ChargingRequestStatus status,
            Instant startAt, Instant endAt, Instant nextUsageAt) {
        this(id, accountId, vehicleId, hubId, targetBatteryPct, requestedAt, status,
                null, startAt, endAt, null, nextUsageAt);
    }

    public ChargingRequest withStatus(ChargingRequestStatus next) {
        return new ChargingRequest(id, accountId, vehicleId, hubId, targetBatteryPct, requestedAt,
                next, portId, startAt, endAt, estimatedMinutes, nextUsageAt);
    }
}
