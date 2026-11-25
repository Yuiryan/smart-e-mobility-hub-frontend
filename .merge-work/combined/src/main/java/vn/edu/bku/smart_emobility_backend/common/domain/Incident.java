package vn.edu.bku.smart_emobility_backend.common.domain;

import java.time.Instant;

public record Incident(String id, String vehicleId, String hubId, String description,
        IncidentSeverity severity, Instant reportedAt, Instant resolvedAt, IncidentStatus status)
        implements IdentifiedEntity { }
