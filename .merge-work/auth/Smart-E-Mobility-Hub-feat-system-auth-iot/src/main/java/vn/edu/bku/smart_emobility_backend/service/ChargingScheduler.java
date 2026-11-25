package vn.edu.bku.smart_emobility_backend.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import vn.edu.bku.smart_emobility_backend.common.domain.*;
import vn.edu.bku.smart_emobility_backend.common.exception.SchedulingException;
import vn.edu.bku.smart_emobility_backend.common.store.*;
import vn.edu.bku.smart_emobility_backend.iot.DeviceReceipt;
import vn.edu.bku.smart_emobility_backend.iot.IoTGateway;

@Service
public class ChargingScheduler {
    private static final Duration PLANNING_LEAD_TIME = Duration.ofMinutes(1);
    private static final double LOW_BATTERY_PCT = MobilityRules.MIN_RENTAL_BATTERY_PCT;
    private static final double PERCENT = 100.0;
    private static final double SECONDS_PER_HOUR = 3600.0;
    private static final double MINUTES_PER_HOUR = 60.0;
    private static final double POWER_TOLERANCE = 1e-9;
    private final InMemoryDataStore store;
    private final StateCoordinator coordinator;
    private final IoTGateway telemetry;
    private final Clock clock;

    public ChargingScheduler(InMemoryDataStore store, StateCoordinator coordinator, IoTGateway telemetry, Clock clock) {
        this.store = store;
        this.coordinator = coordinator;
        this.telemetry = telemetry;
        this.clock = clock;
    }

    private record Candidate(ChargingRequest request, Vehicle vehicle, double battery, double energyKWh) {}
    private record Busy(String hubId, ChargingSlot slot) {}
    private record Inputs(MobilityHub hub, List<ChargingPort> ports, List<Candidate> candidates,
            List<Busy> busy, List<ScheduleProposal.QueueEntry> queue, List<String> violations) {}

    public ScheduleProposal buildProposal(String hubId, String operatorId) {
        StoreSnapshot snapshot = store.snapshot();
        requireOperator(snapshot, operatorId);
        Instant now = clock.instant();
        Inputs input = inputs(snapshot, hubId, now);
        List<ChargingSlot> slots = new ArrayList<>();
        List<ScheduleProposal.QueueEntry> queue = new ArrayList<>(input.queue());
        for (Candidate candidate : input.candidates()) {
            ChargingSlot best = null;
            for (ChargingPort port : input.ports()) {
                ChargingSlot slot = earliestSlot(input, candidate, port, slots, now);
                if (slot != null && (best == null || slot.startAt().isBefore(best.startAt()))) {
                    best = slot;
                }
            }
            if (best == null) {
                queue.add(new ScheduleProposal.QueueEntry(candidate.request().id(), candidate.vehicle().id(), "NO_CAPACITY"));
            } else {
                slots.add(best);
            }
        }
        ScheduleProposal proposal = new ScheduleProposal(UUID.randomUUID().toString(), hubId, now, 0,
                slots, queue, input.violations(), null);
        List<String> violations = validateProposal(input, proposal, now);
        proposal = new ScheduleProposal(proposal.id(), hubId, now, 0, slots, queue, violations, null);
        ScheduleProposal saved = proposal;
        commit(snapshot.version(), data -> data.put(saved));
        return saved;
    }

    public ScheduleProposal findProposal(String id, String operatorId) {
        StoreSnapshot snapshot = store.snapshot();
        requireOperator(snapshot, operatorId);
        return proposal(snapshot, id);
    }

