package vn.edu.bku.smart_emobility_backend.common.domain;

import java.time.Duration;
import java.time.Instant;

public record Vehicle(String vehicleId, String hubId, String parkingSpaceId,
        VehicleType vehicleType, Double batteryPct, Double batteryCapacityKwh,
        Double maxChargingPowerKw, VehicleStatus status, Instant telemetryAt, long version,
        VehicleOwnership ownership, String ownerAccountId) implements IdentifiedEntity {

    public Vehicle(String vehicleId, String hubId, String parkingSpaceId,
            VehicleType vehicleType, Double batteryPct, int batteryCapacityKwh,
            int maxChargingPowerKw, VehicleStatus status, Instant telemetryAt, long version,
            VehicleOwnership ownership, String ownerAccountId) {
        this(vehicleId, hubId, parkingSpaceId, vehicleType, batteryPct,
                Double.valueOf(batteryCapacityKwh), Double.valueOf(maxChargingPowerKw),
                status, telemetryAt, version, ownership, ownerAccountId);
    }

    public Vehicle(String id, String hubId, int batteryPct, VehicleStatus status) {
        this(id, hubId, null, null, (double) batteryPct, (Double) null, (Double) null, status,
                null, 0L, VehicleOwnership.SHARED, null);
    }

    public Vehicle(String id, String hubId, int batteryPct, VehicleStatus status,
            Double batteryCapacityKWh, Double maxChargingPowerKw) {
        this(id, hubId, null, null, (double) batteryPct, batteryCapacityKWh,
                maxChargingPowerKw, status, null, 0L, VehicleOwnership.SHARED, null);
    }

    @Override
    public String id() {
        return vehicleId;
    }

    public Double batteryCapacityKWh() {
        return batteryCapacityKwh;
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
