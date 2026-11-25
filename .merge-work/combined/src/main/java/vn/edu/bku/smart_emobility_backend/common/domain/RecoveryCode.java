package vn.edu.bku.smart_emobility_backend.common.domain;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record RecoveryCode(
        @JsonProperty("tokenId") @JsonAlias("id") String id,
        String accountId,
        @JsonProperty("hashCode") String codeHash,
        Instant expiresAt,
        @JsonProperty("isUsed") boolean used,
        int failedAttempts
) implements IdentifiedEntity {

    @Override
    public String toString() {
        return "RecoveryCode[id=" + id
                + ", accountId=" + accountId
                + ", used=" + used + "]";
    }
}
