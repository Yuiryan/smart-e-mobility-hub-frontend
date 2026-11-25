package vn.edu.bku.smart_emobility_backend.service;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import vn.edu.bku.smart_emobility_backend.common.domain.Account;
import vn.edu.bku.smart_emobility_backend.common.domain.AccountRole;
import vn.edu.bku.smart_emobility_backend.common.domain.CommandStatus;
import vn.edu.bku.smart_emobility_backend.common.exception.AuthorizationException;
import vn.edu.bku.smart_emobility_backend.common.exception.ResourceNotFoundException;
import vn.edu.bku.smart_emobility_backend.common.store.InMemoryDataStore;
import vn.edu.bku.smart_emobility_backend.dto.OperationResponseDTO;
import vn.edu.bku.smart_emobility_backend.iot.DeviceCommand;
import vn.edu.bku.smart_emobility_backend.iot.DeviceOperation;
import vn.edu.bku.smart_emobility_backend.iot.JournaledIoTAdapter;

@Service
public class OperationService {
    private final InMemoryDataStore store;
    private final JournaledIoTAdapter journal;

    public OperationService(InMemoryDataStore store, JournaledIoTAdapter journal) {
        this.store = store;
        this.journal = journal;
    }

    public OperationResponseDTO find(String operationId, String accountId) {
        Account account = store.findById(Account.class, accountId)
                .orElseThrow(() -> new AuthorizationException("Account is unavailable"));
        if (!account.isActive()) {
            throw new AuthorizationException("Account is inactive");
        }
        DeviceOperation operation = store.findById(DeviceOperation.class, operationId)
                .orElseThrow(() -> new ResourceNotFoundException("Operation was not found"));
        if (account.role() != AccountRole.OPERATOR && !account.id().equals(operation.accountId())) {
            throw new AuthorizationException("Operation belongs to another account");
        }
        journal.getResult(operationId);
        operation = store.findById(DeviceOperation.class, operationId)
                .orElseThrow(() -> new ResourceNotFoundException("Operation was not found"));
        var response = operation.response();
        String status = switch (response.status()) {
            case ACKNOWLEDGED -> "SUCCEEDED";
            case FAILED -> "FAILED";
            case PENDING -> "PENDING";
            case AWAITING_RECONCILIATION -> "AWAITING_RECONCILIATION";
        };
        OperationResponseDTO.OperationError error = null;
        if (response.status() == CommandStatus.FAILED) {
            error = new OperationResponseDTO.OperationError("IOT_COMMAND_FAILED", response.reason());
        } else if (response.status() == CommandStatus.AWAITING_RECONCILIATION) {
            error = new OperationResponseDTO.OperationError("IOT_RECONCILIATION_REQUIRED", response.reason());
        }
        return new OperationResponseDTO(operation.id(), type(response.command()), status, operation.createdAt(),
                operation.updatedAt(), response.status() == CommandStatus.ACKNOWLEDGED ? result(response.command()) : null, error);
    }

    private String type(DeviceCommand command) {
        return switch (command.type()) {
            case UNLOCK -> "UNLOCK_VEHICLE";
            case LOCK -> "LOCK_VEHICLE";
            case FORCE_LOCK -> "FORCE_LOCK_VEHICLE";
            case RESERVE_PORT -> "RESERVE_CHARGING_PORT";
            case RELEASE_PORT -> "RELEASE_CHARGING_PORT";
        };
    }

    private Map<String, String> result(DeviceCommand command) {
        Map<String, String> values = new LinkedHashMap<>();
        if (command.vehicleId() != null) {
            values.put("vehicleId", command.vehicleId());
        }
        if (command.spaceId() != null) {
            values.put("spaceId", command.spaceId());
        }
        if (command.portId() != null) {
            values.put("portId", command.portId());
            values.put("hubId", command.hubId());
            values.put("reservationId", command.reservationId());
        }
        return values;
    }
}
