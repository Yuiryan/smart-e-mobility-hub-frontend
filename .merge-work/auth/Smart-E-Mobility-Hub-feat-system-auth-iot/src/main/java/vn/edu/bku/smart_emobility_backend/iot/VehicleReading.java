package vn.edu.bku.smart_emobility_backend.iot;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import vn.edu.bku.smart_emobility_backend.common.domain.VehicleStatus;

public record VehicleReading(String vehicleId, Double batteryPct, VehicleStatus status,
        Double latitude, Double longitude, LockStatus lockStatus, Instant observedAt, String spaceId) {
    private static final Duration FRESHNESS_LIMIT = vn.edu.bku.smart_emobility_backend.common.domain.MobilityRules.TELEMETRY_FRESHNESS_LIMIT;

    public VehicleReading(String vehicleId, Double batteryPct, VehicleStatus status,
            Double latitude, Double longitude, LockStatus lockStatus, Instant observedAt) {
        this(vehicleId, batteryPct, status, latitude, longitude, lockStatus, observedAt, null);
    }

    boolean isCurrentAt(Instant now) {
        Duration age = Duration.between(observedAt, now);
        return !age.isNegative() && age.compareTo(FRESHNESS_LIMIT) <= 0;
    }

    VehicleReading withLockStatus(LockStatus state) {
        return new VehicleReading(vehicleId, batteryPct, status, latitude, longitude, state, observedAt, spaceId);
    }


    public enum LockStatus { LOCKED, UNLOCKED }

    public VehicleReading {
        if (vehicleId == null || vehicleId.isBlank()) {
            throw new IllegalArgumentException("vehicleId is required");
        }
        Objects.requireNonNull(observedAt, "observedAt");
        checkRange(batteryPct, 0, 100, "batteryPct");
        checkRange(latitude, -90, 90, "latitude");
        checkRange(longitude, -180, 180, "longitude");
    }

    private static void checkRange(Double value, double min, double max, String field) {
        if (value != null && (!Double.isFinite(value) || value < min || value > max)) {
            throw new IllegalArgumentException(field + " is outside its valid range");
        }
    }

    boolean complete() {
        return batteryPct != null && status != null && latitude != null && longitude != null && lockStatus != null;
    }
}
