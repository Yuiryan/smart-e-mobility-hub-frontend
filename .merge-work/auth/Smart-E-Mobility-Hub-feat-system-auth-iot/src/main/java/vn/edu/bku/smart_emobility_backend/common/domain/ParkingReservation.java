package vn.edu.bku.smart_emobility_backend.common.domain;

import java.time.Instant;

public record ParkingReservation(String id, String accountId, String vehicleId,
        String parkingSpaceId, Instant startsAt, Instant endsAt, Instant createdAt,
        Instant holdExpiresAt, ParkingReservationStatus status) implements IdentifiedEntity { }
