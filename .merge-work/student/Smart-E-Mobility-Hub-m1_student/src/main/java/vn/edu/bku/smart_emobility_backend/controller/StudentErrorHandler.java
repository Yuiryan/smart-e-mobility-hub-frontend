package vn.edu.bku.smart_emobility_backend.controller;

import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import vn.edu.bku.smart_emobility_backend.common.domain.*;
import vn.edu.bku.smart_emobility_backend.service.*;
import java.time.Instant;
import org.springframework.web.ErrorResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes={HubSearchController.class, VehicleDetailsController.class, BookingController.class,
        ParkingController.class, PickupController.class, ReturnController.class, ChargingController.class})
public class StudentErrorHandler {
    @ExceptionHandler({ResponseStatusException.class, MethodArgumentNotValidException.class, MissingRequestHeaderException.class,
            HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    ResponseEntity<?> handle(Exception ex) {
        HttpStatusCode status = ex instanceof ErrorResponse error ? error.getStatusCode() : HttpStatus.BAD_REQUEST;
        String message = ex instanceof ResponseStatusException error && error.getReason() != null ? error.getReason() : "Invalid student request";
        String code = ex instanceof StudentFailure failure ? failure.code() : switch (status.value()) {
            case 400 -> "VALIDATION_ERROR";
            case 401 -> "AUTH_TOKEN_EXPIRED";
            case 403 -> "ACCESS_DENIED";
            case 404 -> "RESOURCE_NOT_FOUND";
            case 409 -> "OPTIMISTIC_LOCK_CONFLICT";
            case 422 -> "VALIDATION_ERROR";
            case 503 -> "IOT_COMMAND_TIMEOUT";
            default -> "INTERNAL_ERROR";
        };
        return ResponseEntity.status(status).body(Map.of("error",Map.of("code",code,
                "message",message,"details",List.of(),"requestId",UUID.randomUUID().toString(),"timestamp",Instant.now())));
    }
}
