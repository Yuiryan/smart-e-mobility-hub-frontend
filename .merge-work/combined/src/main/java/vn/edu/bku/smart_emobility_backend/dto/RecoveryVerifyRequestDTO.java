package vn.edu.bku.smart_emobility_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import vn.edu.bku.smart_emobility_backend.common.domain.RecoveryPolicy;

public record RecoveryVerifyRequestDTO(
        @NotBlank @Email String email,
        @NotBlank @Pattern(regexp = RecoveryPolicy.CODE_PATTERN) String code) {
    @Override
    public String toString() {
        return "RecoveryVerifyRequestDTO[REDACTED]";
    }
}
