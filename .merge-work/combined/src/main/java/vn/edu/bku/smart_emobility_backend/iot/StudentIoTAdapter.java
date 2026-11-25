package vn.edu.bku.smart_emobility_backend.iot;

import org.springframework.stereotype.Component;
import vn.edu.bku.smart_emobility_backend.common.domain.Student;
import vn.edu.bku.smart_emobility_backend.common.domain.StudentOperation;
import vn.edu.bku.smart_emobility_backend.common.store.InMemoryDataStore;
import vn.edu.bku.smart_emobility_backend.service.StudentDevicePort;

/** Bridges durable student claims to the shared device journal. */
@Component
public record StudentIoTAdapter(IoTGateway gateway, InMemoryDataStore store) implements StudentDevicePort {
    @Override
    public Reading readVehicleStatus(String vehicleId) {
        var telemetry = gateway.getVehicleTelemetry(vehicleId);
        return new Reading(vehicleId, telemetry.batteryPct(), telemetry.observedAt());
    }

    @Override
    public Reply send(String commandId, StudentOperation.Kind kind, String vehicleId, String spaceId) {
        var claim = store.findById(StudentOperation.class, commandId).orElseThrow();
        var owner = store.findById(Student.class, claim.studentId()).orElseThrow();
        var response = kind == StudentOperation.Kind.PICKUP
                ? gateway.unlock(vehicleId, commandId, owner.accountId())
                : gateway.lockAtSpace(vehicleId, spaceId, commandId, owner.accountId());
        return reply(response, claim);
    }

    @Override
    public Reply getResult(String commandId) {
        var claim = store.findById(StudentOperation.class, commandId).orElseThrow();
        return gateway.getResult(commandId).map(response -> reply(response, claim)).orElse(null);
    }

    private Reply reply(DeviceResponse response, StudentOperation claim) {
        Outcome outcome = switch (response.status()) {
            case ACKNOWLEDGED -> Outcome.ACK_SUCCESS;
            case FAILED -> Outcome.REJECTED;
            default -> Outcome.UNKNOWN;
        };
        return new Reply(response.commandId(), response.command().vehicleId(),
                response.command().spaceId() == null ? claim.spaceId() : response.command().spaceId(),
                outcome, response.batteryLevel());
    }
}
