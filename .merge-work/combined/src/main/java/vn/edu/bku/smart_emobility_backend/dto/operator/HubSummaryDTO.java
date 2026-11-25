package vn.edu.bku.smart_emobility_backend.dto.operator;

public record HubSummaryDTO(
        String hubId,
        String hubName,
        int capacity,
        int usableCapacity,
        int blockedCount,
        int freeSpaceCount,
        double hubPowerLimitW,
        long version) {
}
