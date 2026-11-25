package vn.edu.bku.smart_emobility_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import vn.edu.bku.smart_emobility_backend.common.domain.AccountRole;

public record LoginRequestDTO(
        @NotBlank(message = "email is required")
        @Email(message = "email must be valid")
        String email,

        @NotBlank(message = "password is required")
        String password,

        @NotNull(message = "role is required")
        AccountRole role
) {

    @Override
    public String toString() {
        return "LoginRequestDTO[credentials=REDACTED]";
    }
}