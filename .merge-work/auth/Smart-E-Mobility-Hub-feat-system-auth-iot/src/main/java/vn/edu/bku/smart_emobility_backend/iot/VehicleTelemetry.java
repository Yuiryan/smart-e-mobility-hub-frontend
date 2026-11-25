package vn.edu.bku.smart_emobility_backend.iot;

import java.time.Instant;
import vn.edu.bku.smart_emobility_backend.common.domain.DataQuality;
import vn.edu.bku.smart_emobility_backend.common.domain.VehicleStatus;

public record VehicleTelemetry(String vehicleId, Double batteryPct, VehicleStatus status, Double latitude,
        Double longitude, VehicleReading.LockStatus lockStatus, Instant observedAt, DataQuality quality) {
}
