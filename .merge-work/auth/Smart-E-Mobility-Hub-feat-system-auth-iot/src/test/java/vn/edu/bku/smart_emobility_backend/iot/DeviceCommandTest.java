package vn.edu.bku.smart_emobility_backend.iot;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import vn.edu.bku.smart_emobility_backend.common.domain.CommandStatus;
import static org.junit.jupiter.api.Assertions.*;

class DeviceCommandTest {
    private final Instant now = Instant.parse("2026-01-01T12:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final SimulatorAdapter adapter = new SimulatorAdapter(clock);
    private final IoTGateway gateway = new IoTGateway(adapter, clock);

    private VehicleReading sample(Instant observedAt, String spaceId, Double battery) {
        return new VehicleReading("v1", battery, null, null, null,
                VehicleReading.LockStatus.LOCKED, observedAt, spaceId);
    }

    @Test
    void retryAfterOppositeCommandReturnsOriginalAckWithoutMovingLock() {
        var reading = sample(now.minusSeconds(600), "p1", 50.0);
        adapter.emitTelemetry(reading);
        var unlock = gateway.unlock("v1", "c1");
        assertEquals(CommandStatus.ACKNOWLEDGED, unlock.status());
        assertEquals(VehicleReading.LockStatus.UNLOCKED, gateway.getVehicleTelemetry("v1").lockStatus());
        var lock = gateway.lockAtSpace("v1", "p1", "c2");
        assertEquals(CommandStatus.ACKNOWLEDGED, lock.status());
        assertEquals("p1", lock.command().spaceId());
        assertEquals("v1", lock.command().vehicleId());
        assertEquals("c2", lock.commandId());
        assertEquals(50.0, lock.batteryLevel());
        assertEquals(unlock, gateway.unlock("v1", "c1"));
        assertEquals(lock, gateway.getResult("c2").orElseThrow());
        assertEquals(VehicleReading.LockStatus.LOCKED, gateway.getVehicleTelemetry("v1").lockStatus());
        assertEquals(reading.observedAt(), gateway.getVehicleTelemetry("v1").observedAt());
    }

    @Test
    void staleMissingAndUnlocatedDeviceCannotConfirmReturn() {
        assertTrue(gateway.getResult("unknown").isEmpty());
        assertEquals(CommandStatus.AWAITING_RECONCILIATION, gateway.unlock("missing", "a").status());
        adapter.emitTelemetry(sample(now.minusSeconds(600).minusNanos(1), "p1", 50.0));
        assertEquals(CommandStatus.AWAITING_RECONCILIATION, gateway.lockAtSpace("v1", "p1", "b").status());
        adapter.emitTelemetry(sample(now.minusSeconds(1), null, 50.0));
        assertEquals(CommandStatus.AWAITING_RECONCILIATION, gateway.lockAtSpace("v1", "p1", "c").status());
        adapter.emitTelemetry(sample(now, "p2", null));
        assertEquals(CommandStatus.FAILED, gateway.lockAtSpace("v1", "p1", "d").status());
        var ack = gateway.lockAtSpace("v1", "p2", "e");
        assertEquals(CommandStatus.ACKNOWLEDGED, ack.status());
        assertNull(ack.batteryLevel());
    }

    @Test
    void rejectsReusedIdWithDifferentTargetOrAction() {
        gateway.unlock("v1", "id");
        assertThrows(IllegalArgumentException.class, () -> gateway.unlock("v2", "id"));
        assertThrows(IllegalArgumentException.class, () -> gateway.lockAtSpace("v1", "p1", "id"));
        assertThrows(IllegalArgumentException.class, () -> gateway.lockAtSpace("v1", " ", "new"));
        assertThrows(IllegalArgumentException.class, () -> gateway.unlock("v1", ""));
    }

    @Test
    void ticksPreserveActuatorStateAndMissingSensorIsStillMissing() {
        adapter.emitTelemetry(sample(now.minusSeconds(2), "p1", 40.0));
        gateway.unlock("v1", "unlock");
        adapter.emitTelemetry(sample(now.minusSeconds(1), "p1", 38.0));
        assertEquals(VehicleReading.LockStatus.UNLOCKED, gateway.getVehicleTelemetry("v1").lockStatus());
        adapter.emitTelemetry(new VehicleReading("v1", 38.0, null, null, null, null, now, "p1"));
        assertNull(gateway.getVehicleTelemetry("v1").lockStatus());
        assertEquals(CommandStatus.AWAITING_RECONCILIATION, gateway.lockAtSpace("v1", "p1", "lock").status());
    }
}
