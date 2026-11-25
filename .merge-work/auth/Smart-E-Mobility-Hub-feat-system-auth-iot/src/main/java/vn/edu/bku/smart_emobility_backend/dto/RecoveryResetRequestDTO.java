package vn.edu.bku.smart_emobility_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RecoveryResetRequestDTO(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String recoveryToken,
        @NotBlank String newPassword,
        @NotBlank String confirmPassword) {
    @Override
    public String toString() {
        return "RecoveryResetRequestDTO[REDACTED]";
    }
}
