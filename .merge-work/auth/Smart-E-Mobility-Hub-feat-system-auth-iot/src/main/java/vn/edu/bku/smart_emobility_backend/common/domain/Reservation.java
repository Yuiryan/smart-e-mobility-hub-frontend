package vn.edu.bku.smart_emobility_backend.common.domain;

import java.time.Instant;

public record Reservation(String id, String accountId, String vehicleId, String pickupCodeHash,
        Instant createdAt, Instant expiresAt, ReservationStatus status)
        implements IdentifiedEntity { }
