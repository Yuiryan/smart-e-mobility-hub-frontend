package vn.edu.bku.smart_emobility_backend.dto;

import vn.edu.bku.smart_emobility_backend.common.domain.VehicleStatus;
import vn.edu.bku.smart_emobility_backend.common.domain.VehicleType;

public record VehicleSummaryDTO(String vehicleId, VehicleType type, Double batteryPct,
        VehicleStatus status) {
}
