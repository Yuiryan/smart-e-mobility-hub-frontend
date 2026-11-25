package vn.edu.bku.smart_emobility_backend.dto.operator;

import java.util.Map;
import vn.edu.bku.smart_emobility_backend.common.domain.DataQuality;

public record HubMetricsDTO(
        String hubId,
        String hubName,
        int capacity,
        int usableCapacity,
        int blockedCount,
        int freeSpaceCount,
        double hubPowerLimitW,
        long version,
        int occupiedCount,
        int reservedCount,
        int inboundCount,
        Double occupancyPct,
        AlertSeverity parkingSeverity,
        Map<String, Integer> vehicleCountsByStatus,
        Map<String, Integer> chargingPortsByStatus,
        ChargingMetricsDTO charging,
        AlertSeverity severity,
        DataQuality dataQuality,
        String observedAt) {
}
