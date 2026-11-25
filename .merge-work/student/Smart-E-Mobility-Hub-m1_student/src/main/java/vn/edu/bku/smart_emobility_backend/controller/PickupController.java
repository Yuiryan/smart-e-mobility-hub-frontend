package vn.edu.bku.smart_emobility_backend.controller;

import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import vn.edu.bku.smart_emobility_backend.common.domain.*;
import vn.edu.bku.smart_emobility_backend.service.*;

@RestController
@RequestMapping("/api")
public record PickupController(StudentAccess access,VehiclePickupService pickups,StudentRideCommands commands,RideResponse responses) {
    @PostMapping("/pickups")
    public ResponseEntity<?> pickup(@RequestHeader(value="Authorization",required=false) String auth, @RequestHeader("Idempotency-Key") UUID key, @RequestBody VehiclePickupService.PickupRequestDTO request) {
        return responses.command(pickups.processScannedCode(access.fromHeader(auth),key,request));
    }
    @GetMapping("/operations/{id}")
    public RideResponse.Operation operation(@RequestHeader(value="Authorization",required=false) String auth, @PathVariable String id) {
        return responses.operation(commands.lookup(access.fromHeader(auth),id));
    }
}
