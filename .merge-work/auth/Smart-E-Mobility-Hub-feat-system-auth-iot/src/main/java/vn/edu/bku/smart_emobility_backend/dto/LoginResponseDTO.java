package vn.edu.bku.smart_emobility_backend.dto;

import java.time.Instant;

import vn.edu.bku.smart_emobility_backend.common.domain.AccountRole;

public record LoginResponseDTO(
        String sessionToken,
        String tokenType,
        Instant expiresAt,
        UserSummary user
) {

    public record UserSummary(
            String accountId,
            String studentId,
            String email,
            AccountRole role
    ) {
    }

    @Override
    public String toString() {
        return "LoginResponseDTO[sessionToken=REDACTED]";
    }
}
