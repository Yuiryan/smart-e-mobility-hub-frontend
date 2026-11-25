package vn.edu.bku.smart_emobility_backend.iot;

import java.time.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.edu.bku.smart_emobility_backend.common.domain.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChargingIoTGatewayTest {
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final StationSensors sensors = new StationSensors(clock);
    private final SimulatorAdapter device = new SimulatorAdapter(clock, Duration.ZERO, Duration.ofSeconds(5),
            SimulatorAdapter.CommandMode.NORMAL, sensors, null, null);
    private final ChargingIoTGateway gateway = new ChargingIoTGateway(device, device, sensors);

    private void frame(ChargingPortStatus status, Instant observedAt) {
        sensors.tick(List.of(new StationSensors.Station("h", List.of(
                new StationSensors.Frame(0, Map.of("p", new StationSensors.Port(status, 0.0)))))), observedAt);
    }

    private DeviceResponse reserve(String reservation, long start, long end, String id) {
        return gateway.reservePort("h", "p", reservation, now.plusSeconds(start), now.plusSeconds(end), id);
    }

    @Test
    void acceptsAdjacentIntervalsAndReleasesOnlyTheSpecifiedReservation() {
        frame(ChargingPortStatus.AVAILABLE, now);
        assertEquals(CommandStatus.ACKNOWLEDGED, reserve("r1", 0, 60, "c1").status());
        assertEquals(CommandStatus.FAILED, reserve("r2", 59, 120, "c2").status());
        assertEquals(CommandStatus.ACKNOWLEDGED, reserve("r3", 60, 120, "c3").status());
        assertEquals(2, gateway.activeReservations("h").size());
        assertEquals(CommandStatus.FAILED, gateway.releasePort("h", "wrong", "r1", "c4").status());
        assertEquals(CommandStatus.ACKNOWLEDGED, gateway.releasePort("h", "p", "r1", "c5").status());
        assertEquals("r3", gateway.activeReservations("h").getFirst().reservationId());
        assertEquals(CommandStatus.ACKNOWLEDGED, gateway.releasePort("h", "p", "r1", "c6").status());
        assertEquals(CommandStatus.ACKNOWLEDGED, reserve("r4", 0, 60, "c7").status());
        assertEquals(CommandStatus.FAILED, reserve("r1", 0, 60, "c8").status());
    }

    @Test
    void retryIsStableAndIdentityCannotBeReusedWithAnotherPayload() {
        frame(ChargingPortStatus.AVAILABLE, now);
        var first = reserve("r", 10, 60, "id");
        assertEquals(first, reserve("r", 10, 60, "id"));
        assertThrows(IllegalArgumentException.class, () -> reserve("r", 11, 60, "id"));
        assertEquals(CommandStatus.FAILED, reserve("r", 11, 60, "other").status());
        assertEquals(1, gateway.activeReservations("h").size());
    }

    @Test
    void checksSensorFreshnessAndAvailability() {
        assertEquals(CommandStatus.AWAITING_RECONCILIATION, reserve("r1", 0, 60, "a").status());
        frame(ChargingPortStatus.AVAILABLE, now.minusSeconds(600).minusNanos(1));
        assertEquals(CommandStatus.AWAITING_RECONCILIATION, reserve("r2", 0, 60, "b").status());
        frame(ChargingPortStatus.OUT_OF_SERVICE, now);
        assertEquals(CommandStatus.FAILED, reserve("r3", 0, 60, "c").status());
        frame(ChargingPortStatus.AVAILABLE, now.minusSeconds(600));
        assertEquals(DataQuality.FRESH, gateway.readHubStatus("h").quality());
        assertEquals(0.0, gateway.readPort("h", "p").value().powerKw());
        assertEquals(CommandStatus.ACKNOWLEDGED, reserve("r4", 0, 60, "d").status());
    }

    @Test
    void rejectsInvalidIntervalsAndUnknownRelease() {
        frame(ChargingPortStatus.AVAILABLE, now);
        assertThrows(IllegalArgumentException.class, () -> reserve("r", 0, 0, "a"));
        assertThrows(IllegalArgumentException.class, () -> reserve("r", 1, 0, "b"));
        assertEquals(CommandStatus.FAILED, reserve("r", -1, 60, "c").status());
        assertThrows(IllegalArgumentException.class, () -> gateway.releasePort("h", "p", "", "d"));
        assertEquals(CommandStatus.FAILED, gateway.releasePort("h", "p", "unknown", "e").status());
    }

    @Test
    void rejectionAndLostAckApplyToPortCommands() {
        frame(ChargingPortStatus.AVAILABLE, now);
        var command = new DeviceCommand("c", DeviceCommand.Type.RESERVE_PORT, null, null,
                "h", "p", "r", now, now.plusSeconds(60));
        var rejected = new SimulatorAdapter(clock, Duration.ZERO, Duration.ofSeconds(5),
                SimulatorAdapter.CommandMode.REJECT, sensors, null, null);
        assertEquals(CommandStatus.FAILED, rejected.send(command).status());
        assertTrue(rejected.activePortReservations().isEmpty());
        Clock time = mock(Clock.class);
        when(time.instant()).thenReturn(now);
        var dropped = new SimulatorAdapter(time, Duration.ZERO, Duration.ofSeconds(5),
                SimulatorAdapter.CommandMode.DROP_ACK, sensors, null, null);
        assertEquals(CommandStatus.PENDING, dropped.send(command).status());
        assertEquals(1, dropped.activePortReservations().size());
        when(time.instant()).thenReturn(now.plusSeconds(5));
        assertEquals(CommandStatus.AWAITING_RECONCILIATION, dropped.getResult("c").orElseThrow().status());
        assertEquals(CommandStatus.ACKNOWLEDGED, dropped.reconcileResult("c").orElseThrow().status());
    }
    @Test
    void simultaneousOverlappingReservationsHaveOnlyOneWinner() throws Exception {
        frame(ChargingPortStatus.AVAILABLE, now);
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                ready.countDown();
                start.await();
                return reserve("r1", 0, 60, "c1").status();
            });
            var second = executor.submit(() -> {
                ready.countDown();
                start.await();
                return reserve("r2", 0, 60, "c2").status();
            });
            assertTrue(ready.await(5, java.util.concurrent.TimeUnit.SECONDS));
            start.countDown();
            var results = List.of(first.get(5, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(5, java.util.concurrent.TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter(status -> status == CommandStatus.ACKNOWLEDGED).count());
            assertEquals(1, gateway.activeReservations("h").size());
        }
    }

    @Test
    void completedWindowNoLongerBlocksThePort() {
        Clock time = mock(Clock.class);
        when(time.instant()).thenReturn(now);
        var moving = new SimulatorAdapter(time, Duration.ZERO, Duration.ofSeconds(5),
                SimulatorAdapter.CommandMode.NORMAL, sensors, null, null);
        var api = new ChargingIoTGateway(moving, moving, sensors);
        frame(ChargingPortStatus.AVAILABLE, now);
        api.reservePort("h", "p", "r", now, now.plusSeconds(60), "c");
        when(time.instant()).thenReturn(now.plusSeconds(60));
        assertTrue(api.activeReservations("h").isEmpty());
    }

}
