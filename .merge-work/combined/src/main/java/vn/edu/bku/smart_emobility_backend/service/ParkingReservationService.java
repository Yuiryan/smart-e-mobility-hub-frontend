package vn.edu.bku.smart_emobility_backend.service;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.edu.bku.smart_emobility_backend.common.domain.*;

@Service
public record ParkingReservationService(StudentState state, Clock clock,
        @Value("${app.reservation.hold-timeout}") Duration holdDuration) {
    public record ParkingRequest(String hubId, VehicleType vehicleType, Instant startsAt, Instant endsAt, String spaceId) {}

    public List<ParkingSpace> findAvailableSpaces(ParkingRequest request) {
        validate(request);
        return state.store().findAll(ParkingSpace.class).stream().filter(space -> available(space, request))
                .sorted(Comparator.comparing(ParkingSpace::id)).toList();
    }
    public ParkingReservation reserveSelectedSpace(Student student, String hubId, String spaceId, Instant start, Instant end) {
        ParkingSpace selected = state.get(ParkingSpace.class,spaceId);
        VehicleType type = state.store().findAll(Ride.class).stream()
                .filter(r -> student.accountId().equals(r.accountId()) && r.status() == RideStatus.ACTIVE)
                .map(r -> state.get(Vehicle.class,r.vehicleId()).vehicleType()).findFirst().orElse(selected.vehicleType());
        return reserveSpace(student,new ParkingRequest(hubId,type,start,end,spaceId));
    }
    public ParkingReservation reserveSpace(Student student, ParkingRequest request) {
        long version = state.store().version();
        validate(request);
        ParkingSpace space = state.get(ParkingSpace.class, request.spaceId());
        if (!available(space, request)) throw StudentState.conflict("Parking space unavailable");
        Instant now = clock.instant();
        ParkingReservation reservation = new ParkingReservation(UUID.randomUUID().toString(), student.accountId(),
                null, space.id(), request.startsAt(), request.endsAt(), now, request.startsAt().plus(holdDuration),
                ParkingReservationStatus.ACTIVE, student.id());
        if (state.store().findAll(ParkingReservation.class).stream().anyMatch(r -> student.accountId().equals(r.accountId())
                && live(r,now) && StudentState.overlaps(request.startsAt(),request.endsAt(),r.startsAt(),r.endsAt())))
            throw StudentState.conflict("Student already holds parking during this window");
        state.change(version, store -> { store.put(reservation); refreshSpace(space.id(),now); });
        return reservation;
    }
    private void validate(ParkingRequest request) {
        StudentState.window(request.startsAt(), request.endsAt());
        if (!request.endsAt().isAfter(clock.instant())
                || !request.startsAt().plus(holdDuration).isAfter(clock.instant())) throw StudentState.bad("Invalid parking window or vehicle type");
        state.get(MobilityHub.class, request.hubId());
    }
    private boolean available(ParkingSpace space, ParkingRequest request) {
        if (!space.hubId().equals(request.hubId()) || space.vehicleType() != request.vehicleType()
                || (space.status() != ParkingSpaceStatus.AVAILABLE && space.status() != ParkingSpaceStatus.RESERVED) || space.occupiedVehicleId() != null) return false;
        if (state.store().findAll(StudentOperation.class).stream().anyMatch(op -> op.status() == StudentOperation.Status.PENDING
                && space.id().equals(op.spaceId()))) return false;
        return state.store().findAll(ParkingReservation.class).stream().noneMatch(r -> live(r, clock.instant())
                && space.id().equals(r.parkingSpaceId())
                && StudentState.overlaps(request.startsAt(), request.endsAt(), r.startsAt(), r.endsAt()));
    }
    static boolean live(ParkingReservation r, Instant now) {
        return r.status() == ParkingReservationStatus.ACTIVE && now.isBefore(r.holdExpiresAt()) && now.isBefore(r.endsAt());
    }
    public void cancelReservation(Student student, String id) {
        long version = state.store().version();
        ParkingReservation r = state.get(ParkingReservation.class, id);
        if (!r.accountId().equals(student.accountId())) throw StudentAccess.forbidden();
        if (!live(r, clock.instant())) throw StudentState.conflict("Parking hold is no longer active");
        if (pendingReturn(id)) throw StudentState.conflict("Return awaiting device confirmation");
        state.change(version, store -> { store.put(withStatus(r, ParkingReservationStatus.CANCELLED)); refreshSpace(r.parkingSpaceId(),clock.instant()); });
    }
    public void expireReservation(String id, Instant now) {
        long version = state.store().version();
        ParkingReservation r = state.get(ParkingReservation.class, id);
        if (r.status() == ParkingReservationStatus.ACTIVE && !live(r, now) && !pendingReturn(id))
            state.change(version, store -> { store.put(withStatus(r, ParkingReservationStatus.EXPIRED)); refreshSpace(r.parkingSpaceId(),now); });
    }
    private boolean pendingReturn(String id) {
        return state.store().findAll(StudentOperation.class).stream().anyMatch(op -> op.kind() == StudentOperation.Kind.RETURN
                && op.status() == StudentOperation.Status.PENDING && id.equals(op.reservationId()));
    }
    private void refreshSpace(String spaceId, Instant now) {
        ParkingSpace space = state.get(ParkingSpace.class,spaceId);
        if (space.status() != ParkingSpaceStatus.AVAILABLE && space.status() != ParkingSpaceStatus.RESERVED) return;
        String next = state.store().findAll(ParkingReservation.class).stream()
                .filter(r -> spaceId.equals(r.parkingSpaceId()) && live(r,now))
                .min(Comparator.comparing(ParkingReservation::startsAt)).map(ParkingReservation::id).orElse(null);
        state.store().put(space.parkingHold(next));
    }
    static ParkingReservation withStatus(ParkingReservation r, ParkingReservationStatus status) {
        return new ParkingReservation(r.id(), r.accountId(), r.vehicleId(), r.parkingSpaceId(), r.startsAt(),
                r.endsAt(), r.createdAt(), r.holdExpiresAt(), status,r.studentId());
    }
}
