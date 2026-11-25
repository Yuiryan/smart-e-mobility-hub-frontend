package vn.edu.bku.smart_emobility_backend.service.operator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.edu.bku.smart_emobility_backend.common.domain.GeoPoint;
import vn.edu.bku.smart_emobility_backend.common.domain.MobilityHub;
import vn.edu.bku.smart_emobility_backend.common.domain.Vehicle;
import vn.edu.bku.smart_emobility_backend.common.domain.VehicleStatus;
import vn.edu.bku.smart_emobility_backend.common.error.ApiErrorResponseDTO.ErrorBody;
import vn.edu.bku.smart_emobility_backend.common.error.ApiException;
import vn.edu.bku.smart_emobility_backend.common.error.GlobalExceptionHandler;
import vn.edu.bku.smart_emobility_backend.common.store.InMemoryDataStore;
import vn.edu.bku.smart_emobility_backend.common.store.OperatorStateRegistry;
import vn.edu.bku.smart_emobility_backend.common.store.OperatorStateRegistry.PortMeta;
import vn.edu.bku.smart_emobility_backend.common.store.OperatorStateRegistry.SpaceMeta;
import vn.edu.bku.smart_emobility_backend.common.store.OperatorStateRegistry.VehicleMeta;
import vn.edu.bku.smart_emobility_backend.common.store.StoreSnapshot;
import vn.edu.bku.smart_emobility_backend.dto.operator.*;
import vn.edu.bku.smart_emobility_backend.service.operator.DeviceGateway.CommandOutcome;
import vn.edu.bku.smart_emobility_backend.service.operator.DeviceGateway.CommandType;
import vn.edu.bku.smart_emobility_backend.service.operator.DeviceGateway.DeviceCommand;
import vn.edu.bku.smart_emobility_backend.service.operator.DeviceGateway.DeviceState;
import vn.edu.bku.smart_emobility_backend.service.operator.DeviceGateway.OutcomeStatus;

/**
 * Coordinates asynchronous IoT operations, resource guards, timeout reconciliation,
 * and audit updates for UC-09 and UC-12.
 */
@Service
public class OperationOrchestrator {
    private final InMemoryDataStore dataStore;
    private final OperatorStateRegistry stateRegistry;
    private final AuditLogService auditLogService;
    private final DeviceGateway deviceGateway;
    private final Clock clock;
    private final Duration reconcilingThreshold;
    private final Duration deadlineThreshold;

    private final Map<String, ManagedOperation> operations = new ConcurrentHashMap<>();
    private final Map<String, String> commandToOperation = new ConcurrentHashMap<>();
    private final Set<String> processedCommandCallbacks = ConcurrentHashMap.newKeySet();
    private final AtomicLong operationSeq = new AtomicLong(1);
    private final AtomicLong commandSeq = new AtomicLong(1);

    public OperationOrchestrator(
            InMemoryDataStore dataStore,
            OperatorStateRegistry stateRegistry,
            AuditLogService auditLogService,
            DeviceGateway deviceGateway,
            Clock clock,
            @Value("${app.operator.reconciling-threshold:PT5S}") Duration reconcilingThreshold,
            @Value("${app.operator.deadline-threshold:PT30S}") Duration deadlineThreshold) {
        this.dataStore = dataStore;
        this.stateRegistry = stateRegistry;
        this.auditLogService = auditLogService;
        this.deviceGateway = deviceGateway;
        this.clock = clock;
        this.reconcilingThreshold = reconcilingThreshold;
        this.deadlineThreshold = deadlineThreshold;
        this.deviceGateway.registerOutcomeListener(this::handleOutcomeCallback);
    }

    public String nextOperationId() {
        return String.format("op-%04d", operationSeq.getAndIncrement());
    }

    public String nextCommandId() {
        return String.format("cmd-%04d", commandSeq.getAndIncrement());
    }

