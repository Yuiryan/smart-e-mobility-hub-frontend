package vn.edu.bku.smart_emobility_backend.common.store;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import vn.edu.bku.smart_emobility_backend.dto.operator.HubMetricsDTO;

/**
 * LatestDataStore (UC-08): Stores the most recent valid hub status snapshot
 * for fallback when live telemetry/IoT data is unavailable or disconnected.
 * Older snapshots never overwrite newer ones.
 */
@Component
public class LatestDataStore {
    private final Map<String, HubMetricsDTO> latestByHub = new ConcurrentHashMap<>();
    private long latestStateVersion = -1L;

    public synchronized void save(List<HubMetricsDTO> data, long stateVersion) {
        if (data == null || data.isEmpty()) {
            return;
        }
        if (stateVersion < latestStateVersion) {
            return;
        }
        for (HubMetricsDTO hub : data) {
            if (hub != null && hub.hubId() != null) {
                latestByHub.put(hub.hubId(), hub);
            }
        }
        latestStateVersion = stateVersion;
    }

    public synchronized void save(List<HubMetricsDTO> data) {
        save(data, latestStateVersion < 0 ? 0 : latestStateVersion);
    }

    public synchronized List<HubMetricsDTO> readLatest() {
        return latestByHub.values().stream()
                .sorted((a, b) -> a.hubId().compareTo(b.hubId()))
                .toList();
    }

    public synchronized Optional<HubMetricsDTO> readLatestHub(String hubId) {
        return Optional.ofNullable(latestByHub.get(hubId));
    }

    public synchronized void clear() {
        latestByHub.clear();
        latestStateVersion = -1L;
    }
}
