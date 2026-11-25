package vn.edu.bku.smart_emobility_backend.common.domain;

import java.time.Instant;

public record Account(String id, String email, String passwordHash, AccountRole role,
        boolean active, Instant createdAt) implements IdentifiedEntity { }
