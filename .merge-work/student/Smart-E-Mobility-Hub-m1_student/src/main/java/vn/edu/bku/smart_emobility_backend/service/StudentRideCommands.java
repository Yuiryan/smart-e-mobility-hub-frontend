package vn.edu.bku.smart_emobility_backend.service;

import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.bku.smart_emobility_backend.common.domain.*;
import vn.edu.bku.smart_emobility_backend.common.domain.StudentOperation.*;


@Service
public record StudentRideCommands(StudentState state, Clock clock, Optional<StudentDevicePort> device,
        @Value("${app.search.min-battery-pct}") double minBattery) {
    public static String fingerprint(Object... fields) {
        StringBuilder canonical = new StringBuilder();
        for (Object field : fields) {
            String text = Objects.toString(field, "");
            canonical.append(field == null ? -1 : text.length()).append(':').append(text);
        }
        return ReservationService.sha256(canonical.toString());
    }

    StudentOperation execute(Student student, UUID key, Kind kind, String hash, Supplier<StudentOperation> prepare) {
        String id = key.toString();
        StudentOperation prior;
        StudentOperation op;
        StudentDevicePort adapter;
        // Only the local claim is serialized. The physical device call runs after persistence and outside the lock.
        synchronized (state.store()) {
            prior = state.store().findById(StudentOperation.class, id).orElse(null);
            if (prior == null) {
                op = prepare.get();
                adapter = device.orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Device adapter unavailable"));
                boolean busy = state.store().findAll(StudentOperation.class).stream().anyMatch(other ->
                        other.status() == Status.PENDING && (other.studentId().equals(op.studentId())
                                || other.vehicleId().equals(op.vehicleId()) || other.spaceId().equals(op.spaceId())));
                if (busy) throw StudentState.conflict("Another physical operation is pending");
                state.change(state.store().version(), s -> s.put(op));
            } else {
                if (!student.id().equals(prior.studentId())) throw StudentAccess.forbidden();
                if (kind != prior.kind() || !hash.equals(prior.payloadHash())) throw StudentState.conflict("Key already used for a different request");
                op = prior;
                adapter = null;
            }
        }
        if (prior != null) return poll(student,prior);
        try {
            return apply(student, op, adapter.send(id, kind, op.vehicleId(), op.spaceId()));
        } catch (StudentDevicePort.Unavailable timeout) {
            return op;
        }
    }

    public StudentOperation lookup(Student student, String id) {
        StudentOperation op = state.get(StudentOperation.class,id);
        if (!student.id().equals(op.studentId())) throw StudentAccess.forbidden();
        return poll(student,op);
    }
    private StudentOperation poll(Student student, StudentOperation op) {
        if (op.status() != Status.PENDING || device.isEmpty()) return op;
        try {
            return apply(student, op, device.get().getResult(op.id()));
        } catch (StudentDevicePort.Unavailable timeout) {
            return op;
        }
    }

    private StudentOperation apply(Student student, StudentOperation op, StudentDevicePort.Reply reply) {
        if (reply == null || !op.id().equals(reply.commandId()) || !op.vehicleId().equals(reply.vehicleId())
                || !op.spaceId().equals(reply.spaceId()) || reply.outcome() == null || reply.outcome() == StudentDevicePort.Outcome.UNKNOWN)
            return op;
        Double battery = reply.batteryPct();
        boolean success = reply.outcome() == StudentDevicePort.Outcome.ACK_SUCCESS;
        if (success && (op.kind() == Kind.RETURN || battery != null) &&
                (battery == null || !Double.isFinite(battery) || battery < 0 || battery > 100)) return op;
        synchronized (state.store()) {
            StudentOperation current = state.get(StudentOperation.class,op.id());
            if (current.status() != Status.PENDING) return current;
            Instant now = clock.instant();
            state.change(state.store().version(), store -> {
                if (!success) store.put(op.finish(Status.FAILED,null,null,now));
                else if (op.kind() == Kind.PICKUP) finalizePickup(student,op,battery,now);
                else completeReturn(op,battery,now);
            });
            return state.get(StudentOperation.class,op.id());
        }
    }

    private void finalizePickup(Student student, StudentOperation op, Double battery, Instant now) {
        Vehicle vehicle = state.get(Vehicle.class,op.vehicleId());
        ParkingSpace space = state.get(ParkingSpace.class,op.spaceId());
        if (!space.id().equals(vehicle.parkingSpaceId()) || !space.hubId().equals(vehicle.hubId())
                || space.status() != ParkingSpaceStatus.OCCUPIED || space.occupiedVehicleId() != null && !vehicle.id().equals(space.occupiedVehicleId())
                || vehicle.status() != (op.reservationId() == null ? VehicleStatus.AVAILABLE : VehicleStatus.RESERVED))
            throw StudentState.conflict("Dock changed while awaiting unlock confirmation");
        Double initial = battery == null ? vehicle.batteryPct() : battery;
        Ride ride = new Ride(op.rideId(),student.accountId(),vehicle.id(),now,null,initial,null,
                RideStatus.ACTIVE,student.id(),op.reservationId(),op.id(),space.hubId(),null);
        if (op.reservationId() != null) {
            Reservation r = state.get(Reservation.class,op.reservationId());
            if (r.status() != ReservationStatus.ACTIVE || !r.isOwnedBy(student) || !vehicle.id().equals(r.vehicleId()))
                throw StudentState.conflict("Reservation changed while awaiting unlock confirmation");
            state.store().put(r.withStatus(ReservationStatus.PICKED_UP));
        }
        state.store().put(ride);
        state.store().put(space.withOccupant(null));
        state.store().put(relocate(vehicle,null,null,VehicleStatus.IN_USE,initial,battery == null ? vehicle.telemetryAt() : now));
        state.store().put(op.finish(Status.SUCCEEDED,initial,VehicleStatus.IN_USE,now));
    }
    private void completeReturn(StudentOperation op, double battery, Instant now) {
        Vehicle vehicle = state.get(Vehicle.class,op.vehicleId());
        ParkingSpace space = state.get(ParkingSpace.class,op.spaceId());
        Ride ride = state.get(Ride.class,op.rideId());
        if (ride.status() != RideStatus.ACTIVE || !ride.vehicleId().equals(vehicle.id()) || space.occupiedVehicleId() != null
                || space.status() != ParkingSpaceStatus.AVAILABLE && space.status() != ParkingSpaceStatus.RESERVED)
            throw StudentState.conflict("Ride or space changed while awaiting lock confirmation");
        boolean incidentOpen = state.store().findAll(Incident.class).stream().anyMatch(i -> vehicle.id().equals(i.vehicleId())
                && (i.status() == IncidentStatus.OPEN || i.status() == IncidentStatus.INVESTIGATING));
        VehicleStatus status = op.issueReported() || incidentOpen || vehicle.status() == VehicleStatus.MAINTENANCE
                ? VehicleStatus.MAINTENANCE : battery < minBattery ? VehicleStatus.LOW_BATTERY : VehicleStatus.AVAILABLE;
        if (op.reservationId() != null) {
            ParkingReservation r = state.get(ParkingReservation.class,op.reservationId());
            if (r.status() != ParkingReservationStatus.ACTIVE) throw StudentState.conflict("Parking reservation changed");
            state.store().put(ParkingReservationService.withStatus(r,ParkingReservationStatus.COMPLETED));
        }
        state.store().put(ride.complete(now,battery,space.hubId()));
        state.store().put(space.withOccupant(vehicle.id()));
        state.store().put(relocate(vehicle,space.hubId(),space.id(),status,battery,now));
        state.store().put(op.finish(Status.SUCCEEDED,battery,status,now));
    }

    static Vehicle relocate(Vehicle v,String hub,String space,VehicleStatus status,Double battery,Instant observedAt) {
        return new Vehicle(v.id(),hub,space,v.vehicleType(),battery,v.batteryCapacityKwh(),v.maxChargingPowerKw(),
                status,observedAt,v.version()+1,v.ownership(),v.ownerAccountId());
    }
}
