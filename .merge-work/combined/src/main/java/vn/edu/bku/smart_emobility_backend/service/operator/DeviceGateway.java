package vn.edu.bku.smart_emobility_backend.service.operator;

import java.time.Instant;
import java.util.Map;
import java.util.function.Consumer;
import vn.edu.bku.smart_emobility_backend.dto.operator.ResourceType;

/**
 * Consumer contract with the IoT/Simulator subsystem per Section 11 of 04-m2-api-contract.md.
 */
public interface DeviceGateway {

    AcceptedCommand submit(DeviceCommand command);

    CommandOutcome query(String commandId);

    CommandOutcome cancel(String commandId);

    void registerOutcomeListener(Consumer<CommandOutcome> listener);

    public enum CommandType {
        LOCK,
        UNLOCK,
        DISABLE_POWER,
        ENABLE_POWER,
        STOP_CHARGING,
        VERIFY_STATE,
        SET_BATTERY,
        BLOCK_SPACE,
        UNBLOCK_SPACE
    }

    public enum OutcomeStatus {
        PENDING,
        ACK_SUCCESS,
        ACK_REJECTED,
        CANCELLED,
        UNKNOWN
    }

    public record DeviceCommand(
            String commandId,
            String operationId,
            ResourceType resourceType,
            String resourceId,
            CommandType commandType,
            Map<String, Object> payload,
            Instant issuedAt,
            long expectedResourceVersion) {
    }

    public record AcceptedCommand(
            String commandId,
            Instant acceptedAt) {
    }

    public record DeviceState(
            Boolean isLocked,
            Boolean powerEnabled,
            Double batteryPct,
            Boolean chargingStopped,
            Boolean spaceBlocked) {
    }

    public record CommandOutcome(
            String commandId,
            String operationId,
            OutcomeStatus status,
            Instant observedAt,
            DeviceState deviceState,
            String errorCode) {
    }
}
