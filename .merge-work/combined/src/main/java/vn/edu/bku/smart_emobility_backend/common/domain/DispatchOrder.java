package vn.edu.bku.smart_emobility_backend.common.domain;

import java.time.Instant;

public record DispatchOrder(String id, String vehicleId, String sourceHubId,
        String destinationHubId, Instant createdAt, DispatchOrderStatus status)
        implements IdentifiedEntity { }
