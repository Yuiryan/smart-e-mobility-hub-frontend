package vn.edu.bku.smart_emobility_backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.edu.bku.smart_emobility_backend.service.ChargingRequestService;
import vn.edu.bku.smart_emobility_backend.service.StudentAccess;

@RestController
@RequestMapping("/api/charging-requests")
public record ChargingController(StudentAccess access, ChargingRequestService charging) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ChargingRequestService.Receipt bookCharging(@RequestHeader(value="Authorization",required=false) String auth,
            @RequestBody ChargingRequestService.Booking request) {
        return charging.bookCharging(access.fromHeader(auth),request);
    }
}
