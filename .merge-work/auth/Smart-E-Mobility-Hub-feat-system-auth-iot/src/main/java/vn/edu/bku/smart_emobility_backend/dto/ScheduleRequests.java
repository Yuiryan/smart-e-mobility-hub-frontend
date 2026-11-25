package vn.edu.bku.smart_emobility_backend.dto;

import java.time.Instant;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public final class ScheduleRequests {
    private ScheduleRequests() {}
    public record Build(@NotBlank String hubId) {}
    public record Edit(@NotNull @PositiveOrZero Long revision, @NotNull Instant startAt, @NotNull Instant endAt) {}
    public record Confirm(@NotNull @PositiveOrZero Long revision) {}
}
