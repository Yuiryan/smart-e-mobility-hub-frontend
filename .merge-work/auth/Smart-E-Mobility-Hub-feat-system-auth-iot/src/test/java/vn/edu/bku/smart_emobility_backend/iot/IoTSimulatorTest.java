package vn.edu.bku.smart_emobility_backend.iot;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import vn.edu.bku.smart_emobility_backend.common.domain.DataQuality;
import vn.edu.bku.smart_emobility_backend.common.domain.VehicleStatus;
import static org.junit.jupiter.api.Assertions.*;

class IoTSimulatorTest {
    private final Instant now = Instant.parse("2026-01-01T12:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final SimulatorAdapter adapter = new SimulatorAdapter(clock);
    private final IoTGateway gateway = new IoTGateway(adapter, clock);
    private final SimulatorProperties.Frame full = new SimulatorProperties.Frame(
            55.0, VehicleStatus.IN_USE, 10.0, 106.0, VehicleReading.LockStatus.UNLOCKED);
    private final SimulatorProperties.Frame partial = new SimulatorProperties.Frame(
            null, VehicleStatus.IN_USE, null, null, VehicleReading.LockStatus.UNLOCKED);
    private final SimulatorProperties.Device device = new SimulatorProperties.Device("v1", List.of(full, partial));

    @Test
    void independentDevicesCycleWithoutAdvancingOnRepeatedOrLateTick() {
        var other = new SimulatorProperties.Device("v2", List.of(full));
        var simulator = new IoTSimulator(adapter,
                new SimulatorProperties(Duration.ofSeconds(4), List.of(device, other)), clock);
        simulator.tick(now.minusSeconds(8));
        simulator.tick(now.minusSeconds(8));
        simulator.tick(now.minusSeconds(10));
        assertThrows(IllegalArgumentException.class, () -> simulator.tick(now.plusSeconds(1)));
        simulator.tick(now.minusSeconds(4));
        assertEquals(DataQuality.MISSING, gateway.getVehicleTelemetry("v1").quality());
        assertNull(gateway.getVehicleTelemetry("v1").batteryPct());
        assertEquals(DataQuality.FRESH, gateway.getVehicleTelemetry("v2").quality());
        simulator.tick(now);
        assertEquals(DataQuality.FRESH, gateway.getVehicleTelemetry("v1").quality());
        assertEquals(now, gateway.getVehicleTelemetry("v1").observedAt());
        assertEquals(DataQuality.MISSING, gateway.getVehicleTelemetry("unknown").quality());
    }

    @Test
    void registersConfiguredDelayAndPublishesThroughScheduledCallback() {
        var properties = new SimulatorProperties(Duration.ofSeconds(5), List.of(device));
        var simulator = new IoTSimulator(adapter, properties, clock);
        var registrar = new ScheduledTaskRegistrar();
        new SimulatorConfiguration(simulator, properties, clock).configureTasks(registrar);
        var task = registrar.getFixedDelayTaskList().getFirst();
        assertEquals(properties.tickInterval(), task.getIntervalDuration());
        task.getRunnable().run();
        assertEquals(now, gateway.getVehicleTelemetry("v1").observedAt());
    }

    @Test
    void emptyFleetDoesNotScheduleTicks() {
        var properties = new SimulatorProperties(Duration.ofSeconds(3), null);
        var registrar = new ScheduledTaskRegistrar();
        new SimulatorConfiguration(new IoTSimulator(adapter, properties, clock), properties, clock)
                .configureTasks(registrar);
        assertTrue(registrar.getFixedDelayTaskList().isEmpty());
    }

    @Test
    void rejectsBadIntervalsDuplicateDevicesAndInvalidFrames() {
        assertThrows(IllegalArgumentException.class, () -> new SimulatorProperties(Duration.ofMillis(2999), null));
        assertThrows(IllegalArgumentException.class, () -> new SimulatorProperties(Duration.ofMillis(5001), null));
        assertThrows(IllegalArgumentException.class,
                () -> new SimulatorProperties(Duration.ofSeconds(4), List.of(device, device)));
        assertThrows(IllegalArgumentException.class, () -> new SimulatorProperties.Device("v", List.of()));
        var bad = new SimulatorProperties.Frame(-1.0, null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> new SimulatorProperties.Device("v", List.of(bad)));
    }
}
