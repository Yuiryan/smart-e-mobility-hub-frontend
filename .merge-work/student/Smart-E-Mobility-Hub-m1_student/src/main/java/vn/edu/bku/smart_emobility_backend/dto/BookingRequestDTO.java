package vn.edu.bku.smart_emobility_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record BookingRequestDTO(@NotBlank String vehicleId,
        @NotNull Instant startsAt,
        @NotNull Instant endsAt) { }
