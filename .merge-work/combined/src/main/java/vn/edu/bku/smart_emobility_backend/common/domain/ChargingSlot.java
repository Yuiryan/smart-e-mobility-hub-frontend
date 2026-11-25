package vn.edu.bku.smart_emobility_backend.common.domain;

import java.time.Instant;

public record ChargingSlot(String id, String requestId, String vehicleId, String portId,
        Instant startAt, Instant endAt, double powerKw) {}
