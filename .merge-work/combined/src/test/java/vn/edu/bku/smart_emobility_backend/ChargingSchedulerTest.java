package vn.edu.bku.smart_emobility_backend;

import java.nio.file.Path;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.bku.smart_emobility_backend.common.domain.*;
import vn.edu.bku.smart_emobility_backend.common.exception.SchedulingException;
import vn.edu.bku.smart_emobility_backend.common.store.*;
import vn.edu.bku.smart_emobility_backend.iot.*;
import vn.edu.bku.smart_emobility_backend.service.ChargingScheduler;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChargingSchedulerTest {
    private final Instant now = Instant.parse("2026-01-01T00:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    @TempDir Path directory;
    private InMemoryDataStore data;
    private JsonStore json;
    private StateCoordinator coordinator;
    private IoTGateway telemetry;
    private ChargingScheduler scheduler;

    @BeforeEach
    void setup() {
        data = new InMemoryDataStore();
        json = spy(new JsonStore(JsonMapper.builder().build(), directory.resolve("state.json").toString()));
        coordinator = new StateCoordinator(data, json);
        telemetry = mock(IoTGateway.class);
        scheduler = new ChargingScheduler(data, coordinator, telemetry, clock);
        change(() -> {
            data.put(new Account("op", "Operator", null, "op@example.com", "hash", AccountRole.OPERATOR,
                    AccountStatus.ACTIVE, now, now));
            data.put(new MobilityHub("h", "Hub", 10, 106, 10, 4, HubStatus.ACTIVE));
            data.put(new ChargingPort("p1", "h", "Port", 2, ChargingPortStatus.AVAILABLE));
            data.put(new ChargingPort("p2", "h", "Port", 2, ChargingPortStatus.AVAILABLE));
        });
        candidate("v1", 19.9, now.plusSeconds(86400));
    }

    private void change(Runnable change) {
        assertTrue(coordinator.commit(coordinator.currentVersion(), ignored -> change.run()));
    }

    private void candidate(String id, double battery, Instant usage) {
        change(() -> {
            data.put(new Vehicle(id, "h", (int) battery, VehicleStatus.LOW_BATTERY, 10.0, 2.0));
            data.put(new ChargingRequest("r-" + id, "student", id, "h", 80, now,
                    ChargingRequestStatus.PENDING, now, now.plusSeconds(86400), usage));
        });
        when(telemetry.getVehicleTelemetry(id)).thenReturn(new VehicleTelemetry(id, battery,
                VehicleStatus.LOW_BATTERY, 10.0, 106.0, VehicleReading.LockStatus.LOCKED, now, DataQuality.FRESH));
    }

    @Test
    void belowTwentyHasPriorityBeforeEarlierUsageAtTwenty() {
        candidate("v2", 20.0, now.plusSeconds(20000));
        var proposal = scheduler.buildProposal("h", "op");
        assertEquals("v1", proposal.slots().getFirst().vehicleId());
        assertTrue(proposal.valid());
        assertEquals(2, proposal.slots().size());
    }

    @Test
    void hubPowerBoundaryMovesExcessDemandAfterFirstSlot() {
        candidate("v2", 20, now.plusSeconds(86400));
        change(() -> data.put(new MobilityHub("h", "Hub", 10, 106, 10, 2, HubStatus.ACTIVE)));
        var proposal = scheduler.buildProposal("h", "op");
        assertEquals(2, proposal.slots().size());
        assertEquals(proposal.slots().getFirst().endAt(), proposal.slots().get(1).startAt());
        assertTrue(proposal.valid());
    }

    @Test
    void editConflictsRemainDraftAndBlockConfirmation() {
        candidate("v2", 20, now.plusSeconds(86400));
        change(() -> data.put(new MobilityHub("h", "Hub", 10, 106, 10, 2, HubStatus.ACTIVE)));
        var proposal = scheduler.buildProposal("h", "op");
        var first = proposal.slots().getFirst();
        var second = proposal.slots().get(1);
        var edited = scheduler.editSlot(proposal.id(), second.id(), 0, first.startAt(), first.endAt(), "op");
        assertFalse(edited.valid());
        assertTrue(data.findAll(ChargingAllocation.class).isEmpty());
        assertThrows(SchedulingException.class, () -> scheduler.confirmSchedule(proposal.id(), 1, "op"));
        assertThrows(SchedulingException.class, () -> scheduler.editSlot(proposal.id(), first.id(), 0,
                first.startAt(), first.endAt(), "op"));
    }

    @Test
    void confirmationIsAtomicPersistedAndIdempotent() {
        var proposal = scheduler.buildProposal("h", "op");
        var schedule = scheduler.confirmSchedule(proposal.id(), 0, "op");
        assertEquals(schedule, scheduler.confirmSchedule(proposal.id(), 0, "op"));
        assertEquals(1, data.findAll(ChargingAllocation.class).size());
        assertEquals(ChargingRequestStatus.SCHEDULED, data.findById(ChargingRequest.class, "r-v1").orElseThrow().status());
        var restored = json.load().orElseThrow();
        assertEquals(schedule, restored.chargingSchedules().getFirst());
        assertEquals(schedule.id(), restored.scheduleProposals().getFirst().confirmedScheduleId());
        assertEquals(schedule.id(), restored.chargingAllocations().getFirst().scheduleId());
    }

    @Test
    void changedCapacityAndTelemetryAreRevalidatedBeforeConfirm() {
        var proposal = scheduler.buildProposal("h", "op");
        change(() -> data.put(new MobilityHub("h", "Hub", 10, 106, 10, 1.9, HubStatus.ACTIVE)));
        assertThrows(SchedulingException.class, () -> scheduler.confirmSchedule(proposal.id(), 0, "op"));
        change(() -> data.put(new MobilityHub("h", "Hub", 10, 106, 10, 4, HubStatus.ACTIVE)));
        when(telemetry.getVehicleTelemetry("v1")).thenReturn(new VehicleTelemetry(
                "v1", null, null, null, null, null, null, DataQuality.MISSING));
        assertThrows(SchedulingException.class, () -> scheduler.confirmSchedule(proposal.id(), 0, "op"));
        assertTrue(data.findAll(ChargingAllocation.class).isEmpty());
    }

    @Test
    void legacyMissingDataProducesQueueInsteadOfEstimatedDefaults() {
        change(() -> data.put(new Vehicle("v1", "h", 19, VehicleStatus.LOW_BATTERY)));
        var proposal = scheduler.buildProposal("h", "op");
        assertTrue(proposal.slots().isEmpty());
        assertFalse(proposal.valid());
        assertEquals("MISSING_OR_INVALID_CHARGING_DATA", proposal.queue().getFirst().reason());
    }

    @Test
    void persistenceFailureRollsBackScheduleAllocationsAndRequest() {
        var proposal = scheduler.buildProposal("h", "op");
        var before = data.snapshot();
        doThrow(new IllegalStateException("disk failure")).when(json).save(any());
        assertThrows(IllegalStateException.class, () -> scheduler.confirmSchedule(proposal.id(), 0, "op"));
        assertEquals(before, data.snapshot());
    }

    @Test
    void staleVersionDoesNotPersistPartOfAConfirmation() {
        var proposal = scheduler.buildProposal("h", "op");
        StateCoordinator stale = mock(StateCoordinator.class);
        when(stale.commit(anyLong(), any())).thenReturn(false);
        var service = new ChargingScheduler(data, stale, telemetry, clock);
        var error = assertThrows(SchedulingException.class, () -> service.confirmSchedule(proposal.id(), 0, "op"));
        assertEquals("OPTIMISTIC_LOCK_CONFLICT", error.code());
        assertTrue(data.findAll(ChargingSchedule.class).isEmpty());
    }

    @Test
    void rejectsInvalidEditWindowAndNonOperator() {
        var proposal = scheduler.buildProposal("h", "op");
        var slot = proposal.slots().getFirst();
        assertThrows(SchedulingException.class, () -> scheduler.editSlot(proposal.id(), slot.id(), 0,
                slot.endAt(), slot.startAt(), "op"));
        assertThrows(SchedulingException.class, () -> scheduler.editSlot(proposal.id(), slot.id(), 0,
                slot.startAt().plusNanos(1), slot.endAt(), "op"));
        assertThrows(SchedulingException.class, () -> scheduler.buildProposal("h", "student"));
    }

    @Test
    void oldJsonAndStatusAliasesRemainReadable() {
        var mapper = JsonMapper.builder().build();
        var snapshot = mapper.readValue("""
                {"version":0}
                """, StoreSnapshot.class);
        assertTrue(snapshot.chargingSchedules().isEmpty());
        assertTrue(snapshot.scheduleProposals().isEmpty());
        assertEquals(ChargingPortStatus.SCHEDULED, mapper.convertValue("RESERVED", ChargingPortStatus.class));
        assertEquals(ChargingPortStatus.MAINTENANCE, mapper.convertValue("OUT_OF_SERVICE", ChargingPortStatus.class));
        var old = new Vehicle("legacy", "h", 10, VehicleStatus.LOW_BATTERY);
        assertNull(old.batteryCapacityKWh());
    }

    @Test
    void existingReservationPreventsVehicleAndPortDoubleBooking() {
        change(() -> data.put(new ChargingAllocation("existing", "r-v1", "p1",
                now, now.plusSeconds(3600), 2, ChargingAllocationStatus.RESERVED)));
        var proposal = scheduler.buildProposal("h", "op");
        assertFalse(proposal.slots().getFirst().startAt().isBefore(now.plusSeconds(3600)));
    }

    @Test
    void insufficientWindowLeavesCandidateQueued() {
        candidate("v1", 19.9, now.plusSeconds(60));
        var proposal = scheduler.buildProposal("h", "op");
        assertTrue(proposal.slots().isEmpty());
        assertEquals("NO_CAPACITY", proposal.queue().getFirst().reason());
        assertThrows(SchedulingException.class, () -> scheduler.confirmSchedule(proposal.id(), 0, "op"));
    }

    @Test
    void existingDeviceHoldBlocksConfirmationEvenWhenThereIsNoAllocation() {
        var proposal = scheduler.buildProposal("h", "op");
        var slot = proposal.slots().getFirst();
        var command = new vn.edu.bku.smart_emobility_backend.iot.DeviceCommand("hold",
                vn.edu.bku.smart_emobility_backend.iot.DeviceCommand.Type.RESERVE_PORT,
                null, null, "h", slot.portId(), "other-request", slot.startAt(), slot.endAt());
        var response = new vn.edu.bku.smart_emobility_backend.iot.DeviceResponse(
                command, CommandStatus.ACKNOWLEDGED, now, null, null);
        change(() -> data.put(new vn.edu.bku.smart_emobility_backend.iot.DeviceReceipt(
                response, now, now, Duration.ofSeconds(5), false, data.version() + 1)));
        var conflict = assertThrows(SchedulingException.class,
                () -> scheduler.confirmSchedule(proposal.id(), 0, "op"));
        assertEquals("SCHEDULE_CONFLICT", conflict.code());
        assertTrue(data.findAll(ChargingAllocation.class).isEmpty());

        var rebuilt = scheduler.buildProposal("h", "op");
        assertNotEquals(slot.portId(), rebuilt.slots().getFirst().portId());
    }
}
