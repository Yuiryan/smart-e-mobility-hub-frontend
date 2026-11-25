package vn.edu.bku.smart_emobility_backend.simulation;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.bku.smart_emobility_backend.common.domain.*;
import vn.edu.bku.smart_emobility_backend.common.exception.SimulationException;
import vn.edu.bku.smart_emobility_backend.common.store.*;
import vn.edu.bku.smart_emobility_backend.iot.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SimulationEngineTest {
    private final Instant now = Instant.parse("2026-01-01T12:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final InMemoryDataStore live = new InMemoryDataStore();
    private final IoTGateway telemetry = mock(IoTGateway.class);
    private final SimulationProperties properties = new SimulationProperties(Duration.ofHours(1), Duration.ofMinutes(15), 1.0, 100000);
    @TempDir Path directory;
    private StateCoordinator coordinator;
    private JsonStore json;
    private SimulationEngine engine;

    @BeforeEach
    void seed() {
        json = new JsonStore(JsonMapper.builder().build(), directory.resolve("state.json").toString());
        coordinator = new StateCoordinator(live, json);
        engine = new SimulationEngine(live, telemetry, clock, properties);
        change(() -> live.put(new Account("op", "Operator", null, "op@example.com", "hash",
                AccountRole.OPERATOR, AccountStatus.ACTIVE, now, now)));
        hub("a", 10, 4);
        hub("b", 10, 0);
        for (int i = 0; i < 4; i++) {
            vehicle("v" + i, "a", 100);
            int index = i;
            change(() -> {
                Instant ended = now.minusSeconds(300 + index * 600L);
                live.put(new Ride("history" + index, "student", "historical", ended.minusSeconds(300), ended,
                        100, 99, RideStatus.COMPLETED, "a", "b", 1.0));
            });
        }
    }

    private void change(Runnable change) {
        assertTrue(coordinator.commit(coordinator.currentVersion(), ignored -> change.run()));
    }

    private void hub(String id, int capacity, int occupied) {
        change(() -> {
            live.put(new MobilityHub(id, id, 10, 106 + id.charAt(0) / 1000.0, capacity, 4, HubStatus.ACTIVE));
            for (int i = 0; i < capacity; i++) {
                live.put(new ParkingSpace(id + i, id, i < occupied ? ParkingSpaceStatus.OCCUPIED : ParkingSpaceStatus.AVAILABLE));
            }
        });
    }

    private void vehicle(String id, String hubId, double battery) {
        change(() -> live.put(new Vehicle(id, hubId, (int) battery, VehicleStatus.AVAILABLE)));
        when(telemetry.getVehicleTelemetry(id)).thenReturn(new VehicleTelemetry(id, battery, VehicleStatus.AVAILABLE,
                10.0, 106.0, VehicleReading.LockStatus.LOCKED, now, DataQuality.FRESH));
    }

    private Scenario scenario(double hours, double multiplier, String outage) {
        return new Scenario("Test", new Scenario.Parameters(hours, multiplier, outage));
    }

    @Test
    void simulationLeavesLiveMemoryFileAndDeviceCommandsUntouched() throws Exception {
        StoreSnapshot before = live.snapshot();
        String persisted = Files.readString(directory.resolve("state.json"));
        var result = engine.runSimulation(scenario(1, 1, null), "op");
        assertEquals(0, result.failedBookingsCount());
        assertEquals(0, result.failedReturnsCount());
        assertEquals(before, live.snapshot());
        assertEquals(persisted, Files.readString(directory.resolve("state.json")));
        assertTrue(mockingDetails(telemetry).getInvocations().stream()
                .allMatch(invocation -> invocation.getMethod().getName().equals("getVehicleTelemetry")));
        assertEquals(before.version(), result.sourceVersion());
    }

    @Test
    void outagePreventsDeparturesToThatHub() {
        var result = engine.runSimulation(scenario(1, 1, "b"), "op");
        assertEquals(4, result.failedBookingsCount());
        assertEquals(0, result.failedReturnsCount());
        assertTrue(result.recommendedDispatch().stream().noneMatch(p -> p.fromHub().equals("b") || p.toHub().equals("b")));
    }

    @Test
    void fullDestinationRejectsReturnsWithoutExceedingCapacity() {
        hub("b", 10, 10);
        var result = engine.runSimulation(scenario(1, 1, null), "op");
        assertEquals(4, result.failedReturnsCount());
        assertTrue(result.projectedOverloadHubs().contains("b"));
        assertEquals(100.0, result.congestionMetrics().get("b"));
    }

    @Test
    void zeroMultiplierRetainsInitialPeakAndProducesNoNewBookings() {
        hub("a", 20, 17);
        var result = engine.runSimulation(scenario(1, 0, null), "op");
        assertEquals(0, result.failedBookingsCount());
        assertEquals(0, result.failedReturnsCount());
        assertTrue(result.projectedOverloadHubs().contains("a"));
        assertEquals(85.0, result.congestionMetrics().get("a"));
    }

    @Test
    void missingAndLowBatteryVehiclesDoNotSatisfyDemand() {
        when(telemetry.getVehicleTelemetry("v0")).thenReturn(new VehicleTelemetry(
                "v0", null, null, null, null, null, null, DataQuality.MISSING));
        vehicle("v1", "a", 19.9);
        vehicle("v2", "a", 19.9);
        vehicle("v3", "a", 20.0);
        var result = engine.runSimulation(scenario(1, 1, null), "op");
        assertEquals(3, result.failedBookingsCount());
        assertTrue(result.missingData().contains("TELEMETRY_UNAVAILABLE:v0"));
    }

    @Test
    void missingLegacyHistoryIsReportedInsteadOfAssumingDemand() {
        change(() -> {
            for (Ride ride : live.findAll(Ride.class)) {
                live.remove(Ride.class, ride.id());
            }
            live.put(new Ride("old", "student", "v0", now.minusSeconds(600), now.minusSeconds(300),
                    100, 99, RideStatus.COMPLETED));
        });
        var result = engine.runSimulation(scenario(1, 1, null), "op");
        assertTrue(result.missingData().contains("MISSING_DEMAND_BASELINE"));
        assertTrue(result.missingData().contains("INCOMPLETE_RIDE_HISTORY:old"));
        assertEquals(0, result.failedBookingsCount());
    }

    @Test
    void activeRideCanFailReturnDuringOutageWithZeroRentalMultiplier() {
        change(() -> {
            live.put(new Vehicle("active", null, 50, VehicleStatus.IN_USE));
            live.put(new Ride("current", "student", "active", now.minusSeconds(600), null, 60, null,
                    RideStatus.ACTIVE, "a", "b", 1.0));
        });
        when(telemetry.getVehicleTelemetry("active")).thenReturn(new VehicleTelemetry("active", 50.0,
                VehicleStatus.IN_USE, 10.0, 106.0, VehicleReading.LockStatus.UNLOCKED, now, DataQuality.FRESH));
        assertEquals(1, engine.runSimulation(scenario(1, 0, "b"), "op").failedReturnsCount());
    }

    @Test
    void proposalCountsStayWithinSupplyAndSafeDestinationCapacity() {
        hub("a", 10, 9);
        for (int i = 4; i < 9; i++) {
            vehicle("v" + i, "a", 100);
        }
        var result = engine.runSimulation(scenario(1, 0, null), "op");
        int moved = result.recommendedDispatch().stream().mapToInt(SimulationResult.DispatchProposal::vehicleCount).sum();
        assertEquals(3, moved);
        assertTrue(result.recommendedDispatch().stream().allMatch(p -> p.fromHub().equals("a") && p.toHub().equals("b")));
    }

    @Test
    void sameSnapshotProducesSameMetricsAndCounts() {
        var first = engine.runSimulation(scenario(2, 2.5, null), "op");
        var second = engine.runSimulation(scenario(2, 2.5, null), "op");
        assertEquals(first.failedBookingsCount(), second.failedBookingsCount());
        assertEquals(first.failedReturnsCount(), second.failedReturnsCount());
        assertEquals(first.congestionMetrics(), second.congestionMetrics());
        assertEquals(first.projectedOverloadHubs(), second.projectedOverloadHubs());
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, -1, 168.1, Double.NaN, Double.POSITIVE_INFINITY})
    void invalidDurationIsRejected(double hours) {
        assertEquals(400, assertThrows(SimulationException.class,
                () -> engine.runSimulation(scenario(hours, 1, null), "op")).status());
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 100.1, Double.NaN, Double.POSITIVE_INFINITY})
    void invalidMultiplierIsRejected(double multiplier) {
        assertEquals(400, assertThrows(SimulationException.class,
                () -> engine.runSimulation(scenario(1, multiplier, null), "op")).status());
    }

    @Test
    void invalidOutageRoleAndForecastLimitAreRejected() {
        assertEquals(404, assertThrows(SimulationException.class,
                () -> engine.runSimulation(scenario(1, 1, "unknown"), "op")).status());
        assertEquals(403, assertThrows(SimulationException.class,
                () -> engine.runSimulation(scenario(1, 1, null), "student")).status());
        var bounded = new SimulationEngine(live, telemetry, clock,
                new SimulationProperties(Duration.ofHours(1), Duration.ofMinutes(15), 1.0, 3));
        assertEquals("SIMULATION_TOO_LARGE", assertThrows(SimulationException.class,
                () -> bounded.runSimulation(scenario(1, 1, null), "op")).code());
    }

    @Test
    void snapshotDataStoreIsIndependentAndIncompleteHubsAreReported() {
        SimulationState state = engine.createStateSnapshot();
        state.isolatedStore.remove(MobilityHub.class, "a");
        assertTrue(live.findById(MobilityHub.class, "a").isPresent());
        change(() -> live.remove(ParkingSpace.class, "a0"));
        var result = engine.runSimulation(scenario(1, 0, null), "op");
        assertTrue(result.missingData().contains("INCOMPLETE_HUB:a"));
        assertFalse(result.congestionMetrics().containsKey("a"));
    }
    @Test
    void upperParameterBoundariesAndConfigurationValidation() {
        assertEquals("COMPLETED", engine.runSimulation(scenario(168, 0, null), "op").status());
        assertEquals("COMPLETED", engine.runSimulation(scenario(1, 100, null), "op").status());
        assertThrows(IllegalArgumentException.class, () -> new SimulationProperties(
                Duration.ofHours(1), Duration.ZERO, 1, 100));
        assertThrows(IllegalArgumentException.class, () -> new SimulationProperties(
                Duration.ofHours(1), Duration.ofMinutes(15), Double.NaN, 100));
    }

}
