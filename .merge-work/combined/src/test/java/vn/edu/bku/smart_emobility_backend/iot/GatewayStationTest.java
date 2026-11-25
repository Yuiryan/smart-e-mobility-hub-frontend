package vn.edu.bku.smart_emobility_backend.iot;

import java.nio.file.Path;
import java.time.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.bku.smart_emobility_backend.common.domain.*;
import vn.edu.bku.smart_emobility_backend.common.store.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GatewayStationTest {
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    @TempDir Path directory;

    @Test
    void restoresKnownAndUncertainOperationsWithoutDispatch() {
        var json = new JsonStore(JsonMapper.builder().build(), directory.resolve("state.json").toString());
        var ram = new InMemoryDataStore();
        var coordinator = new StateCoordinator(ram, json);
        var device = mock(SimulatorAdapter.class);
        var journal = new JournaledIoTAdapter(device, ram, coordinator, clock);
        var done = new DeviceCommand("c1", DeviceCommand.Type.UNLOCK, "v1", null);
        var pending = new DeviceCommand("c2", DeviceCommand.Type.LOCK, "v1", "p1");
        var ack = new DeviceResponse(done, CommandStatus.ACKNOWLEDGED, now, 42.0, null);
        when(device.send(done)).thenReturn(ack);
        when(device.send(pending)).thenReturn(new DeviceResponse(pending, CommandStatus.PENDING, now, null, null));
        assertEquals(ack, journal.send(done));
        journal.send(pending);

        var restored = new InMemoryDataStore();
        restored.restore(json.load().orElseThrow());
        var restartedDevice = mock(SimulatorAdapter.class);
        var restarted = new JournaledIoTAdapter(restartedDevice, restored, new StateCoordinator(restored, json), clock);
        restarted.reconcileAfterRestart();
        assertEquals(ack, restarted.send(done));
        assertEquals(CommandStatus.AWAITING_RECONCILIATION, restarted.send(pending).status());
        assertThrows(IllegalArgumentException.class, () -> restarted.send(
                new DeviceCommand("c1", DeviceCommand.Type.UNLOCK, "other", null)));
        verify(restartedDevice, never()).send(any());
    }

    @Test
    void failedIntentWritePreventsDispatch() {
        var ram = new InMemoryDataStore();
        var json = mock(JsonStore.class);
        doThrow(new IllegalStateException("disk unavailable")).when(json).save(any());
        var device = mock(SimulatorAdapter.class);
        var journal = new JournaledIoTAdapter(device, ram, new StateCoordinator(ram, json), clock);
        assertThrows(IllegalStateException.class, () -> journal.send(
                new DeviceCommand("c", DeviceCommand.Type.UNLOCK, "v", null)));
        verifyNoInteractions(device);
        assertTrue(ram.findAll(DeviceOperation.class).isEmpty());
    }

    @Test
    void stationOnlyTickPublishesAndReplacesPortMeasurements() {
        var time = mock(Clock.class);
        when(time.instant()).thenReturn(now);
        var sensors = new StationSensors(time);
        var port = new StationSensors.Port(ChargingPortStatus.AVAILABLE, 0.0);
        var frames = List.of(new StationSensors.Frame(0, Map.of("p", port)), new StationSensors.Frame(null, Map.of()));
        var properties = new SimulatorProperties(Duration.ofSeconds(3), List.of(),
                List.of(new StationSensors.Station("h", frames)));
        var simulator = new IoTSimulator(new SimulatorAdapter(time), properties, time, sensors);
        simulator.tick(now);
        when(time.instant()).thenReturn(now.plusSeconds(600));
        assertEquals(DataQuality.FRESH, sensors.readPort("h", "p").quality());
        assertEquals(0, sensors.readOccupiedSpaces("h").value());
        when(time.instant()).thenReturn(now.plusSeconds(600).plusNanos(1));
        assertEquals(DataQuality.STALE, sensors.readPort("h", "p").quality());
        simulator.tick(time.instant());
        assertEquals(DataQuality.MISSING, sensors.readPort("h", "p").quality());
        assertNull(sensors.readOccupiedSpaces("h").value());
        assertThrows(IllegalArgumentException.class, () -> new StationSensors.Port(null, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new StationSensors.Frame(-1, null));
    }

    @Test
    void confirmedAllocationBlocksDeviceHoldButAllowsAdjacentWindow() {
        var ram = new InMemoryDataStore();
        var json = new JsonStore(JsonMapper.builder().build(), directory.resolve("charging.json").toString());
        var coordinator = new StateCoordinator(ram, json);
        var sensors = new StationSensors(clock);
        var frame = new StationSensors.Frame(0, Map.of("p", new StationSensors.Port(ChargingPortStatus.AVAILABLE, 0.0)));
        sensors.tick(List.of(new StationSensors.Station("h", List.of(frame))), now);
        var device = new SimulatorAdapter(clock, Duration.ZERO, Duration.ofSeconds(5),
                SimulatorAdapter.CommandMode.NORMAL, sensors, ram, coordinator);
        Instant boundary = now.plusSeconds(60);
        assertTrue(coordinator.commit(coordinator.currentVersion(), store -> store.put(new ChargingAllocation(
                "a", "request", "p", now, boundary, 2.0, ChargingAllocationStatus.RESERVED))));
        var journal = new JournaledIoTAdapter(device, ram, coordinator, clock);
        var gateway = new ChargingIoTGateway(journal, device, sensors);
        assertEquals(CommandStatus.FAILED, gateway.reservePort(
                "h", "p", "r1", boundary.minusNanos(1), boundary.plusSeconds(60), "c1").status());
        assertEquals(CommandStatus.ACKNOWLEDGED, gateway.reservePort(
                "h", "p", "r2", boundary, boundary.plusSeconds(60), "c2").status());
    }
}