    public ScheduleProposal editSlot(String id, String slotId, long revision, Instant start, Instant end, String operatorId) {
        if (start == null || end == null || !start.isBefore(end) || !minuteAligned(start) || !minuteAligned(end)) {
            throw failure(400, "VALIDATION_ERROR", "Slot requires minute-aligned startAt before endAt");
        }
        StoreSnapshot snapshot = store.snapshot();
        requireOperator(snapshot, operatorId);
        ScheduleProposal previous = proposal(snapshot, id);
        requireRevision(previous, revision);
        if (previous.confirmedScheduleId() != null) {
            throw failure(409, "SCHEDULE_ALREADY_CONFIRMED", id);
        }
        if (previous.slots().stream().noneMatch(slot -> slot.id().equals(slotId))) {
            throw failure(404, "RESOURCE_NOT_FOUND", slotId);
        }
        List<ChargingSlot> slots = previous.slots().stream().map(slot -> slot.id().equals(slotId)
                ? new ChargingSlot(slot.id(), slot.requestId(), slot.vehicleId(), slot.portId(), start, end, slot.powerKw())
                : slot).toList();
        Instant now = clock.instant();
        Inputs input = inputs(snapshot, previous.hubId(), now);
        ScheduleProposal edited = new ScheduleProposal(id, previous.hubId(), previous.createdAt(), revision + 1,
                slots, previous.queue(), List.of(), null);
        edited = new ScheduleProposal(id, edited.hubId(), edited.createdAt(), edited.revision(),
                slots, edited.queue(), validateProposal(input, edited, now), null);
        ScheduleProposal saved = edited;
        commit(snapshot.version(), data -> data.put(saved));
        return saved;
    }

    public ChargingSchedule confirmSchedule(String id, long revision, String operatorId) {
        StoreSnapshot snapshot = store.snapshot();
        requireOperator(snapshot, operatorId);
        ScheduleProposal proposal = proposal(snapshot, id);
        if (proposal.confirmedScheduleId() != null) {
            return snapshot.chargingSchedules().stream().filter(s -> s.id().equals(proposal.confirmedScheduleId()))
                    .findFirst().orElseThrow(() -> failure(409, "SCHEDULE_STATE_INVALID", id));
        }
        requireRevision(proposal, revision);
        Instant now = clock.instant();
        Inputs input = inputs(snapshot, proposal.hubId(), now);
        List<String> violations = validateProposal(input, proposal, now);
        if (proposal.slots().isEmpty()) {
            violations.add("EMPTY_SCHEDULE");
        }
        if (!violations.isEmpty()) {
            throw new SchedulingException(409, "SCHEDULE_CONFLICT", violations);
        }
        String scheduleId = UUID.randomUUID().toString();
        List<ChargingAllocation> allocations = proposal.slots().stream().map(slot ->
                new ChargingAllocation(UUID.randomUUID().toString(), slot.requestId(), slot.portId(),
                        slot.startAt(), slot.endAt(), slot.powerKw(), ChargingAllocationStatus.RESERVED,
                        slot.vehicleId(), scheduleId)).toList();
        ChargingSchedule schedule = new ChargingSchedule(scheduleId, id, proposal.hubId(), operatorId, now,
                allocations.stream().map(ChargingAllocation::id).toList());
        ScheduleProposal confirmed = new ScheduleProposal(id, proposal.hubId(), proposal.createdAt(),
                revision + 1, proposal.slots(), proposal.queue(), List.of(), scheduleId);
        Map<String, ChargingRequest> requests = index(snapshot.chargingRequests(), ChargingRequest::id);
        commit(snapshot.version(), data -> {
            allocations.forEach(data::put);
            for (ChargingSlot slot : proposal.slots()) {
                ChargingRequest request = requests.get(slot.requestId());
                data.put(new ChargingRequest(request.id(), request.accountId(), request.vehicleId(), request.hubId(),
                        request.targetBatteryPct(), request.requestedAt(), ChargingRequestStatus.SCHEDULED,
                        request.startAt(), request.endAt(), request.nextUsageAt()));
            }
            data.put(schedule);
            data.put(confirmed);
        });
        return schedule;
    }

