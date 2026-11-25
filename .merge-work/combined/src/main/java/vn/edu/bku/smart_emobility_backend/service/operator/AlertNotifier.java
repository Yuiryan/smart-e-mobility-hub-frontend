package vn.edu.bku.smart_emobility_backend.service.operator;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import vn.edu.bku.smart_emobility_backend.dto.operator.OverloadAlertDTO;

/**
 * AlertNotifier (UC-10): Tracks proactive critical alert transitions when a Hub dimension
 * enters CRITICAL severity.
 */
@Component
public class AlertNotifier {
    private final List<OverloadAlertDTO> publishedCriticalAlerts = new CopyOnWriteArrayList<>();

    public void publishCritical(OverloadAlertDTO alert) {
        if (alert != null) {
            publishedCriticalAlerts.add(alert);
        }
    }

    public List<OverloadAlertDTO> publishedCriticalAlerts() {
        return List.copyOf(publishedCriticalAlerts);
    }

    public void clear() {
        publishedCriticalAlerts.clear();
    }
}
