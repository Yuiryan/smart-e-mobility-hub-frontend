package vn.edu.bku.smart_emobility_backend.common.domain;

import java.time.Instant;

public record Ride(String id, String accountId, String vehicleId, Instant startedAt,
        Instant endedAt, int startBatteryPct, Integer endBatteryPct, RideStatus status,
        String originHubId, String destinationHubId, Double distanceKm) implements IdentifiedEntity {
    public Ride(String id, String accountId, String vehicleId, Instant startedAt, Instant endedAt,
            int startBatteryPct, Integer endBatteryPct, RideStatus status) {
        this(id, accountId, vehicleId, startedAt, endedAt, startBatteryPct, endBatteryPct, status, null, null, null);
    }
}
