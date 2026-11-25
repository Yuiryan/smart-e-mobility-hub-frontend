package vn.edu.bku.smart_emobility_backend.iot;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import vn.edu.bku.smart_emobility_backend.common.domain.ChargingPortStatus;
import vn.edu.bku.smart_emobility_backend.common.domain.DataQuality;

@Component
public class StationSensors {
    public record Port(ChargingPortStatus status, Double powerKw) {
        public Port {
            if (powerKw != null && (!Double.isFinite(powerKw) || powerKw < 0)) {
                throw new IllegalArgumentException("Invalid measured power");
            }
        }
    }

    public record Frame(Integer occupiedSpaces, Map<String, Port> ports) {
        public Frame {
            if (occupiedSpaces != null && occupiedSpaces < 0) {
                throw new IllegalArgumentException("Invalid occupied space count");
            }
            ports = ports == null ? Map.of() : Map.copyOf(ports);
            if (ports.keySet().stream().anyMatch(String::isBlank)) {
                throw new IllegalArgumentException("Missing port identifier");
            }
        }
    }

    public record Station(String hubId, List<Frame> frames) {
        public Station {
            if (hubId == null || hubId.isBlank() || frames == null || frames.isEmpty()) {
                throw new IllegalArgumentException("Station requires hubId and frames");
            }
            frames = List.copyOf(frames);
        }
    }

    public record Measurement<T>(T value, Instant observedAt, DataQuality quality) {}
    private record Sample(Frame frame, Instant observedAt, int nextIndex) {}
    private final Map<String, Sample> latest = new HashMap<>();
    private final Clock clock;
    private static final Duration FRESHNESS_LIMIT = vn.edu.bku.smart_emobility_backend.common.domain.MobilityRules.TELEMETRY_FRESHNESS_LIMIT;

    public StationSensors(Clock clock) {
        this.clock = clock;
    }

    synchronized void tick(List<Station> stations, Instant now) {
        for (Station station : stations) {
            Sample previous = latest.get(station.hubId());
            int index = previous == null ? 0 : previous.nextIndex();
            latest.put(station.hubId(), new Sample(station.frames().get(index), now,
                    (index + 1) % station.frames().size()));
        }
    }

    public synchronized Measurement<Frame> readHubStatus(String hubId) {
        Sample sample = latest.get(hubId);
        Frame frame = sample == null ? null : sample.frame();
        boolean complete = frame != null && frame.occupiedSpaces() != null
                && frame.ports().values().stream().allMatch(port -> port.status() != null && port.powerKw() != null);
        return measurement(sample, frame, complete);
    }

    public synchronized Measurement<Integer> readOccupiedSpaces(String hubId) {
        Sample sample = latest.get(hubId);
        Integer value = sample == null ? null : sample.frame().occupiedSpaces();
        return measurement(sample, value, value != null);
    }

    public synchronized Measurement<Port> readPort(String hubId, String portId) {
        Sample sample = latest.get(hubId);
        Port port = sample == null ? null : sample.frame().ports().get(portId);
        return measurement(sample, port, port != null && port.status() != null && port.powerKw() != null);
    }

    private <T> Measurement<T> measurement(Sample sample, T value, boolean complete) {
        if (sample == null) {
            return new Measurement<>(null, null, DataQuality.MISSING);
        }
        Duration age = Duration.between(sample.observedAt(), clock.instant());
        DataQuality quality = DataQuality.MISSING;
        if (complete && !age.isNegative()) {
            quality = age.compareTo(FRESHNESS_LIMIT) <= 0 ? DataQuality.FRESH : DataQuality.STALE;
        }
        return new Measurement<>(value, sample.observedAt(), quality);
    }
}
