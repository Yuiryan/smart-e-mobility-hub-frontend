package vn.edu.bku.smart_emobility_backend.iot;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class IoTSimulator {
    private final SimulatorAdapter adapter;
    private final SimulatorProperties properties;
    private final Clock clock;
    private final Map<String, Integer> nextFrame = new HashMap<>();
    private Instant lastTick;
    private final StationSensors stations;

    public IoTSimulator(SimulatorAdapter adapter, SimulatorProperties properties, Clock clock) {
        this(adapter, properties, clock, new StationSensors(clock));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public IoTSimulator(SimulatorAdapter adapter, SimulatorProperties properties, Clock clock, StationSensors stations) {
        this.stations = stations;
        this.adapter = adapter;
        this.properties = properties;
        this.clock = clock;
    }

    public synchronized void tick(Instant now) {
        if (now.isAfter(clock.instant())) {
            throw new IllegalArgumentException("Simulator tick is in the future");
        }
        if (lastTick != null && !now.isAfter(lastTick)) {
            return;
        }
        for (var device : properties.devices()) {
            int index = nextFrame.getOrDefault(device.vehicleId(), 0);
            adapter.emitTelemetry(device.frames().get(index).reading(device.vehicleId(), now));
            nextFrame.put(device.vehicleId(), (index + 1) % device.frames().size());
        }
        stations.tick(properties.stations(), now);
        lastTick = now;
    }
}