    private Inputs inputs(StoreSnapshot snapshot, String hubId, Instant now) {
        MobilityHub hub = snapshot.hubs().stream().filter(h -> h.id().equals(hubId)).findFirst()
                .orElseThrow(() -> failure(404, "RESOURCE_NOT_FOUND", hubId));
        List<String> violations = new ArrayList<>();
        if (hub.status() != HubStatus.ACTIVE || !positive(hub.chargingCapacityKw())) {
            violations.add("HUB_UNAVAILABLE");
        }
        List<ChargingPort> ports = snapshot.chargingPorts().stream().filter(port -> hubId.equals(port.hubId())
                && port.status() == ChargingPortStatus.AVAILABLE && positive(port.powerKw()))
                .sorted(Comparator.comparing(ChargingPort::id)).toList();
        Map<String, Vehicle> vehicles = index(snapshot.vehicles(), Vehicle::id);
        Map<String, ChargingPort> allPorts = index(snapshot.chargingPorts(), ChargingPort::id);
        Map<String, ChargingRequest> requests = index(snapshot.chargingRequests(), ChargingRequest::id);
        List<Busy> busy = new ArrayList<>();
        for (ChargingAllocation allocation : snapshot.chargingAllocations()) {
            if (allocation.status() != ChargingAllocationStatus.ACTIVE && allocation.status() != ChargingAllocationStatus.RESERVED) {
                continue;
            }
            ChargingPort port = allPorts.get(allocation.chargingPortId());
            ChargingRequest request = requests.get(allocation.chargingRequestId());
            String vehicleId = allocation.vehicleId() != null ? allocation.vehicleId() : request == null ? null : request.vehicleId();
            if (port == null || allocation.startsAt() == null || allocation.endsAt() == null
                    || !allocation.startsAt().isBefore(allocation.endsAt()) || !positive(allocation.allocatedPowerKw()) || vehicleId == null) {
                violations.add("EXISTING_ALLOCATION_INCOMPLETE:" + allocation.id());
                continue;
            }
            if (allocation.endsAt().isAfter(now)) {
                busy.add(new Busy(port.hubId(), new ChargingSlot(allocation.id(), allocation.chargingRequestId(),
                        vehicleId, port.id(), allocation.startsAt(), allocation.endsAt(), allocation.allocatedPowerKw())));
            }
        }
        for (var hold : DeviceReceipt.activePortReservations(snapshot.deviceReceipts(), now)) {
            if (!hubId.equals(hold.hubId())) {
                continue;
            }
            ChargingPort port = allPorts.get(hold.portId());
            if (port == null || !hubId.equals(port.hubId()) || !positive(port.powerKw())) {
                violations.add("PORT_HOLD_INCOMPLETE:" + hold.commandId());
                continue;
            }
            boolean allocated = busy.stream().map(Busy::slot).anyMatch(slot ->
                    hold.reservationId().equals(slot.requestId()) && hold.portId().equals(slot.portId())
                            && hold.startAt().equals(slot.startAt()) && hold.endAt().equals(slot.endAt()));
            if (!allocated) {
                ChargingRequest request = requests.get(hold.reservationId());
                busy.add(new Busy(hubId, new ChargingSlot(hold.commandId(), hold.reservationId(),
                        request == null ? null : request.vehicleId(), hold.portId(),
                        hold.startAt(), hold.endAt(), port.powerKw())));
            }
        }
        List<Candidate> candidates = new ArrayList<>();
        List<ScheduleProposal.QueueEntry> queue = new ArrayList<>();
        for (ChargingRequest request : snapshot.chargingRequests()) {
            if (!hubId.equals(request.hubId()) || request.status() != ChargingRequestStatus.PENDING) {
                continue;
            }
            Vehicle vehicle = vehicles.get(request.vehicleId());
            String reason = null;
            if (vehicle == null || !hubId.equals(vehicle.hubId())
                    || (vehicle.status() != VehicleStatus.AVAILABLE && vehicle.status() != VehicleStatus.LOW_BATTERY)) {
                reason = "VEHICLE_UNAVAILABLE";
            } else if (!positive(vehicle.batteryCapacityKWh()) || !positive(vehicle.maxChargingPowerKw())
                    || request.startAt() == null || request.endAt() == null || request.nextUsageAt() == null
                    || !request.startAt().isBefore(request.endAt()) || request.targetBatteryPct() <= 0 || request.targetBatteryPct() > PERCENT) {
                reason = "MISSING_OR_INVALID_CHARGING_DATA";
            }
            if (reason != null) {
                queue.add(new ScheduleProposal.QueueEntry(request.id(), request.vehicleId(), reason));
                violations.add(reason + ":" + request.id());
                continue;
            }
            var reading = telemetry.getVehicleTelemetry(vehicle.id());
            if (reading.quality() != DataQuality.FRESH || reading.batteryPct() == null) {
                queue.add(new ScheduleProposal.QueueEntry(request.id(), vehicle.id(), "TELEMETRY_UNAVAILABLE"));
                violations.add("TELEMETRY_UNAVAILABLE:" + request.id());
            } else if (request.targetBatteryPct() <= reading.batteryPct()) {
                queue.add(new ScheduleProposal.QueueEntry(request.id(), vehicle.id(), "TARGET_ALREADY_REACHED"));
            } else {
                double energy = vehicle.batteryCapacityKWh() * (request.targetBatteryPct() - reading.batteryPct()) / PERCENT;
                candidates.add(new Candidate(request, vehicle, reading.batteryPct(), energy));
            }
        }
        for (Vehicle vehicle : snapshot.vehicles()) {
            if (hubId.equals(vehicle.hubId()) && vehicle.status() == VehicleStatus.LOW_BATTERY
                    && snapshot.chargingRequests().stream().noneMatch(request -> vehicle.id().equals(request.vehicleId())
                            && (request.status() == ChargingRequestStatus.PENDING || request.status() == ChargingRequestStatus.SCHEDULED
                                    || request.status() == ChargingRequestStatus.CHARGING))) {
                queue.add(new ScheduleProposal.QueueEntry(null, vehicle.id(), "MISSING_CHARGING_REQUEST"));
                violations.add("MISSING_CHARGING_REQUEST:" + vehicle.id());
            }
        }
        candidates.sort(Comparator.comparingInt((Candidate c) -> c.battery() < LOW_BATTERY_PCT ? 0 : 1)
                .thenComparing(c -> c.request().nextUsageAt()).thenComparingDouble(Candidate::battery)
                .thenComparing(c -> c.request().id()));
        return new Inputs(hub, ports, candidates, busy, queue, violations);
    }

