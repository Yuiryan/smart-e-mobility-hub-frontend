package vn.edu.bku.smart_emobility_backend.simulation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record Scenario(@NotBlank @Size(max = 120) String scenarioName, @NotNull @Valid Parameters parameters) {
    public record Parameters(@NotNull Double simulatedDurationHours, @NotNull Double rentalMultiplier, String hubOutageId) {}
}
