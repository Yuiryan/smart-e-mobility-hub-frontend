package vn.edu.bku.smart_emobility_backend.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import vn.edu.bku.smart_emobility_backend.common.domain.DataQuality;
import vn.edu.bku.smart_emobility_backend.common.domain.Vehicle;
import vn.edu.bku.smart_emobility_backend.common.store.VehicleDataStore;
import vn.edu.bku.smart_emobility_backend.dto.VehicleDetails;

@Service
public class VehicleLookupService {
    private final VehicleDataStore vehicleDataStore;
    private final Clock clock;
    private final Duration freshnessLimit;

    public VehicleLookupService(VehicleDataStore vehicleDataStore, Clock clock,
            @Value("${app.search.freshness-limit:PT10M}") Duration freshnessLimit) {
        this.vehicleDataStore = vehicleDataStore;
        this.clock = clock;
        this.freshnessLimit = freshnessLimit;
    }

    private StudentDevicePort device;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setDevice(StudentDevicePort device) { this.device = device; }

    public VehicleDetails onVehicleReserved(String vehicleId) { return getVehicleDetails(vehicleId); }

    public VehicleDetails getVehicleDetails(String vehicleId) {
        Vehicle vehicle = vehicleDataStore.findById(vehicleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "vehicle not found"));
        StudentDevicePort.Reading reading;
        try {
            reading = device == null ? new StudentDevicePort.Reading(vehicleId,vehicle.batteryPct(),vehicle.telemetryAt())
                    : device.readVehicleStatus(vehicleId);
        } catch (StudentDevicePort.Unavailable unavailable) {
            return new VehicleDetails(vehicleId,vehicle.status(),null,null,"Telemetry unavailable","telemetryAt");
        }
        boolean matched = reading != null && vehicleId.equals(reading.vehicleId());
        Instant observed = matched ? reading.observedAt() : null;
        Double battery = matched ? reading.batteryPct() : null;
        if (battery != null && (!Double.isFinite(battery) || battery < 0 || battery > 100)) battery = null;
        DataQuality quality = battery == null ? DataQuality.MISSING : quality(observed, clock.instant());
        String reason = quality == DataQuality.FRESH ? null
                : quality == DataQuality.STALE ? "Telemetry is stale" : "Telemetry is missing";
        String missingData = quality == DataQuality.MISSING ? (observed == null ? "telemetryAt" : "batteryLevel") : null;
        // A delayed telemetry response must not restore the business status observed before the call.
        if (device != null) vehicle = vehicleDataStore.findById(vehicleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"vehicle not found"));
        return new VehicleDetails(vehicle.vehicleId(), vehicle.status(), battery,
                observed, reason, missingData);
    }

    private DataQuality quality(Instant observedAt, Instant now) {
        if (observedAt == null) return DataQuality.MISSING;
        Duration age = Duration.between(observedAt, now);
        return !age.isNegative() && age.compareTo(freshnessLimit) <= 0
                ? DataQuality.FRESH : DataQuality.STALE;
    }
}
