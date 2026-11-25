package vn.edu.bku.smart_emobility_backend.iot;

import java.time.Clock;
import java.util.Optional;
import java.time.Instant;
import org.springframework.stereotype.Component;
import vn.edu.bku.smart_emobility_backend.common.domain.DataQuality;

@Component
public class IoTGateway {
    private final IoTAdapter adapter;
    private final Clock clock;

    public IoTGateway(IoTAdapter adapter, Clock clock) {
        this.adapter = adapter;
        this.clock = clock;
    }

    public DeviceResponse unlock(String vehicleId, String commandId) {
        return adapter.send(new DeviceCommand(commandId, DeviceCommand.Type.UNLOCK, vehicleId, null));
    }

    public DeviceResponse lockAtSpace(String vehicleId, String spaceId, String commandId) {
        return adapter.send(new DeviceCommand(commandId, DeviceCommand.Type.LOCK, vehicleId, spaceId));
    }

    public DeviceResponse forceLock(String vehicleId, String commandId) {
        return adapter.send(new DeviceCommand(commandId, DeviceCommand.Type.FORCE_LOCK, vehicleId, null));
    }

    public DeviceResponse unlock(String vehicleId, String commandId, String accountId) {
        return adapter.send(new DeviceCommand(commandId, DeviceCommand.Type.UNLOCK, vehicleId, null), accountId);
    }

    public DeviceResponse lockAtSpace(String vehicleId, String spaceId, String commandId, String accountId) {
        return adapter.send(new DeviceCommand(commandId, DeviceCommand.Type.LOCK, vehicleId, spaceId), accountId);
    }

    public DeviceResponse forceLock(String vehicleId, String commandId, String accountId) {
        return adapter.send(new DeviceCommand(commandId, DeviceCommand.Type.FORCE_LOCK, vehicleId, null), accountId);
    }

    public Optional<DeviceResponse> reconcile(String commandId) {
        return adapter.reconcileResult(commandId);
    }

    public Optional<DeviceResponse> getResult(String commandId) {
        return adapter.getResult(commandId);
    }

    public VehicleTelemetry getVehicleTelemetry(String vehicleId) {
        if (vehicleId == null || vehicleId.isBlank()) {
            throw new IllegalArgumentException("vehicleId is required");
        }
        var sample = adapter.readVehicleStatus(vehicleId);
        if (sample.isEmpty()) {
            return new VehicleTelemetry(vehicleId, null, null, null, null, null, null, DataQuality.MISSING);
        }
        VehicleReading reading = sample.get();
        return new VehicleTelemetry(vehicleId, reading.batteryPct(), reading.status(), reading.latitude(),
                reading.longitude(), reading.lockStatus(), reading.observedAt(), quality(reading, clock.instant()));
    }

    private DataQuality quality(VehicleReading reading, Instant now) {
        if (!reading.complete() || reading.observedAt().isAfter(now)) {
            return DataQuality.MISSING;
        }
        return reading.isCurrentAt(now) ? DataQuality.FRESH : DataQuality.STALE;
    }
}
