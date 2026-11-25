package vn.edu.bku.smart_emobility_backend.service.operator;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.edu.bku.smart_emobility_backend.common.domain.DataQuality;
import vn.edu.bku.smart_emobility_backend.common.domain.MobilityHub;
import vn.edu.bku.smart_emobility_backend.common.error.ApiException;
import vn.edu.bku.smart_emobility_backend.common.error.GlobalExceptionHandler;
import vn.edu.bku.smart_emobility_backend.common.store.InMemoryDataStore;
import vn.edu.bku.smart_emobility_backend.common.store.StoreSnapshot;
import vn.edu.bku.smart_emobility_backend.dto.operator.*;
import vn.edu.bku.smart_emobility_backend.dto.operator.OverloadAlertDTO.AlertListResponseDTO;

/**
 * OverloadMonitor (UC-10): Evaluates parking space capacity, charging port usage,
 * and charging power utilization across Mobility Hubs using the same snapshot
 * and calculation logic as MonitoringAggregator (UC-08).
 */
@Service
public class OverloadMonitor {
    private static final String UNIT_PERCENT = "PERCENT";

    private final InMemoryDataStore dataStore;
    private final MonitoringAggregator monitoringAggregator;
    private final AlertRepository alertRepository;
    private final AlertNotifier alertNotifier;
    private final Clock clock;
    private final double warningThresholdPct;
    private final double criticalThresholdPct;

    public OverloadMonitor(
            InMemoryDataStore dataStore,
            MonitoringAggregator monitoringAggregator,
            AlertRepository alertRepository,
            AlertNotifier alertNotifier,
            Clock clock,
            @Value("${app.operator.warning-threshold-pct:80}") double warningThresholdPct,
            @Value("${app.operator.critical-threshold-pct:95}") double criticalThresholdPct) {
        this.dataStore = dataStore;
        this.monitoringAggregator = monitoringAggregator;
        this.alertRepository = alertRepository;
        this.alertNotifier = alertNotifier;
        this.clock = clock;
        this.warningThresholdPct = warningThresholdPct;
        this.criticalThresholdPct = criticalThresholdPct;
    }

    public synchronized AlertListResponseDTO listAlerts(
            String hubIdFilter,
            AlertSeverity severityFilter,
            AlertDimension dimensionFilter) {
        StoreSnapshot snapshot = dataStore.snapshot();
        Instant now = clock.instant();

        if (hubIdFilter != null) {
            boolean exists = snapshot.hubs().stream().anyMatch(h -> Objects.equals(h.hubId(), hubIdFilter));
            if (!exists) {
                throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                        "Không tìm thấy Mobility Hub: " + hubIdFilter);
            }
        }

        List<OverloadAlertDTO> allEvaluated = evaluateAllHubs(snapshot, now);

        List<OverloadAlertDTO> filtered = allEvaluated.stream()
                .filter(a -> hubIdFilter == null || hubIdFilter.equals(a.hubId()))
                .filter(a -> dimensionFilter == null || dimensionFilter == a.dimension())
                .filter(a -> {
                    if (severityFilter != null) {
                        return a.severity() == severityFilter;
                    }
                    return a.severity() != AlertSeverity.NORMAL;
                })
                .sorted(Comparator.comparingInt((OverloadAlertDTO a) -> severityOrder(a.severity()))
                        .thenComparing(OverloadAlertDTO::hubId)
                        .thenComparingInt(a -> a.dimension().ordinal()))
                .toList();

