package vn.edu.bku.smart_emobility_backend.common.domain;

import java.time.Instant;
import java.util.List;

public record ChargingSchedule(String id, String proposalId, String hubId, String operatorId,
        Instant confirmedAt, List<String> allocationIds) implements IdentifiedEntity {
    public ChargingSchedule {
        allocationIds = List.copyOf(allocationIds);
    }
}
