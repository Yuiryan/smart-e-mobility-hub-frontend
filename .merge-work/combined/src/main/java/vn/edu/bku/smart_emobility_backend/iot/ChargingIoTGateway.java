package vn.edu.bku.smart_emobility_backend.iot;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class ChargingIoTGateway {
    private final IoTAdapter adapter;
    private final SimulatorAdapter simulator;
    private final StationSensors sensors;

    public ChargingIoTGateway(IoTAdapter adapter, SimulatorAdapter simulator, StationSensors sensors) {
        this.adapter = adapter;
        this.simulator = simulator;
        this.sensors = sensors;
    }

    public StationSensors.Measurement<StationSensors.Frame> readHubStatus(String hubId) {
        return sensors.readHubStatus(hubId);
    }

    public StationSensors.Measurement<StationSensors.Port> readPort(String hubId, String portId) {
        return sensors.readPort(hubId, portId);
    }

    public DeviceResponse reservePort(String hubId, String portId, String reservationId,
            Instant startAt, Instant endAt, String commandId) {
        return adapter.send(new DeviceCommand(commandId, DeviceCommand.Type.RESERVE_PORT, null, null,
                hubId, portId, reservationId, startAt, endAt));
    }

    public DeviceResponse releasePort(String hubId, String portId, String reservationId, String commandId) {
        return adapter.send(new DeviceCommand(commandId, DeviceCommand.Type.RELEASE_PORT, null, null,
                hubId, portId, reservationId, null, null));
    }

    public DeviceResponse reservePort(String hubId, String portId, String reservationId,
            Instant startAt, Instant endAt, String commandId, String accountId) {
        return adapter.send(new DeviceCommand(commandId, DeviceCommand.Type.RESERVE_PORT, null, null,
                hubId, portId, reservationId, startAt, endAt), accountId);
    }

    public DeviceResponse releasePort(String hubId, String portId, String reservationId,
            String commandId, String accountId) {
        return adapter.send(new DeviceCommand(commandId, DeviceCommand.Type.RELEASE_PORT, null, null,
                hubId, portId, reservationId, null, null), accountId);
    }

    public List<DeviceCommand> activeReservations(String hubId) {
        return simulator.activePortReservations().stream().filter(command -> command.hubId().equals(hubId)).toList();
    }

    public Optional<DeviceResponse> getResult(String commandId) {
        return adapter.getResult(commandId);
    }

    public Optional<DeviceResponse> reconcile(String commandId) {
        return adapter.reconcileResult(commandId);
    }
}
