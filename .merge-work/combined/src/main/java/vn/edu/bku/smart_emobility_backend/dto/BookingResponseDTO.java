package vn.edu.bku.smart_emobility_backend.dto;

import java.time.Instant;
import vn.edu.bku.smart_emobility_backend.common.domain.ReservationStatus;

public record BookingResponseDTO(String reservationId, String vehicleId, String hubId,
        ReservationStatus status, String pickupCode, Instant startsAt, Instant endsAt,
        Instant expiresAt, Instant createdAt) { }