    private ChargingSlot earliestSlot(Inputs input, Candidate candidate, ChargingPort port, List<ChargingSlot> planned, Instant now) {
        double power = Math.min(input.hub().chargingCapacityKw(), Math.min(port.powerKw(), candidate.vehicle().maxChargingPowerKw()));
        if (!positive(power)) {
            return null;
        }
        double minutes = Math.ceil(candidate.energyKWh() / power * MINUTES_PER_HOUR);
        if (!Double.isFinite(minutes) || minutes < 1 || minutes > Long.MAX_VALUE / 60) {
            return null;
        }
        Instant earliestAllowed = now.plus(PLANNING_LEAD_TIME);
        Instant earliest = ceilMinute(candidate.request().startAt().isAfter(earliestAllowed)
                ? candidate.request().startAt() : earliestAllowed);
        Instant deadline = candidate.request().endAt().isBefore(candidate.request().nextUsageAt())
                ? candidate.request().endAt() : candidate.request().nextUsageAt();
        TreeSet<Instant> starts = new TreeSet<>();
        starts.add(earliest);
        input.busy().forEach(busy -> starts.add(ceilMinute(busy.slot().endAt())));
        planned.forEach(slot -> starts.add(ceilMinute(slot.endAt())));
        List<Busy> occupied = new ArrayList<>(input.busy());
        planned.forEach(slot -> occupied.add(new Busy(input.hub().id(), slot)));
        for (Instant start : starts) {
            if (start.isBefore(earliest) || !start.isBefore(deadline)
                    || Duration.between(start, deadline).toSeconds() / 60 < minutes) {
                continue;
            }
            Instant end = start.plusSeconds((long) minutes * 60);
            ChargingSlot slot = new ChargingSlot(UUID.randomUUID().toString(), candidate.request().id(),
                    candidate.vehicle().id(), port.id(), start, end, power);
            if (fits(input.hub(), slot, occupied)) {
                return slot;
            }
        }
        return null;
    }

