package vn.edu.bku.smart_emobility_backend.common.domain;

public record ParkingSpace(String id, String hubId, ParkingSpaceStatus status, VehicleType vehicleType,
        String occupiedVehicleId, String parkingReservationId, String vehicleReservationId) implements IdentifiedEntity {
    public ParkingSpace(String id, String hubId, ParkingSpaceStatus status) {
        this(id, hubId, status, null, null, null, null);
    }
    public ParkingSpace parkingHold(String id) {
        return new ParkingSpace(this.id,hubId,id == null ? ParkingSpaceStatus.AVAILABLE : ParkingSpaceStatus.RESERVED,
                vehicleType,occupiedVehicleId,id,vehicleReservationId);
    }
    public ParkingSpace vehicleHold(String reservation) {
        return new ParkingSpace(id,hubId,status,vehicleType,occupiedVehicleId,parkingReservationId,reservation);
    }
    public ParkingSpace withOccupant(String occupant) {
        return new ParkingSpace(id, hubId, occupant == null ? ParkingSpaceStatus.AVAILABLE : ParkingSpaceStatus.OCCUPIED,
                vehicleType, occupant, null, null);
    }
}
