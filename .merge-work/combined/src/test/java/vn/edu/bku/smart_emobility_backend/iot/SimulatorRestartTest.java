package vn.edu.bku.smart_emobility_backend.iot;

import java.nio.file.Path;
import java.time.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.bku.smart_emobility_backend.common.domain.*;
import vn.edu.bku.smart_emobility_backend.common.store.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "app.data-file=./target/test-data/iot-wiring-${random.uuid}.json",
        "app.iot.simulator.tick-interval=3s",
        "app.iot.simulator.stations[0].hub-id=wiring-hub",
        "app.iot.simulator.stations[0].frames[0].occupied-spaces=0",
        "app.iot.simulator.stations[0].frames[0].ports.p.status=AVAILABLE",
        "app.iot.simulator.stations[0].frames[0].ports.p.power-kw=0"
})
class SimulatorRestartTest {
    @Autowired private JsonMapper mapper;
    @Autowired private IoTAdapter wiredAdapter;
    @Autowired private ChargingIoTGateway wiredGateway;
    @Autowired private IoTSimulator wiredSimulator;
    @Autowired private SimulatorProperties properties;
    @TempDir Path directory;
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);

    private SimulatorAdapter device(InMemoryDataStore store, StateCoordinator coordinator, StationSensors sensors) {
        return new SimulatorAdapter(clock, Duration.ZERO, Duration.ofSeconds(5),
                SimulatorAdapter.CommandMode.DROP_ACK, sensors, store, coordinator);
    }

    @Test
    void springBindsFramesAndUsesJournal() {
        assertInstanceOf(JournaledIoTAdapter.class, wiredAdapter);
        assertEquals("wiring-hub", properties.stations().getFirst().hubId());
        wiredSimulator.tick(Instant.now());
        assertEquals(DataQuality.FRESH, wiredGateway.readHubStatus("wiring-hub").quality());
    }

    @Test
    void lostAckCanBeReconciledAfterRestoreWithLockAndPortState() {
        var json = new JsonStore(mapper, directory.resolve("state.json").toString());
        var ram = new InMemoryDataStore();
        var coordinator = new StateCoordinator(ram, json);
        var sensors = new StationSensors(clock);
        sensors.tick(List.of(new StationSensors.Station("h", List.of(new StationSensors.Frame(0,
                Map.of("p", new StationSensors.Port(ChargingPortStatus.AVAILABLE, 0.0)))))), now);
        var device = device(ram, coordinator, sensors);
        var journal = new JournaledIoTAdapter(device, ram, coordinator, clock);
        device.emitTelemetry(new VehicleReading("v", 50.0, null, null, null,
                VehicleReading.LockStatus.LOCKED, now, null));
        var unlock = new DeviceCommand("unlock", DeviceCommand.Type.UNLOCK, "v", null);
        assertEquals(CommandStatus.PENDING, journal.send(unlock).status());
        journal.send(new DeviceCommand("hold", DeviceCommand.Type.RESERVE_PORT, null, null,
                "h", "p", "r", now, now.plusSeconds(60)));

        var restored = new InMemoryDataStore();
        restored.restore(json.load().orElseThrow());
        var restoredCoordinator = new StateCoordinator(restored, json);
        var restoredDevice = device(restored, restoredCoordinator, new StationSensors(clock));
        var restarted = new JournaledIoTAdapter(restoredDevice, restored, restoredCoordinator, clock);
        restarted.reconcileAfterRestart();
        assertEquals(CommandStatus.ACKNOWLEDGED, restarted.getResult("unlock").orElseThrow().status());
        assertEquals(CommandStatus.ACKNOWLEDGED, restarted.getResult("hold").orElseThrow().status());
        assertEquals(1, restoredDevice.activePortReservations().size());
        restoredDevice.emitTelemetry(new VehicleReading("v", 49.0, null, null, null,
                VehicleReading.LockStatus.LOCKED, now, null));
        assertEquals(VehicleReading.LockStatus.UNLOCKED, restoredDevice.readVehicleStatus("v").orElseThrow().lockStatus());
        assertEquals(now, restoredDevice.readVehicleStatus("v").orElseThrow().observedAt());
        long version = restored.version();
        restarted.send(unlock);
        assertEquals(version, restored.version());
        assertEquals(2, restored.findAll(DeviceReceipt.class).size());
        restarted.send(new DeviceCommand("release", DeviceCommand.Type.RELEASE_PORT, null, null,
                "h", "p", "r", null, null));
        restarted.reconcileResult("release");
        var again = new InMemoryDataStore();
        again.restore(json.load().orElseThrow());
        assertTrue(device(again, new StateCoordinator(again, json), new StationSensors(clock))
                .activePortReservations().isEmpty());
    }

    @Test
    void failedReceiptWritePreservesActuatorAndIntent() {
        var ram = new InMemoryDataStore();
        var json = mock(JsonStore.class);
        var coordinator = new StateCoordinator(ram, json);
        var device = device(ram, coordinator, new StationSensors(clock));
        var journal = new JournaledIoTAdapter(device, ram, coordinator, clock);
        device.emitTelemetry(new VehicleReading("v", 50.0, null, null, null,
                VehicleReading.LockStatus.LOCKED, now, null));
        doAnswer(invocation -> {
            StoreSnapshot snapshot = invocation.getArgument(0);
            if (!snapshot.deviceReceipts().isEmpty()) {
                throw new IllegalStateException("disk failure");
            }
            return null;
        }).when(json).save(any());
        var command = new DeviceCommand("c", DeviceCommand.Type.UNLOCK, "v", null);
        assertThrows(IllegalStateException.class, () -> journal.send(command));
        assertTrue(ram.findAll(DeviceReceipt.class).isEmpty());
        assertEquals(VehicleReading.LockStatus.LOCKED, device.readVehicleStatus("v").orElseThrow().lockStatus());
        assertEquals(CommandStatus.AWAITING_RECONCILIATION, journal.send(command).status());
    }

    @Test
    void oldSnapshotAndVehicleCommandRemainReadable() {
        assertTrue(mapper.readValue("""
                {"version":1}
                """, StoreSnapshot.class).deviceReceipts().isEmpty());
        var command = mapper.readValue("""
                {"commandId":"c","type":"UNLOCK","vehicleId":"v","spaceId":null}
                """, DeviceCommand.class);
        assertEquals(new DeviceCommand("c", DeviceCommand.Type.UNLOCK, "v", null), command);
    }
    @Test
    void receiptSurvivesFailureToSaveBackendAck() {
        var ram = new InMemoryDataStore();
        var json = mock(JsonStore.class);
        var coordinator = new StateCoordinator(ram, json);
        var device = new SimulatorAdapter(clock, Duration.ZERO, Duration.ofSeconds(5),
                SimulatorAdapter.CommandMode.NORMAL, new StationSensors(clock), ram, coordinator);
        var journal = new JournaledIoTAdapter(device, ram, coordinator, clock);
        device.emitTelemetry(new VehicleReading("v", 50.0, null, null, null,
                VehicleReading.LockStatus.UNLOCKED, now, null));
        doAnswer(invocation -> {
            StoreSnapshot snapshot = invocation.getArgument(0);
            if (snapshot.deviceOperations().stream().anyMatch(op -> op.response().status() == CommandStatus.ACKNOWLEDGED)) {
                throw new IllegalStateException("failure saving backend acknowledgement");
            }
            return null;
        }).when(json).save(any());
        var command = new DeviceCommand("force-lock", DeviceCommand.Type.FORCE_LOCK, "v", null);
        assertThrows(IllegalStateException.class, () -> journal.send(command));
        assertEquals(1, ram.findAll(DeviceReceipt.class).size());
        assertEquals(CommandStatus.PENDING, ram.findAll(DeviceOperation.class).getFirst().response().status());
        assertEquals(VehicleReading.LockStatus.LOCKED, device.readVehicleStatus("v").orElseThrow().lockStatus());

        reset(json);
        assertEquals(CommandStatus.ACKNOWLEDGED, journal.reconcileResult("force-lock").orElseThrow().status());
        assertEquals(1, ram.findAll(DeviceReceipt.class).size());
    }

}
