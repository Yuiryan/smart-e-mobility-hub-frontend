package vn.edu.bku.smart_emobility_backend.service;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import vn.edu.bku.smart_emobility_backend.common.domain.*;

@Service
public record ChargingRequestService(StudentState state, AllocationService allocator, Clock clock, Optional<StudentChargingPort> devices,
        @org.springframework.beans.factory.annotation.Value("${app.search.freshness-limit}") Duration freshness) {
    public record Booking(String vehicleId, String hubId, Instant requestedStartAt, Double targetBatteryPct) {}
    public record Receipt(String chargingRequestId, String hubId, String portId, ChargingRequestStatus status,
            double targetBatteryPct, Instant scheduledStartAt, Instant scheduledEndAt, int estimatedDurationMinutes) {}

    private Vehicle owned(Student student, String id) {
        Vehicle v = state.get(Vehicle.class, id);
        if (v.ownership() != VehicleOwnership.PRIVATE || !student.accountId().equals(v.ownerAccountId())) throw StudentAccess.forbidden();
        if (v.telemetryAt() == null || v.telemetryAt().isAfter(clock.instant())
                || Duration.between(v.telemetryAt(),clock.instant()).compareTo(freshness) > 0)
            throw StudentState.invalid("Fresh vehicle battery reading required");
        return v;
    }
    public int estimateChargingTime(Student student, String vehicleId, String portId, Double target) {
        return minutes(owned(student,vehicleId), state.get(ChargingPort.class,portId), target);
    }
    private static int minutes(Vehicle v, ChargingPort port, Double target) {
        double power = Math.min(port.powerKw(),v.maxChargingPowerKw());
        if (target == null || !Double.isFinite(target) || target > 100 || target <= 0) throw StudentState.bad("Invalid battery target");
        if (v.batteryPct() == null || !Double.isFinite(v.batteryPct()) || v.batteryPct() < 0 || v.batteryPct() >= target
                || !Double.isFinite(power) || power <= 0 || !Double.isFinite(v.batteryCapacityKwh()) || v.batteryCapacityKwh() <= 0)
            throw StudentState.invalid("Insufficient battery or power data for estimate");
        return (int)Math.ceil(60 * v.batteryCapacityKwh() * (target - v.batteryPct()) / (100 * power));
    }
    public Receipt bookCharging(Student student, Booking b) {
        long version = state.store().version();
        Instant start = b.requestedStartAt();
        if (start == null) throw StudentState.bad("requestedStartAt is required");
        if (b.targetBatteryPct() == null || !Double.isFinite(b.targetBatteryPct()) || b.targetBatteryPct() <= 0 || b.targetBatteryPct() > 100)
            throw StudentState.bad("Invalid battery target");
        ChargingRequest pending = state.store().findAll(ChargingRequest.class).stream().filter(r ->
                r.status() == ChargingRequestStatus.PENDING && student.accountId().equals(r.accountId())
                && Objects.equals(r.vehicleId(),b.vehicleId()) && Objects.equals(r.hubId(),b.hubId())
                && start.equals(r.startAt()) && r.targetBatteryPct() == b.targetBatteryPct()).findFirst().orElse(null);
        if (pending != null) return confirm(pending);
        Vehicle vehicle = owned(student,b.vehicleId());
        MobilityHub hub = state.get(MobilityHub.class,b.hubId());
        if (start.isBefore(clock.instant())) throw StudentState.bad("Charging start is in the past");
        Set<String> onlinePorts = onlinePorts(hub);
        List<ChargingPort> ports = state.store().findAll(ChargingPort.class);
        List<ChargingAllocation> allocations = state.store().findAll(ChargingAllocation.class);
        for (ChargingPort port : ports.stream().sorted(Comparator.comparing(ChargingPort::id)).toList()) {
            if (!hub.id().equals(port.hubId()) || !onlinePorts.contains(port.id()) || port.status() != ChargingPortStatus.AVAILABLE
                    || !Double.isFinite(port.powerKw()) || port.powerKw() <= 0) continue;
            int duration = minutes(vehicle,port,b.targetBatteryPct());
            Instant end = start.plus(Duration.ofMinutes(duration));
            double power = Math.min(vehicle.maxChargingPowerKw(),port.powerKw());
            if (alreadyScheduled(vehicle,start,end) || !parkingAvailable(student,vehicle,hub,start,end)
                    || !allocator.fits(allocations,ports,port,hub,start,end,power)) continue;
            String id = UUID.randomUUID().toString();
            ChargingRequest request = new ChargingRequest(id,student.accountId(),vehicle.id(),hub.id(),b.targetBatteryPct(),
                    clock.instant(),ChargingRequestStatus.PENDING,port.id(),start,end,duration);
            ChargingAllocation allocation = new ChargingAllocation(UUID.randomUUID().toString(),id,port.id(),start,end,
                    power,ChargingAllocationStatus.RESERVED,vehicle.id(),null);
            state.change(version,store -> { store.put(request); store.put(allocation); });
            return confirm(request);
        }
        throw StudentState.invalid("No compatible parking, charging port or hub power for this interval");
    }

    private Set<String> onlinePorts(MobilityHub hub) {
        StudentChargingPort.HubStatus status = adapter().readHubStatus(hub.id());
        if (status == null || !hub.id().equals(status.hubId()) || status.availablePortIds() == null
                || status.observedAt() == null || status.observedAt().isAfter(clock.instant())
                || Duration.between(status.observedAt(),clock.instant()).compareTo(freshness) > 0)
            throw unavailable("Current hub status unavailable");
        return status.availablePortIds();
    }
    private boolean alreadyScheduled(Vehicle vehicle,Instant start,Instant end) {
        return state.store().findAll(ChargingRequest.class).stream().anyMatch(r -> vehicle.id().equals(r.vehicleId())
                && r.status() != ChargingRequestStatus.CANCELLED && r.status() != ChargingRequestStatus.COMPLETED
                && (r.startAt() == null || r.endAt() == null || StudentState.overlaps(start,end,r.startAt(),r.endAt())));
    }
    private boolean parkingAvailable(Student student,Vehicle vehicle,MobilityHub hub,Instant start,Instant end) {
        for (ParkingSpace space : state.store().findAll(ParkingSpace.class)) {
            if (!hub.id().equals(space.hubId()) || space.vehicleType() != vehicle.vehicleType()) continue;
            if (space.status() == ParkingSpaceStatus.OCCUPIED && vehicle.id().equals(space.occupiedVehicleId())) return true;
            if (space.occupiedVehicleId() != null || space.status() != ParkingSpaceStatus.AVAILABLE && space.status() != ParkingSpaceStatus.RESERVED) continue;
            boolean held = state.store().findAll(ParkingReservation.class).stream().anyMatch(r -> space.id().equals(r.parkingSpaceId())
                    && !student.accountId().equals(r.accountId()) && ParkingReservationService.live(r,clock.instant())
                    && StudentState.overlaps(start,end,r.startsAt(),r.endsAt()));
            if (!held) return true;
        }
        return false;
    }
    private org.springframework.web.server.ResponseStatusException unavailable(String reason) {
        return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,reason);
    }
    private StudentChargingPort adapter() { return devices.orElseThrow(() -> unavailable("Charging adapter unavailable")); }

    private Receipt confirm(ChargingRequest request) {
        StudentChargingPort.Result reply = adapter().reservePort(request.id(),request.portId(),request.startAt(),request.endAt());
        if (reply == null || reply == StudentChargingPort.Result.UNKNOWN) throw unavailable("Charging reservation pending, retry the same booking");
        long version = state.store().version();
        ChargingRequest current = state.get(ChargingRequest.class,request.id());
        if (current.status() == ChargingRequestStatus.PENDING) {
            if (reply == StudentChargingPort.Result.REJECTED) {
                cancelAllocation(current,version);
                throw StudentState.invalid("Charging device refused reservation");
            }
            state.change(version,store -> store.put(current.withStatus(ChargingRequestStatus.SCHEDULED)));
        } else if (current.status() != ChargingRequestStatus.SCHEDULED) throw StudentState.conflict("Charging request changed");
        return new Receipt(request.id(),request.hubId(),request.portId(),ChargingRequestStatus.SCHEDULED,request.targetBatteryPct(),
                request.startAt(),request.endAt(),request.estimatedMinutes());
    }
    public void cancelRequest(Student student, String id) {
        long version = state.store().version();
        ChargingRequest request = state.get(ChargingRequest.class,id);
        if (!student.accountId().equals(request.accountId())) throw StudentAccess.forbidden();
        if (request.status() == ChargingRequestStatus.CANCELLED) return;
        if (request.status() == ChargingRequestStatus.SCHEDULED) {
            adapter();
            state.change(version,store -> store.put(request.withStatus(ChargingRequestStatus.CANCEL_PENDING)));
        } else if (request.status() != ChargingRequestStatus.CANCEL_PENDING) throw StudentState.conflict("Request cannot be cancelled");
        StudentChargingPort.Result reply = adapter().releasePort(id,request.portId());
        if (reply == null || reply == StudentChargingPort.Result.UNKNOWN) throw unavailable("Charging cancellation pending, retry cancellation");
        version = state.store().version();
        ChargingRequest current = state.get(ChargingRequest.class,id);
        if (current.status() == ChargingRequestStatus.CANCELLED) return;
        if (current.status() != ChargingRequestStatus.CANCEL_PENDING) throw StudentState.conflict("Charging request changed");
        if (reply == StudentChargingPort.Result.REJECTED) {
            state.change(version,store -> store.put(current.withStatus(ChargingRequestStatus.SCHEDULED)));
            throw StudentState.invalid("Charging device refused release");
        }
        cancelAllocation(current,version);
    }
    private void cancelAllocation(ChargingRequest request,long version) {
        String id = request.id();
        ChargingAllocation a = state.store().findAll(ChargingAllocation.class).stream()
                .filter(x -> id.equals(x.chargingRequestId()) && x.status() == ChargingAllocationStatus.RESERVED)
                .findFirst().orElseThrow(() -> StudentState.conflict("Allocation is not held by request"));
        state.change(version, store -> {
            store.put(request.withStatus(ChargingRequestStatus.CANCELLED));
            store.put(a.cancel());
        });
    }
}
