package vn.edu.bku.smart_emobility_backend.iot;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import vn.edu.bku.smart_emobility_backend.common.domain.CommandStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DelayedAcknowledgementTest {
    private final Clock clock = mock(Clock.class);
    private final Instant start = Instant.parse("2026-01-01T12:00:00Z");

    private SimulatorAdapter adapter(long delaySeconds) {
        when(clock.instant()).thenReturn(start);
        var adapter = new SimulatorAdapter(clock, Duration.ofSeconds(delaySeconds), Duration.ofSeconds(5));
        adapter.emitTelemetry(new VehicleReading("v1", 50.0, null, null, null,
                VehicleReading.LockStatus.LOCKED, start, "p1"));
        return adapter;
    }

    @Test
    void timeoutCanBeReconciledWithLateAckAndRetryDoesNotRepeatActuation() {
        var adapter = adapter(10);
        var unlock = new DeviceCommand("unlock", DeviceCommand.Type.UNLOCK, "v1", null);
        assertEquals(CommandStatus.PENDING, adapter.send(unlock).status());
        when(clock.instant()).thenReturn(start.plusSeconds(5).minusNanos(1));
        assertEquals(CommandStatus.PENDING, adapter.getResult("unlock").orElseThrow().status());
        when(clock.instant()).thenReturn(start.plusSeconds(5));
        assertEquals(CommandStatus.AWAITING_RECONCILIATION, adapter.send(unlock).status());
        adapter.send(new DeviceCommand("lock", DeviceCommand.Type.LOCK, "v1", "p1"));
        when(clock.instant()).thenReturn(start.plusSeconds(10));
        var result = adapter.getResult("unlock").orElseThrow();
        assertEquals(CommandStatus.ACKNOWLEDGED, result.status());
        assertEquals(start, result.observedAt());
        assertEquals("unlock", result.commandId());
        assertEquals(result, adapter.send(unlock));
        assertEquals(VehicleReading.LockStatus.LOCKED, adapter.readVehicleStatus("v1").orElseThrow().lockStatus());
    }

    @Test
    void ackAtDeadlineTakesPrecedenceOverTimeout() {
        var adapter = adapter(5);
        var command = new DeviceCommand("id", DeviceCommand.Type.UNLOCK, "v1", null);
        assertEquals(CommandStatus.PENDING, adapter.send(command).status());
        when(clock.instant()).thenReturn(start.plusSeconds(5));
        assertEquals(CommandStatus.ACKNOWLEDGED, adapter.getResult("id").orElseThrow().status());
    }

    @Test
    void delayedFailureStaysFailureAndUnknownIdStaysEmpty() {
        var adapter = adapter(1);
        adapter.send(new DeviceCommand("id", DeviceCommand.Type.LOCK, "v1", "wrong"));
        when(clock.instant()).thenReturn(start.plusSeconds(1));
        assertEquals(CommandStatus.FAILED, adapter.getResult("id").orElseThrow().status());
        assertTrue(adapter.getResult("unknown").isEmpty());
    }

    @Test
    void rejectsInvalidTimingConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> new SimulatorAdapter(clock, Duration.ofNanos(-1), Duration.ofSeconds(5)));
        assertThrows(IllegalArgumentException.class,
                () -> new SimulatorAdapter(clock, Duration.ZERO, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new SimulatorAdapter(clock, Duration.ZERO, Duration.ofNanos(-1)));
    }
}
