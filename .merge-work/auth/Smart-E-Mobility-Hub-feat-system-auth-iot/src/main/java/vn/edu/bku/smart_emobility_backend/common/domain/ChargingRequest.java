package vn.edu.bku.smart_emobility_backend.common.domain;

import java.time.Instant;

public record ChargingRequest(String id, String accountId, String vehicleId, String hubId,
        int targetBatteryPct, Instant requestedAt, ChargingRequestStatus status,
        Instant startAt, Instant endAt, Instant nextUsageAt) implements IdentifiedEntity {
    public ChargingRequest(String id, String accountId, String vehicleId, String hubId,
            int targetBatteryPct, Instant requestedAt, ChargingRequestStatus status) {
        this(id, accountId, vehicleId, hubId, targetBatteryPct, requestedAt, status, null, null, null);
    }
}
