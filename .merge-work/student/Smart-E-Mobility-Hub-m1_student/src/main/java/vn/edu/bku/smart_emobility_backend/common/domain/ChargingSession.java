package vn.edu.bku.smart_emobility_backend.common.domain;

import java.time.Instant;

/** Actual charging times reported by the charging system for an allocation. */
public record ChargingSession(String sessionId, String allocationId, Instant actualStartAt, Instant actualEndAt)
        implements IdentifiedEntity {
    @Override
    public String id() { return sessionId; }
}
