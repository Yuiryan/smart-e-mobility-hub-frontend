package vn.edu.bku.smart_emobility_backend.common.domain;

public record Vehicle(String id, String hubId, int batteryPct, VehicleStatus status,
        Double batteryCapacityKWh, Double maxChargingPowerKw) implements IdentifiedEntity {
    public Vehicle(String id, String hubId, int batteryPct, VehicleStatus status) {
        this(id, hubId, batteryPct, status, null, null);
    }
}