    private List<String> validateProposal(Inputs input, ScheduleProposal proposal, Instant now) {
        List<String> violations = new ArrayList<>(input.violations());
        Map<String, Candidate> candidates = index(input.candidates(), c -> c.request().id());
        Map<String, ChargingPort> ports = index(input.ports(), ChargingPort::id);
        List<Busy> occupied = new ArrayList<>(input.busy());
        for (ChargingSlot slot : proposal.slots()) {
            Candidate candidate = candidates.get(slot.requestId());
            ChargingPort port = ports.get(slot.portId());
            if (candidate == null || port == null || !candidate.vehicle().id().equals(slot.vehicleId())) {
                violations.add("INPUT_CHANGED:" + slot.id());
                continue;
            }
            Instant deadline = candidate.request().endAt().isBefore(candidate.request().nextUsageAt())
                    ? candidate.request().endAt() : candidate.request().nextUsageAt();
            if (slot.startAt().isBefore(now) || slot.startAt().isBefore(candidate.request().startAt())
                    || !slot.startAt().isBefore(slot.endAt()) || slot.endAt().isAfter(deadline)
                    || !minuteAligned(slot.startAt()) || !minuteAligned(slot.endAt())) {
                violations.add("INVALID_WINDOW:" + slot.id());
            }
            double delivered = slot.powerKw() * Duration.between(slot.startAt(), slot.endAt()).toSeconds() / SECONDS_PER_HOUR;
            if (!positive(slot.powerKw()) || slot.powerKw() > port.powerKw() + POWER_TOLERANCE
                    || slot.powerKw() > candidate.vehicle().maxChargingPowerKw() + POWER_TOLERANCE
                    || delivered + POWER_TOLERANCE < candidate.energyKWh()) {
                violations.add("INVALID_POWER_OR_DURATION:" + slot.id());
            }
            if (!fits(input.hub(), slot, occupied)) {
                violations.add("RESOURCE_OR_POWER_CONFLICT:" + slot.id());
            }
            occupied.add(new Busy(input.hub().id(), slot));
        }
        return violations;
    }

    private boolean fits(MobilityHub hub, ChargingSlot slot, List<Busy> occupied) {
        List<Busy> overlapping = occupied.stream().filter(b -> b.slot().startAt().isBefore(slot.endAt())
                && slot.startAt().isBefore(b.slot().endAt())).toList();
        if (overlapping.stream().anyMatch(b -> b.slot().portId().equals(slot.portId())
                || slot.vehicleId().equals(b.slot().vehicleId()))) {
            return false;
        }
        TreeSet<Instant> points = new TreeSet<>();
        points.add(slot.startAt());
        overlapping.forEach(b -> {
            if (b.slot().startAt().isAfter(slot.startAt())) {
                points.add(b.slot().startAt());
            }
        });
        for (Instant point : points) {
            double total = slot.powerKw() + overlapping.stream().filter(b -> hub.id().equals(b.hubId())
                    && !b.slot().startAt().isAfter(point) && b.slot().endAt().isAfter(point))
                    .mapToDouble(b -> b.slot().powerKw()).sum();
            if (total > hub.chargingCapacityKw() + POWER_TOLERANCE) {
                return false;
            }
        }
        return true;
    }

    private ScheduleProposal proposal(StoreSnapshot snapshot, String id) {
        return snapshot.scheduleProposals().stream().filter(p -> p.id().equals(id)).findFirst()
                .orElseThrow(() -> failure(404, "RESOURCE_NOT_FOUND", id));
    }

    private void requireRevision(ScheduleProposal proposal, long revision) {
        if (proposal.revision() != revision) {
            throw failure(409, "OPTIMISTIC_LOCK_CONFLICT", "Proposal revision has changed");
        }
    }

    private void requireOperator(StoreSnapshot snapshot, String id) {
        boolean allowed = snapshot.accounts().stream().anyMatch(account -> account.id().equals(id)
                && account.role() == AccountRole.OPERATOR && account.isActive());
        if (!allowed) {
            throw failure(403, "ACCESS_DENIED", "Active Operator account required");
        }
    }

    private void commit(long version, java.util.function.Consumer<InMemoryDataStore> change) {
        if (!coordinator.commit(version, change)) {
            throw failure(409, "OPTIMISTIC_LOCK_CONFLICT", "State changed, retry the request");
        }
    }

    private boolean positive(Double value) {
        return value != null && Double.isFinite(value) && value > 0;
    }

    private boolean minuteAligned(Instant value) {
        return value.equals(value.truncatedTo(ChronoUnit.MINUTES));
    }

    private Instant ceilMinute(Instant value) {
        Instant minute = value.truncatedTo(ChronoUnit.MINUTES);
        return minute.equals(value) ? minute : minute.plus(1, ChronoUnit.MINUTES);
    }

    private <T> Map<String, T> index(List<T> values, Function<T, String> id) {
        return values.stream().collect(Collectors.toMap(id, Function.identity()));
    }

    private SchedulingException failure(int status, String code, String reason) {
        return new SchedulingException(status, code, List.of(reason));
    }
}
