package vn.edu.bku.smart_emobility_backend.service.operator;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Repository;
import vn.edu.bku.smart_emobility_backend.dto.operator.AlertSeverity;
import vn.edu.bku.smart_emobility_backend.dto.operator.OverloadAlertDTO;

/**
 * AlertRepository (UC-10): Stores current alerts keyed by stable alertId ("{hubId}:{dimension}")
 * without duplicating records on repeated evaluations.
 */
@Repository
public class AlertRepository {
    private final Map<String, OverloadAlertDTO> alertsById = new ConcurrentHashMap<>();

    public synchronized Optional<OverloadAlertDTO> findById(String alertId) {
        return Optional.ofNullable(alertsById.get(alertId));
    }

    public synchronized void upsert(OverloadAlertDTO alert) {
        if (alert != null && alert.alertId() != null) {
            alertsById.put(alert.alertId(), alert);
        }
    }

    public synchronized List<OverloadAlertDTO> listActive() {
        return alertsById.values().stream()
                .filter(a -> a.severity() != AlertSeverity.NORMAL)
                .toList();
    }

    public synchronized List<OverloadAlertDTO> listAll() {
        return List.copyOf(alertsById.values());
    }

    public synchronized void clear() {
        alertsById.clear();
    }
}
