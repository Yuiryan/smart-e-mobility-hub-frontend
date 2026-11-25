package vn.edu.bku.smart_emobility_backend.controller;

import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import vn.edu.bku.smart_emobility_backend.common.domain.*;
import vn.edu.bku.smart_emobility_backend.service.*;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public record RideResponse(StudentState state) {
    public record Operation(String operationId,String type,StudentOperation.Status status,Instant createdAt,Instant updatedAt,Object result,String error) {}
    record Pickup(String rideId,String vehicleId,RideStatus status,String startHubId,Instant startedAt,Double initialBatteryPct) {}
    record Returned(String rideId,String vehicleId,RideStatus status,String endHubId,Instant endedAt,Double initialBatteryPct,Double finalBatteryPct,VehicleStatus vehicleStatus) {}
    ResponseEntity<?> command(StudentOperation op) {
        return switch (op.status()) {
            case FAILED -> throw new StudentFailure(HttpStatus.UNPROCESSABLE_CONTENT,"IOT_COMMAND_FAILED","Device rejected command");
            case PENDING -> {
                if (op.kind() == StudentOperation.Kind.RETURN)
                    throw new StudentFailure(HttpStatus.SERVICE_UNAVAILABLE,"IOT_COMMAND_TIMEOUT","Awaiting lock confirmation. Retry with the same key.");
                yield ResponseEntity.accepted().body(Map.of("operationId",op.id(),"status",op.status(),
                    "statusUrl","/api/operations/"+op.id(),"retryAfterSeconds",2));
            }
            case SUCCEEDED -> ResponseEntity.status(op.kind() == StudentOperation.Kind.PICKUP ? HttpStatus.CREATED : HttpStatus.OK).body(result(op));
        };
    }
    Operation operation(StudentOperation op) {
        return new Operation(op.id(),op.kind() == StudentOperation.Kind.PICKUP ? "UNLOCK_VEHICLE" : "LOCK_AT_SPACE",
                op.status(),op.createdAt(),op.finishedAt() == null ? op.createdAt() : op.finishedAt(),
                op.status() == StudentOperation.Status.SUCCEEDED ? Map.of("rideId",op.rideId(),"vehicleId",op.vehicleId()) : null,
                op.status() == StudentOperation.Status.FAILED ? "Device rejected command" : null);
    }
    private Object result(StudentOperation op) {
        Ride ride = state.get(Ride.class,op.rideId());
        if (op.kind() == StudentOperation.Kind.PICKUP) return new Pickup(ride.id(),ride.vehicleId(),RideStatus.ACTIVE,ride.startHubId(),ride.startedAt(),ride.startBatteryPct());
        return new Returned(ride.id(),ride.vehicleId(),ride.status(),ride.endHubId(),ride.endedAt(),ride.startBatteryPct(),ride.endBatteryPct(),op.finalVehicleStatus());
    }
}