    /**
     * Registers a new pending operation, sets the pending guard on the target resource,
     * records ACCEPTED audit entry, and submits the command to DeviceGateway.
     */
    public PendingEnvelopeDTO startOperation(
            String operationId,
            OperationKind kind,
            OperationResultType expectedResultType,
            ResourceType resourceType,
            String resourceId,
            long expectedResourceVersion,
            String auditId,
            CommandType commandType,
            Map<String, Object> commandPayload,
            Function<CommandOutcome, Object> onAckSuccessCommit,
            Runnable onFailureCleanup) {
        Instant now = clock.instant();
        String commandId = nextCommandId();

        setResourcePendingOperation(resourceType, resourceId, operationId);

        ManagedOperation managed = new ManagedOperation(
                operationId,
                commandId,
                kind,
                expectedResultType,
                OperationStatus.PENDING,
                resourceType,
                resourceId,
                auditId,
                commandType,
                now,
                now,
                null,
                null,
                onAckSuccessCommit,
                onFailureCleanup);

        operations.put(operationId, managed);
        commandToOperation.put(commandId, operationId);

        DeviceCommand deviceCommand = new DeviceCommand(
                commandId,
                operationId,
                resourceType,
                resourceId,
                commandType,
                commandPayload,
                now,
                expectedResourceVersion);

        try {
            deviceGateway.submit(deviceCommand);
        } catch (ApiException ex) {
            // If gateway throws before accepting (e.g. 503 DEPENDENCY_UNAVAILABLE), clean up guard & operation
            operations.remove(operationId);
            commandToOperation.remove(commandId);
            clearResourcePendingOperation(resourceType, resourceId, operationId);
            if (onFailureCleanup != null) {
                onFailureCleanup.run();
            }
            auditLogService.updateOutcome(auditId, AuditOutcome.FAILED, ex.code());
            throw ex;
        }

        return PendingEnvelopeDTO.of(operationId);
    }

