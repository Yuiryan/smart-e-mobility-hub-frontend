package vn.edu.bku.smart_emobility_backend.common.domain;

import java.time.Instant;
public record Ride(String id, String accountId, String vehicleId, Instant startedAt,
        Instant endedAt, Double startBatteryPct, Double endBatteryPct, RideStatus status,
        String studentId, String reservationId, String requestId, String startHubId,
        String endHubId, Double distanceKm) implements IdentifiedEntity {
    public Ride(String id, String accountId, String vehicleId, Instant startedAt, Instant endedAt,
            int startBatteryPct, Integer endBatteryPct, RideStatus status) {
        this(id, accountId, vehicleId, startedAt, endedAt, (double) startBatteryPct,
                endBatteryPct == null ? null : endBatteryPct.doubleValue(), status,
                null, null, null, null, null, null);
    }

    public Ride(String id, String accountId, String vehicleId, Instant startedAt, Instant endedAt,
            Double startBatteryPct, Double endBatteryPct, RideStatus status, String studentId,
            String reservationId, String requestId, String startHubId, String endHubId) {
        this(id, accountId, vehicleId, startedAt, endedAt, startBatteryPct, endBatteryPct,
                status, studentId, reservationId, requestId, startHubId, endHubId, null);
    }

    public Ride(String id, String accountId, String vehicleId, Instant startedAt, Instant endedAt,
            int startBatteryPct, Integer endBatteryPct, RideStatus status,
            String originHubId, String destinationHubId, Double distanceKm) {
        this(id, accountId, vehicleId, startedAt, endedAt, (double) startBatteryPct,
                endBatteryPct == null ? null : endBatteryPct.doubleValue(), status,
                null, null, null, originHubId, destinationHubId, distanceKm);
    }

    public String originHubId() {
        return startHubId;
    }

    public String destinationHubId() {
        return endHubId;
    }

    public Ride complete(Instant at, double battery, String hub) {
        return new Ride(id, accountId, vehicleId, startedAt, at, startBatteryPct, battery,
                RideStatus.COMPLETED, studentId, reservationId, requestId, startHubId, hub, distanceKm);
    }
}
