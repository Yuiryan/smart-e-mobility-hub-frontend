package vn.edu.bku.smart_emobility_backend.service;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.edu.bku.smart_emobility_backend.common.domain.*;
import vn.edu.bku.smart_emobility_backend.common.domain.StudentOperation.*;

@Service
public record VehiclePickupService(StudentRideCommands commands, StudentState state, Clock clock,
        @Value("${app.search.freshness-limit}") Duration freshness, @Value("${app.search.min-battery-pct}") double minBattery) {
    public record PickupRequestDTO(String vehicleId,String reservationId,String pickupCode,Instant startAt,Instant endAt) {}

    public StudentOperation processScannedCode(Student student, UUID key, PickupRequestDTO request) {
        String hash = StudentRideCommands.fingerprint(request.vehicleId(),request.reservationId(),request.pickupCode(),request.startAt(),request.endAt());
        return commands.execute(student,key,Kind.PICKUP,hash,() -> {
            Instant now = clock.instant();
            Vehicle vehicle = state.get(Vehicle.class,request.vehicleId());
            if (student.hasActiveRide(state.store().findAll(Ride.class))) throw new StudentFailure(org.springframework.http.HttpStatus.CONFLICT,"ACTIVE_RIDE_EXISTS","Student already has an active ride");
            if (request.reservationId() == null) {
                if (request.startAt() != null || request.endAt() != null) StudentState.window(request.startAt(),request.endAt());
                if (request.pickupCode() != null || request.endAt() != null && !now.isBefore(request.endAt())) throw StudentState.bad("Invalid direct rental request");
                if (student.hasActiveReservation(state.store().findAll(Reservation.class),now)) throw StudentState.conflict("Student already holds a reservation");
            } else {
                if (request.startAt() != null || request.endAt() != null) throw StudentState.bad("Reserved pickup uses the reservation window");
                Reservation reservation = state.get(Reservation.class,request.reservationId());
                if (!reservation.isOwnedBy(student) || !vehicle.id().equals(reservation.vehicleId())
                        || request.pickupCode() == null || !ReservationService.sha256(request.pickupCode()).equals(reservation.pickupCodeHash()))
                    throw StudentAccess.forbidden();
                if (!reservation.isActiveAt(now) || vehicle.status() != VehicleStatus.RESERVED) throw StudentState.conflict("Reservation no longer active");
            }
            Vehicle candidate = request.reservationId() == null ? vehicle :
                    StudentRideCommands.relocate(vehicle,vehicle.hubId(),vehicle.parkingSpaceId(),VehicleStatus.AVAILABLE,vehicle.batteryPct(),vehicle.telemetryAt());
            if (!candidate.isSearchable(now,freshness,minBattery)) throw new StudentFailure(org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT,"VEHICLE_NOT_RENTABLE","Vehicle is not rentable");
            ParkingSpace space = state.get(ParkingSpace.class,vehicle.parkingSpaceId());
            if (space.vehicleReservationId() != null && !space.vehicleReservationId().equals(request.reservationId())) throw StudentState.conflict("Dock held by another reservation");
            if (!space.hubId().equals(vehicle.hubId()) || space.status() != ParkingSpaceStatus.OCCUPIED
                    || space.occupiedVehicleId() != null && !vehicle.id().equals(space.occupiedVehicleId()))
                throw StudentState.conflict("Vehicle is not docked in its recorded space");
            return new StudentOperation(key.toString(),student.id(),vehicle.id(),space.id(),UUID.randomUUID().toString(),
                    request.reservationId(),Kind.PICKUP,hash,false,now,Status.PENDING,null,null,null);
        });
    }
}
