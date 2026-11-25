package vn.edu.bku.smart_emobility_backend.common.domain;

import java.time.Instant;

public record ParkingReservation(String id, String accountId, String vehicleId,
        String parkingSpaceId, Instant startsAt, Instant endsAt, Instant createdAt,
        Instant holdExpiresAt, ParkingReservationStatus status, String studentId) implements IdentifiedEntity {
    public ParkingReservation(String id,String accountId,String vehicleId,String spaceId,Instant start,Instant end,Instant createdAt,Instant deadline,ParkingReservationStatus status) {
        this(id,accountId,vehicleId,spaceId,start,end,createdAt,deadline,status,null);
    }
}
