package vn.edu.bku.smart_emobility_backend.common.domain;

import java.time.Duration;
import java.time.Instant;

public record Vehicle(String vehicleId, String hubId, String parkingSpaceId,
        VehicleType vehicleType, Double batteryPct, double batteryCapacityKwh,
        double maxChargingPowerKw, VehicleStatus status, Instant telemetryAt, long version,
        VehicleOwnership ownership, String ownerAccountId) implements IdentifiedEntity {

    @Override
    public String id() {
        return vehicleId;
    }

    public boolean isSearchable(Instant now, Duration freshnessLimit, double minBatteryPct) {
        if (now == null || freshnessLimit == null || freshnessLimit.isNegative()
                || status != VehicleStatus.AVAILABLE || ownership != VehicleOwnership.SHARED
                || batteryPct == null || !Double.isFinite(batteryPct)
                || batteryPct < minBatteryPct || batteryPct > 100.0 || telemetryAt == null) {
            return false;
        }

        Duration telemetryAge = Duration.between(telemetryAt, now);
        return !telemetryAge.isNegative() && telemetryAge.compareTo(freshnessLimit) <= 0;
    }
}
