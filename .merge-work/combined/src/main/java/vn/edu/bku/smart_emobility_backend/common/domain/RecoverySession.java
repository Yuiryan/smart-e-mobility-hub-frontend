package vn.edu.bku.smart_emobility_backend.common.domain;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record RecoverySession(
        @JsonProperty("tokenHash") @JsonAlias("id") String id,
        String accountId,
        String recoveryCodeId,
        Instant createdAt,
        Instant expiresAt
) implements IdentifiedEntity {

    @Override
    public String toString() {
        return "RecoverySession[accountId=" + accountId
                + ", recoveryCodeId=" + recoveryCodeId + "]";
    }
}
