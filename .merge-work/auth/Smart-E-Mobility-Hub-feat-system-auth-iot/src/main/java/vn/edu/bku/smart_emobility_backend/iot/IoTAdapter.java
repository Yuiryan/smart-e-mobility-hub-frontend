package vn.edu.bku.smart_emobility_backend.iot;

import java.util.Optional;

public interface IoTAdapter {
    DeviceResponse send(DeviceCommand command);

    default DeviceResponse send(DeviceCommand command, String accountId) {
        throw new UnsupportedOperationException("Adapter does not support account-bound operations");
    }

    Optional<DeviceResponse> getResult(String commandId);

    default Optional<DeviceResponse> reconcileResult(String commandId) {
        return getResult(commandId);
    }

    Optional<VehicleReading> readVehicleStatus(String vehicleId);
}
