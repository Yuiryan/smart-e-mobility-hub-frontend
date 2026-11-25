package vn.edu.bku.smart_emobility_backend.iot;

import java.time.Instant;
import vn.edu.bku.smart_emobility_backend.common.domain.CommandStatus;
import vn.edu.bku.smart_emobility_backend.common.domain.IdentifiedEntity;

public record DeviceOperation(Instant createdAt, DeviceResponse response, String accountId,
        Instant updatedAt) implements IdentifiedEntity {
    public DeviceOperation(Instant createdAt, DeviceResponse response) {
        this(createdAt, response, null, createdAt);
    }

    public DeviceOperation {
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    public String id() {
        return response.commandId();
    }

    boolean terminal() {
        return response.status() == CommandStatus.ACKNOWLEDGED || response.status() == CommandStatus.FAILED;
    }
}
