package vn.edu.bku.smart_emobility_backend.dto.operator;

import vn.edu.bku.smart_emobility_backend.common.domain.DataQuality;

public record ChargingMetricsDTO(
        int activePortCount,
        int totalUsablePortCount,
        Double portUtilizationPct,
        Double measuredPowerW,
        double powerLimitW,
        Double powerUtilizationPct,
        int waitingRequestCount,
        AlertSeverity severity,
        DataQuality dataQuality,
        String observedAt) {
}
