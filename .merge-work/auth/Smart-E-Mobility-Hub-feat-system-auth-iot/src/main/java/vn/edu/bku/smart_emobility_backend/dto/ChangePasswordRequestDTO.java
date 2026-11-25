package vn.edu.bku.smart_emobility_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import vn.edu.bku.smart_emobility_backend.common.domain.PasswordPolicy;

public record ChangePasswordRequestDTO(
        @NotBlank(message = "currentPassword is required")
        String currentPassword,

        @NotBlank(message = "newPassword is required")
        @Size(
                min = PasswordPolicy.MIN_LENGTH,
                message = "newPassword must contain at least 8 characters")
        String newPassword,

        @NotBlank(message = "confirmPassword is required")
        String confirmPassword
) {

    @Override
    public String toString() {
        return "ChangePasswordRequestDTO[credentials=REDACTED]";
    }
}