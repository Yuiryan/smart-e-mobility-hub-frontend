package vn.edu.bku.smart_emobility_backend.iot;

import java.time.Clock;
import java.util.Optional;
import java.util.Objects;
import vn.edu.bku.smart_emobility_backend.common.domain.Account;
import vn.edu.bku.smart_emobility_backend.common.domain.AccountRole;
import vn.edu.bku.smart_emobility_backend.common.exception.AuthorizationException;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import vn.edu.bku.smart_emobility_backend.common.domain.CommandStatus;
import vn.edu.bku.smart_emobility_backend.common.store.InMemoryDataStore;
import vn.edu.bku.smart_emobility_backend.common.store.StateCoordinator;

@Component
@Primary
public class JournaledIoTAdapter implements IoTAdapter {
    private final SimulatorAdapter device;
    private final InMemoryDataStore store;
    private final StateCoordinator coordinator;
    private final Clock clock;

    public JournaledIoTAdapter(SimulatorAdapter device, InMemoryDataStore store, StateCoordinator coordinator, Clock clock) {
        this.device = device;
        this.store = store;
        this.coordinator = coordinator;
        this.clock = clock;
    }

    @Override
    public synchronized DeviceResponse send(DeviceCommand command) {
        return send(command, null);
    }

    @Override
    public synchronized DeviceResponse send(DeviceCommand command, String accountId) {
        Dispatch dispatch = findOrCreateIntent(command, accountId);
        if (!dispatch.created()) {
            return reconcile(dispatch.operation());
        }
        return update(dispatch.operation(), device.send(command));
    }

    @Override
    public synchronized Optional<DeviceResponse> getResult(String commandId) {
        return store.findById(DeviceOperation.class, commandId).map(this::reconcile);
    }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void reconcileAfterRestart() {
        store.findAll(DeviceOperation.class).stream().filter(op -> !op.terminal()).forEach(op -> reconcileResult(op.id()));
    }

    @Override
    public synchronized Optional<DeviceResponse> reconcileResult(String commandId) {
        return store.findById(DeviceOperation.class, commandId).map(op -> {
            if (op.terminal()) {
                return op.response();
            }
            DeviceResponse result = device.reconcileResult(commandId).orElseGet(() ->
                    new DeviceResponse(op.response().command(), CommandStatus.AWAITING_RECONCILIATION,
                            clock.instant(), null, "Device receipt unavailable"));
            return update(op, result);
        });
    }

    private DeviceResponse reconcile(DeviceOperation op) {
        if (op.terminal()) {
            return op.response();
        }
        var result = device.getResult(op.id()).orElseGet(() -> new DeviceResponse(op.response().command(),
                CommandStatus.AWAITING_RECONCILIATION, clock.instant(), null, "Device receipt unavailable"));
        return update(op, result);
    }

    private DeviceResponse update(DeviceOperation op, DeviceResponse result) {
        if (!op.response().command().equals(result.command())) {
            throw new IllegalStateException("Device returned a response for another command");
        }
        if (op.response().status() == result.status() && java.util.Objects.equals(op.response().reason(), result.reason())) {
            return op.response();
        }
        save(new DeviceOperation(op.createdAt(), result, op.accountId(), clock.instant()));
        return result;
    }

    private record Dispatch(DeviceOperation operation, boolean created) {}

    private Dispatch findOrCreateIntent(DeviceCommand command, String accountId) {
        synchronized (store) {
            if (accountId != null) {
                Account account = store.findById(Account.class, accountId)
                        .orElseThrow(() -> new AuthorizationException("Account is unavailable"));
                if (!account.isActive()
                        || (command.type() == DeviceCommand.Type.FORCE_LOCK && account.role() != AccountRole.OPERATOR)) {
                    throw new AuthorizationException("Account cannot execute this device command");
                }
                if (command.reservationId() != null && account.role() != AccountRole.OPERATOR) {
                    boolean belongsToAnotherAccount = store.findAll(DeviceOperation.class).stream()
                            .filter(op -> op.response().command().type() == DeviceCommand.Type.RESERVE_PORT
                                    && command.reservationId().equals(op.response().command().reservationId())
                                    && op.response().status() != CommandStatus.FAILED)
                            .anyMatch(op -> !Objects.equals(op.accountId(), accountId));
                    if (belongsToAnotherAccount) {
                        throw new AuthorizationException("Port reservation belongs to another account");
                    }
                }
            }
            var existing = store.findById(DeviceOperation.class, command.commandId());
            if (existing.isPresent()) {
                if (!Objects.equals(accountId, existing.get().accountId())) {
                    throw new AuthorizationException("Operation belongs to another account");
                }
                if (!existing.get().response().command().equals(command)) {
                    throw new IllegalArgumentException("Command ID already belongs to a different command");
                }
                return new Dispatch(existing.get(), false);
            }
            var now = clock.instant();
            var intent = new DeviceOperation(now, new DeviceResponse(command, CommandStatus.PENDING, now, null, null), accountId, now);
            save(intent);
            return new Dispatch(intent, true);
        }
    }

    private void save(DeviceOperation op) {
        synchronized (store) {
            if (!coordinator.commit(store.version(), data -> data.put(op))) {
                throw new IllegalStateException("Device operation version conflict");
            }
        }
    }

    @Override
    public Optional<VehicleReading> readVehicleStatus(String vehicleId) {
        return device.readVehicleStatus(vehicleId);
    }
}
