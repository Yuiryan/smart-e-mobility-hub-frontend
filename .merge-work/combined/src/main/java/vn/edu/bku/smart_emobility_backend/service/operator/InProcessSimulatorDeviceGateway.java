package vn.edu.bku.smart_emobility_backend.service.operator;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.edu.bku.smart_emobility_backend.common.error.ApiException;

/**
 * In-process simulator gateway implementation of DeviceGateway.
 * Supports automatic ACK_SUCCESS (default for demo) as well as manual/deferred ACK,
 * ACK_REJECTED, TIMEOUT, and UNAVAILABLE modes for testing and simulation.
 */
@Component
public class InProcessSimulatorDeviceGateway implements DeviceGateway {

    public enum SimulationMode {
        AUTO_ACK_SUCCESS,
        DEFER_ACK,
        AUTO_ACK_REJECTED,
        UNAVAILABLE
    }

    private final Clock clock;
    private final List<Consumer<CommandOutcome>> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, DeviceCommand> commands = new ConcurrentHashMap<>();
    private final Map<String, CommandOutcome> outcomes = new ConcurrentHashMap<>();
    private volatile SimulationMode mode = SimulationMode.AUTO_ACK_SUCCESS;

    public InProcessSimulatorDeviceGateway(Clock clock) {
        this.clock = clock;
    }

    public void setMode(SimulationMode mode) {
        this.mode = mode;
    }

    public SimulationMode getMode() {
        return mode;
    }

    @Override
    public void registerOutcomeListener(Consumer<CommandOutcome> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    @Override
    public AcceptedCommand submit(DeviceCommand command) {
        if (mode == SimulationMode.UNAVAILABLE) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE",
                    "Hệ thống IoT / Bộ giả lập hiện không sẵn sàng nhận lệnh");
        }
        Instant now = clock.instant();
        commands.put(command.commandId(), command);

        if (mode == SimulationMode.AUTO_ACK_SUCCESS) {
            DeviceState desiredState = buildDesiredDeviceState(command);
            CommandOutcome outcome = new CommandOutcome(
                    command.commandId(),
                    command.operationId(),
                    OutcomeStatus.ACK_SUCCESS,
                    now,
                    desiredState,
                    null);
            outcomes.put(command.commandId(), outcome);
            notifyListeners(outcome);
        } else if (mode == SimulationMode.AUTO_ACK_REJECTED) {
            CommandOutcome outcome = new CommandOutcome(
                    command.commandId(),
                    command.operationId(),
                    OutcomeStatus.ACK_REJECTED,
                    now,
                    null,
                    "IOT_REJECTED");
            outcomes.put(command.commandId(), outcome);
            notifyListeners(outcome);
        } else {
            CommandOutcome pending = new CommandOutcome(
                    command.commandId(),
                    command.operationId(),
                    OutcomeStatus.PENDING,
                    null,
                    null,
                    null);
            outcomes.put(command.commandId(), pending);
        }
        return new AcceptedCommand(command.commandId(), now);
    }

    @Override
    public CommandOutcome query(String commandId) {
        CommandOutcome existing = outcomes.get(commandId);
        if (existing != null) {
            return existing;
        }
        DeviceCommand cmd = commands.get(commandId);
        return new CommandOutcome(
                commandId,
                cmd != null ? cmd.operationId() : null,
                OutcomeStatus.UNKNOWN,
                null,
                null,
                null);
    }

    @Override
    public CommandOutcome cancel(String commandId) {
        CommandOutcome existing = outcomes.get(commandId);
        if (existing != null && (existing.status() == OutcomeStatus.ACK_SUCCESS
                || existing.status() == OutcomeStatus.ACK_REJECTED
                || existing.status() == OutcomeStatus.CANCELLED)) {
            return existing;
        }
        DeviceCommand cmd = commands.get(commandId);
        CommandOutcome cancelled = new CommandOutcome(
                commandId,
                cmd != null ? cmd.operationId() : null,
                OutcomeStatus.CANCELLED,
                clock.instant(),
                null,
                "IOT_TIMEOUT");
        outcomes.put(commandId, cancelled);
        return cancelled;
    }

    /**
     * Allows tests or simulator threads to deliver an explicit CommandOutcome callback.
     */
    public void emitOutcome(CommandOutcome outcome) {
        outcomes.put(outcome.commandId(), outcome);
        notifyListeners(outcome);
    }

    public DeviceState buildDesiredDeviceState(DeviceCommand command) {
        return switch (command.commandType()) {
            case LOCK -> new DeviceState(true, null, null, null, null);
            case UNLOCK -> new DeviceState(false, null, null, null, null);
            case DISABLE_POWER -> new DeviceState(null, false, null, true, null);
            case ENABLE_POWER -> new DeviceState(null, true, null, false, null);
            case STOP_CHARGING -> new DeviceState(null, true, null, true, null);
            case SET_BATTERY -> {
                Object pctObj = command.payload() != null ? command.payload().get("batteryPct") : null;
                Double pct = pctObj instanceof Number n ? n.doubleValue() : null;
                yield new DeviceState(null, null, pct, null, null);
            }
            case BLOCK_SPACE -> new DeviceState(null, null, null, null, true);
            case UNBLOCK_SPACE -> new DeviceState(null, null, null, null, false);
            case VERIFY_STATE -> new DeviceState(null, null, null, null, null);
        };
    }

    public Map<String, DeviceCommand> submittedCommands() {
        return Map.copyOf(commands);
    }

    public void reset() {
        commands.clear();
        outcomes.clear();
        mode = SimulationMode.AUTO_ACK_SUCCESS;
    }

    private void notifyListeners(CommandOutcome outcome) {
        for (Consumer<CommandOutcome> listener : listeners) {
            listener.accept(outcome);
        }
    }
}
