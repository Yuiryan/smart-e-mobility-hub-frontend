package vn.edu.bku.smart_emobility_backend.dto;

import java.time.Instant;
import vn.edu.bku.smart_emobility_backend.common.domain.VehicleStatus;

public record VehicleDetails(String vehicleId, VehicleStatus status, Double batteryLevel,
        Instant updatedAt, String reason, String missingData) { }
