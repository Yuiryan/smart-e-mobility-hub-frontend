package vn.edu.bku.smart_emobility_backend.common.domain;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record Session(
        @JsonProperty("tokenHash") @JsonAlias("id") String id,
        String accountId,
        Instant createdAt,
        Instant expiresAt
) implements IdentifiedEntity {
}
