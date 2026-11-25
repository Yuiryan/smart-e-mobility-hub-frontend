package vn.edu.bku.smart_emobility_backend.common.domain;

import java.time.Instant;
import java.util.List;

public record ScheduleProposal(String id, String hubId, Instant createdAt, long revision,
        List<ChargingSlot> slots, List<QueueEntry> queue, List<String> violations,
        String confirmedScheduleId) implements IdentifiedEntity {
    public ScheduleProposal {
        slots = List.copyOf(slots);
        queue = List.copyOf(queue);
        violations = List.copyOf(violations);
    }

    @com.fasterxml.jackson.annotation.JsonProperty(value = "valid", access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    public boolean valid() {
        return violations.isEmpty();
    }

    public record QueueEntry(String requestId, String vehicleId, String reason) {}
}
