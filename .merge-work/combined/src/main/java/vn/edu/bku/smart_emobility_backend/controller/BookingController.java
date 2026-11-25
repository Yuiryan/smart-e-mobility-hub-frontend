package vn.edu.bku.smart_emobility_backend.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.bku.smart_emobility_backend.dto.BookingResponseDTO;
import vn.edu.bku.smart_emobility_backend.dto.BookingRequestDTO;
import vn.edu.bku.smart_emobility_backend.service.ReservationService;

@RestController
@RequestMapping("/api/reservations")
public class BookingController {
    private final ReservationService reservationService;

    public BookingController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponseDTO createReservation(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody BookingRequestDTO request) {
        return reservationService.confirmReservation(bearerToken(authorization), request);
    }

    @PostMapping("/{reservationId}/cancel")
    public void cancelReservation(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String reservationId) {
        reservationService.cancelReservation(bearerToken(authorization), reservationId);
    }

    private String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")
                || authorization.length() <= "Bearer ".length()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Authorization must use Bearer token");
        }
        return authorization.substring("Bearer ".length()).trim();
    }
}
