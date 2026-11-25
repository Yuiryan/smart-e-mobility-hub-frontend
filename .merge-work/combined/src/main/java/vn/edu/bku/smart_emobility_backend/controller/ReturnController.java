package vn.edu.bku.smart_emobility_backend.controller;

import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import vn.edu.bku.smart_emobility_backend.common.domain.*;
import vn.edu.bku.smart_emobility_backend.service.*;

@RestController
public record ReturnController(StudentAccess access, VehicleReturnService returns, RideResponse responses) {
    public record Destination(String hubId, String spaceId) {}
    @PostMapping("/api/rides/{rideId}/return")
    public ResponseEntity<?> returnVehicle(@RequestHeader(value="Authorization",required=false) String auth, @RequestHeader("Idempotency-Key") UUID key,
            @PathVariable String rideId,@RequestBody Destination request) {
        return responses.command(returns.returnVehicle(access.fromHeader(auth),key,rideId,new VehicleReturnService.ReturnRequest(request.hubId(),request.spaceId(),false)));
    }
}