    public synchronized OperationDTO getOperation(String operationId) {
        ManagedOperation op = operations.get(operationId);
        if (op == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                    "Không tìm thấy operation: " + operationId);
        }
        evaluateTimeouts(op);
        op = operations.get(operationId);
        return toDto(op);
    }

    public synchronized void handleOutcomeCallback(CommandOutcome outcome) {
        if (outcome == null || outcome.commandId() == null) {
            return;
        }
        String opId = outcome.operationId() != null
                ? outcome.operationId()
                : commandToOperation.get(outcome.commandId());
        if (opId == null) {
            return;
        }
        ManagedOperation op = operations.get(opId);
        if (op == null || !op.commandId().equals(outcome.commandId())) {
            return;
        }
        if (op.status().isTerminal()) {
            return;
        }
        if (outcome.status() == OutcomeStatus.PENDING) {
            return;
        }
        // Deduplicate terminal callbacks per commandId
        if (!processedCommandCallbacks.add(outcome.commandId())) {
            return;
        }

        Instant now = clock.instant();
        if (outcome.status() == OutcomeStatus.ACK_SUCCESS) {
            if (!isDeviceStateMatchingCommand(op.commandType(), outcome.deviceState())) {
                failOperation(op, now, "IOT_REJECTED",
                        "Phản hồi ACK từ thiết bị không khớp trạng thái mong muốn của lệnh " + op.commandType());
                return;
            }
            try {
                clearResourcePendingOperation(op.resourceType(), op.resourceId(), op.operationId());
                Object resultObj = op.onAckSuccessCommit().apply(outcome);
                auditLogService.updateOutcome(op.auditId(), AuditOutcome.SUCCEEDED, null);
                operations.put(op.operationId(), op.withStatusAndResult(
                        OperationStatus.SUCCEEDED, now, resultObj, null));
            } catch (Exception ex) {
                // If commit failed after device ACK, enter RECONCILING per Section 4
                setResourcePendingOperation(op.resourceType(), op.resourceId(), op.operationId());
                auditLogService.updateOutcome(op.auditId(), AuditOutcome.RECONCILING, "STATE_CONFLICT");
                operations.put(op.operationId(), op.withStatusAndResult(
                        OperationStatus.RECONCILING, now, null, null));
            }
        } else if (outcome.status() == OutcomeStatus.ACK_REJECTED) {
            String errCode = outcome.errorCode() != null ? outcome.errorCode() : "IOT_REJECTED";
            failOperation(op, now, errCode, "Thiết bị IoT từ chối thực hiện lệnh");
        } else if (outcome.status() == OutcomeStatus.CANCELLED) {
            String errCode = outcome.errorCode() != null ? outcome.errorCode() : "IOT_TIMEOUT";
            failOperation(op, now, errCode, "Adapter xác nhận lệnh chưa áp dụng trước deadline");
        } else if (outcome.status() == OutcomeStatus.UNKNOWN) {
            auditLogService.updateOutcome(op.auditId(), AuditOutcome.RECONCILING, null);
            operations.put(op.operationId(), op.withStatusAndResult(
                    OperationStatus.RECONCILING, now, null, null));
        }
    }

    private void evaluateTimeouts(ManagedOperation op) {
        if (op.status().isTerminal()) {
            return;
        }
        Instant now = clock.instant();
        Duration elapsed = Duration.between(op.createdAt(), now);

        if (elapsed.compareTo(deadlineThreshold) >= 0) {
            CommandOutcome queried = deviceGateway.query(op.commandId());
            if (queried != null && (queried.status() == OutcomeStatus.ACK_SUCCESS
                    || queried.status() == OutcomeStatus.ACK_REJECTED)) {
                handleOutcomeCallback(queried);
                return;
            }
            CommandOutcome cancelled = deviceGateway.cancel(op.commandId());
            if (cancelled != null && cancelled.status() == OutcomeStatus.CANCELLED) {
                handleOutcomeCallback(cancelled);
                return;
            }
            // Outcome still UNKNOWN -> remain RECONCILING and keep guard
            if (op.status() != OperationStatus.RECONCILING) {
                auditLogService.updateOutcome(op.auditId(), AuditOutcome.RECONCILING, null);
                operations.put(op.operationId(), op.withStatusAndResult(
                        OperationStatus.RECONCILING, now, null, null));
            }
            return;
        }

        if (elapsed.compareTo(reconcilingThreshold) >= 0 && op.status() == OperationStatus.PENDING) {
            auditLogService.updateOutcome(op.auditId(), AuditOutcome.RECONCILING, null);
            operations.put(op.operationId(), op.withStatusAndResult(
                    OperationStatus.RECONCILING, now, null, null));
        }
    }

    private void failOperation(ManagedOperation op, Instant now, String errorCode, String message) {
        clearResourcePendingOperation(op.resourceType(), op.resourceId(), op.operationId());
        if (op.onFailureCleanup() != null) {
            op.onFailureCleanup().run();
        }
        auditLogService.updateOutcome(op.auditId(), AuditOutcome.FAILED, errorCode);
        ErrorBody errorBody = new ErrorBody(
                errorCode,
                message,
                List.of(),
                UUID.randomUUID().toString(),
                GlobalExceptionHandler.formatTimestamp(now));
        operations.put(op.operationId(), op.withStatusAndResult(
                OperationStatus.FAILED, now, null, errorBody));
    }

    private boolean isDeviceStateMatchingCommand(CommandType commandType, DeviceState state) {
        if (commandType == CommandType.VERIFY_STATE) {
            return true;
        }
        if (state == null) {
            return true;
        }
        return switch (commandType) {
            case LOCK -> state.isLocked() == null || Boolean.TRUE.equals(state.isLocked());
            case UNLOCK -> state.isLocked() == null || Boolean.FALSE.equals(state.isLocked());
            case DISABLE_POWER -> state.powerEnabled() == null || Boolean.FALSE.equals(state.powerEnabled());
            case ENABLE_POWER -> state.powerEnabled() == null || Boolean.TRUE.equals(state.powerEnabled());
            case STOP_CHARGING -> state.chargingStopped() == null || Boolean.TRUE.equals(state.chargingStopped());
            case SET_BATTERY -> state.batteryPct() == null
                    || (state.batteryPct() >= 0.0 && state.batteryPct() <= 100.0);
            case BLOCK_SPACE -> state.spaceBlocked() == null || Boolean.TRUE.equals(state.spaceBlocked());
            case UNBLOCK_SPACE -> state.spaceBlocked() == null || Boolean.FALSE.equals(state.spaceBlocked());
            case VERIFY_STATE -> true;
        };
    }

    public void setResourcePendingOperation(ResourceType type, String resourceId, String operationId) {
        StoreSnapshot snapshot = dataStore.snapshot();
        switch (type) {
            case VEHICLE -> {
                Vehicle v = snapshot.vehicles().stream()
                        .filter(x -> x.vehicleId().equals(resourceId))
                        .findFirst().orElse(null);
                MobilityHub hub = (v != null && v.hubId() != null)
                        ? snapshot.hubs().stream().filter(h -> h.hubId().equals(v.hubId())).findFirst().orElse(null)
                        : null;
                GeoPoint defaultLoc = hub != null ? hub.location() : null;
                boolean defaultLocked = v == null || v.status() != VehicleStatus.IN_USE;
                VehicleMeta current = stateRegistry.getOrDefaultVehicleMeta(
                        resourceId, v != null ? v.telemetryAt() : null, defaultLoc, defaultLocked);
                stateRegistry.putVehicleMeta(resourceId, new VehicleMeta(
                        current.batterySource(),
                        current.batteryUpdatedAt(),
                        current.connectionStatus(),
                        current.location(),
                        current.isLocked(),
                        operationId,
                        current.maintenanceCauses()));
            }
            case CHARGING_PORT -> {
                PortMeta current = stateRegistry.getPortMeta(resourceId);
                if (current == null) {
                    stateRegistry.putPortMeta(resourceId, new PortMeta(
                            null, null, null, null, 0L, operationId, Set.of()));
                } else {
                    stateRegistry.putPortMeta(resourceId, new PortMeta(
                            current.measuredPowerW(),
                            current.powerEnabled(),
                            current.observedAt(),
                            current.connectionStatus(),
                            current.version(),
                            operationId,
                            current.maintenanceCauses()));
                }
            }
            case PARKING_SPACE -> {
                SpaceMeta current = stateRegistry.getSpaceMeta(resourceId);
                if (current == null) {
                    stateRegistry.putSpaceMeta(resourceId, new SpaceMeta(
                            null, null, null, null, null, 0L, operationId, null));
                } else {
                    stateRegistry.putSpaceMeta(resourceId, new SpaceMeta(
                            current.blocked(),
                            current.reservationKind(),
                            current.vehicleId(),
                            current.parkingReservationId(),
                            current.dispatchOrderId(),
                            current.version(),
                            operationId,
                            current.observedAt()));
                }
            }
        }
    }

    public void clearResourcePendingOperation(ResourceType type, String resourceId, String operationId) {
        switch (type) {
            case VEHICLE -> {
                VehicleMeta current = stateRegistry.getVehicleMeta(resourceId);
                if (current != null && (operationId == null || operationId.equals(current.pendingOperationId()))) {
                    stateRegistry.putVehicleMeta(resourceId, new VehicleMeta(
                            current.batterySource(),
                            current.batteryUpdatedAt(),
                            current.connectionStatus(),
                            current.location(),
                            current.isLocked(),
                            null,
                            current.maintenanceCauses()));
                }
            }
            case CHARGING_PORT -> {
                PortMeta current = stateRegistry.getPortMeta(resourceId);
                if (current != null && (operationId == null || operationId.equals(current.pendingOperationId()))) {
                    stateRegistry.putPortMeta(resourceId, new PortMeta(
                            current.measuredPowerW(),
                            current.powerEnabled(),
                            current.observedAt(),
                            current.connectionStatus(),
                            current.version(),
                            null,
                            current.maintenanceCauses()));
                }
            }
            case PARKING_SPACE -> {
                SpaceMeta current = stateRegistry.getSpaceMeta(resourceId);
                if (current != null && (operationId == null || operationId.equals(current.pendingOperationId()))) {
                    stateRegistry.putSpaceMeta(resourceId, new SpaceMeta(
                            current.blocked(),
                            current.reservationKind(),
                            current.vehicleId(),
                            current.parkingReservationId(),
                            current.dispatchOrderId(),
                            current.version(),
                            null,
                            current.observedAt()));
                }
            }
        }
    }

    public synchronized void clear() {
        operations.clear();
        commandToOperation.clear();
        processedCommandCallbacks.clear();
        operationSeq.set(1);
        commandSeq.set(1);
    }

    private OperationDTO toDto(ManagedOperation op) {
        OperationResultType resultType = op.status() == OperationStatus.SUCCEEDED ? op.expectedResultType() : null;
        Object result = op.status() == OperationStatus.SUCCEEDED ? op.result() : null;
        ErrorBody error = op.status() == OperationStatus.FAILED ? op.error() : null;
        Integer retryAfter = op.status().isTerminal() ? null : 2;

        return new OperationDTO(
                op.operationId(),
                op.kind(),
                op.status(),
                op.resourceType(),
                op.resourceId(),
                GlobalExceptionHandler.formatTimestamp(op.createdAt()),
                GlobalExceptionHandler.formatTimestamp(op.updatedAt()),
                resultType,
                result,
                error,
                retryAfter);
    }

    private record ManagedOperation(
            String operationId,
            String commandId,
            OperationKind kind,
            OperationResultType expectedResultType,
            OperationStatus status,
            ResourceType resourceType,
            String resourceId,
            String auditId,
            CommandType commandType,
            Instant createdAt,
            Instant updatedAt,
            Object result,
            ErrorBody error,
            Function<CommandOutcome, Object> onAckSuccessCommit,
            Runnable onFailureCleanup) {

        ManagedOperation withStatusAndResult(
                OperationStatus newStatus,
                Instant newUpdatedAt,
                Object newResult,
                ErrorBody newError) {
            return new ManagedOperation(
                    operationId,
                    commandId,
                    kind,
                    expectedResultType,
                    newStatus,
                    resourceType,
                    resourceId,
                    auditId,
                    commandType,
                    createdAt,
                    newUpdatedAt,
                    newResult,
                    newError,
                    onAckSuccessCommit,
                    onFailureCleanup);
        }
    }
}
