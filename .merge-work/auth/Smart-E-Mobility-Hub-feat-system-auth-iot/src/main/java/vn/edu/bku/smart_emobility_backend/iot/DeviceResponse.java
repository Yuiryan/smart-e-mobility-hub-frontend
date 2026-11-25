package vn.edu.bku.smart_emobility_backend.iot;

import java.time.Instant;
import vn.edu.bku.smart_emobility_backend.common.domain.CommandStatus;

public record DeviceResponse(DeviceCommand command, CommandStatus status, Instant observedAt,
        Double batteryLevel, String reason) {
    public String commandId() {
        return command.commandId();
    }
}
