package vn.edu.bku.smart_emobility_backend.common.domain;

import java.time.Instant;

public record Session(String id, String accountId, Instant createdAt, Instant expiresAt)
        implements IdentifiedEntity { }
