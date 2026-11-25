package vn.edu.bku.smart_emobility_backend.iot;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import vn.edu.bku.smart_emobility_backend.common.domain.CommandStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CommandFailureTest {
    private final Clock clock = mock(Clock.class);
    private final Instant start = Instant.parse("2026-01-01T12:00:00Z");

    private SimulatorAdapter adapter(SimulatorAdapter.CommandMode mode) {
        when(clock.instant()).thenReturn(start);
        var adapter = new SimulatorAdapter(clock, Duration.ZERO, Duration.ofSeconds(5), mode);
        adapter.emitTelemetry(new VehicleReading("v", 50.0, null, null, null,
                VehicleReading.LockStatus.LOCKED, start, "p"));
        return adapter;
    }

    @Test
    void rejectionPreservesLockAndCanBeQueriedAgain() {
        var adapter = adapter(SimulatorAdapter.CommandMode.REJECT);
        var command = new DeviceCommand("id", DeviceCommand.Type.UNLOCK, "v", null);
        var result = adapter.send(command);
        assertEquals(CommandStatus.FAILED, result.status());
        assertEquals(VehicleReading.LockStatus.LOCKED, adapter.readVehicleStatus("v").orElseThrow().lockStatus());
        when(clock.instant()).thenReturn(start.plusSeconds(20));
        assertEquals(result, adapter.send(command));
        assertEquals(result, adapter.getResult("id").orElseThrow());
    }

    @Test
    void lostAckStaysUncertainAfterTimeoutDespitePhysicalExecution() {
        var adapter = adapter(SimulatorAdapter.CommandMode.DROP_ACK);
        var command = new DeviceCommand("id", DeviceCommand.Type.UNLOCK, "v", null);
        assertEquals(CommandStatus.PENDING, adapter.send(command).status());
        assertEquals(VehicleReading.LockStatus.UNLOCKED, adapter.readVehicleStatus("v").orElseThrow().lockStatus());
        when(clock.instant()).thenReturn(start.plusSeconds(5).minusNanos(1));
        assertEquals(CommandStatus.PENDING, adapter.getResult("id").orElseThrow().status());
        when(clock.instant()).thenReturn(start.plusSeconds(5));
        assertEquals(CommandStatus.AWAITING_RECONCILIATION, adapter.getResult("id").orElseThrow().status());
        when(clock.instant()).thenReturn(start.plusSeconds(60));
        var response = adapter.send(command);
        assertEquals(CommandStatus.AWAITING_RECONCILIATION, response.status());
        assertEquals(command, response.command());
        assertNull(response.batteryLevel());
    }
}
