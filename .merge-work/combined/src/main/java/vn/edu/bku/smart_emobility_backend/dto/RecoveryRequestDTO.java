package vn.edu.bku.smart_emobility_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RecoveryRequestDTO(
        @NotBlank(message = "email is required")
        @Email(message = "email must be valid")
        String email
) {
}
