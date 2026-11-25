package vn.edu.bku.smart_emobility_backend.dto;

import java.time.Instant;

public record RecoveryGrantDTO(
        String recoveryToken, Instant expiresAt) {
    @Override
    public String toString() {
        return "RecoveryGrantDTO[REDACTED]";
    }
}
