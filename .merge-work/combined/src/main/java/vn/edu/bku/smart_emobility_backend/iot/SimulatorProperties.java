package vn.edu.bku.smart_emobility_backend.iot;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;
import vn.edu.bku.smart_emobility_backend.common.domain.VehicleStatus;

@ConfigurationProperties("app.iot.simulator")
public record SimulatorProperties(@DefaultValue("4s") Duration tickInterval, List<Device> devices,
        List<StationSensors.Station> stations) {
    public SimulatorProperties(Duration tickInterval, List<Device> devices) {
        this(tickInterval, devices, List.of());
    }

    private static final Duration MIN_INTERVAL = Duration.ofSeconds(3);
    private static final Duration MAX_INTERVAL = Duration.ofSeconds(5);

    @ConstructorBinding
    public SimulatorProperties {
        stations = stations == null ? List.of() : List.copyOf(stations);
        if (stations.stream().map(StationSensors.Station::hubId).distinct().count() != stations.size()) {
            throw new IllegalArgumentException("Duplicate station hubId");
        }
        if (tickInterval.compareTo(MIN_INTERVAL) < 0 || tickInterval.compareTo(MAX_INTERVAL) > 0) {
            throw new IllegalArgumentException("Simulator tick interval must be between 3 and 5 seconds");
        }
        devices = devices == null ? List.of() : List.copyOf(devices);
        var ids = new HashSet<String>();
        for (Device device : devices) {
            if (!ids.add(device.vehicleId())) {
                throw new IllegalArgumentException("Duplicate simulator vehicle: " + device.vehicleId());
            }
        }
    }

    public record Device(String vehicleId, List<Frame> frames) {
        public Device {
            if (frames == null || frames.isEmpty()) {
                throw new IllegalArgumentException("A simulated device needs at least one frame");
            }
            frames = List.copyOf(frames);
            for (Frame frame : frames) {
                // Validate every configured reading before scheduling the first tick.
                frame.reading(vehicleId, Instant.EPOCH);
            }
        }
    }

    public record Frame(Double batteryPct, VehicleStatus status, Double latitude, Double longitude,
            VehicleReading.LockStatus lockStatus, String spaceId) {
        @ConstructorBinding
        public Frame {
            if (spaceId != null && spaceId.isBlank()) {
                throw new IllegalArgumentException("spaceId must be nonblank when present");
            }
        }

        public Frame(Double batteryPct, VehicleStatus status, Double latitude, Double longitude,
                VehicleReading.LockStatus lockStatus) {
            this(batteryPct, status, latitude, longitude, lockStatus, null);
        }

        VehicleReading reading(String vehicleId, Instant observedAt) {
            return new VehicleReading(vehicleId, batteryPct, status, latitude, longitude, lockStatus, observedAt, spaceId);
        }
    }
}