        return new AlertListResponseDTO(
                GlobalExceptionHandler.formatTimestamp(now),
                snapshot.version(),
                filtered);
    }

    public synchronized List<OverloadAlertDTO> evaluateAllHubs(StoreSnapshot snapshot, Instant now) {
        List<OverloadAlertDTO> results = new ArrayList<>();
        List<MobilityHub> sortedHubs = snapshot.hubs().stream()
                .sorted(Comparator.comparing(MobilityHub::hubId))
                .toList();

        for (MobilityHub hub : sortedHubs) {
            HubMetricsDTO metrics = monitoringAggregator.computeHubMetrics(hub, snapshot, now);
            List<OverloadAlertDTO> hubAlerts = evaluateHubDimensions(hub, metrics);
            for (OverloadAlertDTO alert : hubAlerts) {
                Optional<OverloadAlertDTO> previous = alertRepository.findById(alert.alertId());
                alertRepository.upsert(alert);
                if (alert.severity() == AlertSeverity.CRITICAL
                        && (previous.isEmpty() || previous.get().severity() != AlertSeverity.CRITICAL)) {
                    alertNotifier.publishCritical(alert);
                }
                results.add(alert);
            }
        }
        return results;
    }

    private List<OverloadAlertDTO> evaluateHubDimensions(MobilityHub hub, HubMetricsDTO metrics) {
        List<OverloadAlertDTO> alerts = new ArrayList<>(3);

        // 1. PARKING dimension
        Double rawParkingPct = metrics.usableCapacity() > 0
                ? (100.0 * (metrics.occupiedCount() + metrics.reservedCount() + metrics.inboundCount()))
                        / metrics.usableCapacity()
                : null;
        alerts.add(buildDimensionAlert(
                hub.hubId(),
                AlertDimension.PARKING,
                rawParkingPct,
                metrics.occupancyPct(),
                metrics.usableCapacity() > 0,
                metrics.dataQuality(),
                metrics.observedAt(),
                "Sức chứa chỗ đỗ"));

        // 2. CHARGING_PORTS dimension
        ChargingMetricsDTO charging = metrics.charging();
        Double rawPortPct = charging.totalUsablePortCount() > 0
                ? (100.0 * charging.activePortCount()) / charging.totalUsablePortCount()
                : null;
        boolean validPortCap = charging.totalUsablePortCount() > 0;
        alerts.add(buildDimensionAlert(
                hub.hubId(),
                AlertDimension.CHARGING_PORTS,
                rawPortPct,
                charging.portUtilizationPct(),
                validPortCap,
                charging.dataQuality(),
                charging.observedAt(),
                "Tỷ lệ sử dụng cổng sạc"));

        // 3. CHARGING_POWER dimension
        Double rawPowerPct = (charging.powerLimitW() > 0 && charging.measuredPowerW() != null)
                ? (100.0 * charging.measuredPowerW()) / charging.powerLimitW()
                : null;
        boolean validPowerCap = charging.powerLimitW() > 0;
        alerts.add(buildDimensionAlert(
                hub.hubId(),
                AlertDimension.CHARGING_POWER,
                rawPowerPct,
                charging.powerUtilizationPct(),
                validPowerCap,
                charging.dataQuality(),
                charging.observedAt(),
                "Tỷ lệ tải công suất sạc"));

        return alerts;
    }

    private OverloadAlertDTO buildDimensionAlert(
            String hubId,
            AlertDimension dimension,
            Double rawPct,
            Double roundedPct,
            boolean validCapacity,
            DataQuality dataQuality,
            String observedAt,
            String labelPrefix) {
        String alertId = hubId + ":" + dimension.name();
        AlertSeverity severity;
        Double threshold = null;
        AlertReasonCode reasonCode;
        String message;

        if (!validCapacity) {
            severity = AlertSeverity.MISSING_DATA;
            reasonCode = AlertReasonCode.INVALID_CAPACITY;
            message = labelPrefix + " không có thông số sức chứa hợp lệ";
        } else if (dataQuality == DataQuality.MISSING || rawPct == null) {
            severity = AlertSeverity.MISSING_DATA;
            reasonCode = AlertReasonCode.MISSING_DATA;
            message = labelPrefix + " thiếu dữ liệu quan sát";
        } else if (dataQuality == DataQuality.STALE) {
            severity = AlertSeverity.MISSING_DATA;
            reasonCode = AlertReasonCode.STALE_DATA;
            message = labelPrefix + " có dữ liệu quan sát đã cũ (trễ hạn)";
        } else {
            // FRESH
            if (rawPct >= criticalThresholdPct) {
                severity = AlertSeverity.CRITICAL;
                threshold = MonitoringAggregator.roundPct(criticalThresholdPct);
                reasonCode = AlertReasonCode.HIGH_UTILIZATION;
                message = labelPrefix + " vượt ngưỡng nghiêm trọng (" + formatPctText(roundedPct) + "%)";
            } else if (rawPct >= warningThresholdPct) {
                severity = AlertSeverity.WARNING;
                threshold = MonitoringAggregator.roundPct(warningThresholdPct);
                reasonCode = AlertReasonCode.HIGH_UTILIZATION;
                message = labelPrefix + " đã dùng " + formatPctText(roundedPct) + "%";
            } else {
                severity = AlertSeverity.NORMAL;
                threshold = null;
                reasonCode = AlertReasonCode.NORMAL_UTILIZATION;
                message = labelPrefix + " ở mức bình thường (" + formatPctText(roundedPct) + "%)";
            }
        }

        return new OverloadAlertDTO(
                alertId,
                hubId,
                dimension,
                severity,
                validCapacity ? roundedPct : null,
                threshold,
                UNIT_PERCENT,
                reasonCode,
                message,
                observedAt,
                dataQuality);
    }

    private static int severityOrder(AlertSeverity severity) {
        return switch (severity) {
            case CRITICAL -> 0;
            case WARNING -> 1;
            case MISSING_DATA -> 2;
            case NORMAL -> 3;
        };
    }

    private static String formatPctText(Double pct) {
        if (pct == null) {
            return "N/A";
        }
        if (pct == Math.floor(pct)) {
            return String.valueOf(pct.intValue());
        }
        return String.valueOf(pct);
    }
}
