package vn.edu.bku.smart_emobility_backend.service;

import java.time.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import vn.edu.bku.smart_emobility_backend.common.domain.*;
import vn.edu.bku.smart_emobility_backend.common.domain.StudentOperation.*;

@Service
public record VehicleReturnService(StudentRideCommands commands, StudentState state, Clock clock) {
    public record ReturnRequest(String hubId,String spaceId,Boolean issueReported) {}
    public StudentOperation returnVehicle(Student student, UUID key, String rideId, ReturnRequest request) {
        String hash = StudentRideCommands.fingerprint(rideId,request.hubId(),request.spaceId(),request.issueReported());
        return commands.execute(student,key,Kind.RETURN,hash,() -> {
            if (request.issueReported() == null) throw StudentState.bad("issueReported is required");
            Ride ride = state.get(Ride.class,rideId);
            if (!student.accountId().equals(ride.accountId())) throw StudentAccess.forbidden();
            if (ride.status() != RideStatus.ACTIVE) throw StudentState.conflict("Ride is not active");
            Vehicle vehicle = state.get(Vehicle.class,ride.vehicleId());
            ParkingSpace space = state.get(ParkingSpace.class, request.spaceId());
            if (!space.hubId().equals(request.hubId()) || space.vehicleType() != vehicle.vehicleType()
                    || space.occupiedVehicleId() != null || (space.status() != ParkingSpaceStatus.AVAILABLE && space.status() != ParkingSpaceStatus.RESERVED)) throw StudentState.conflict("Incompatible or occupied space");
            Instant now = clock.instant();
            ParkingReservation hold = state.store().findAll(ParkingReservation.class).stream()
                    .filter(r -> space.id().equals(r.parkingSpaceId()) && ParkingReservationService.live(r,now) && !now.isBefore(r.startsAt()))
                    .findFirst().orElse(null);
            if (hold != null && !student.accountId().equals(hold.accountId())) throw StudentState.conflict("Space is reserved");
            return new StudentOperation(key.toString(),student.id(),vehicle.id(),space.id(),ride.id(),hold == null ? null : hold.id(),
                    Kind.RETURN,hash,request.issueReported(),now,Status.PENDING,null,null,null);
        });
    }
}
