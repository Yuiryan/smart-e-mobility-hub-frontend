package vn.edu.bku.smart_emobility_backend.controller;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.edu.bku.smart_emobility_backend.common.domain.ParkingReservation;
import vn.edu.bku.smart_emobility_backend.service.ParkingReservationService;
import vn.edu.bku.smart_emobility_backend.service.StudentAccess;

@RestController
@RequestMapping("/api/parking-reservations")
public record ParkingController(StudentAccess access, ParkingReservationService parking) {
    public record Booking(String hubId, String spaceId, Instant startsAt, Instant endsAt) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> reserveSpace(@RequestHeader(value="Authorization",required=false) String auth,
            @RequestBody Booking request) {
        ParkingReservation hold = parking.reserveSelectedSpace(access.fromHeader(auth),request.hubId(),
                request.spaceId(),request.startsAt(),request.endsAt());
        return Map.of("parkingReservationId",hold.id(),"hubId",request.hubId(),"spaceId",hold.parkingSpaceId(),
                "status",hold.status(),"startsAt",hold.startsAt(),"endsAt",hold.endsAt(),"expiresAt",hold.holdExpiresAt());
    }
}
