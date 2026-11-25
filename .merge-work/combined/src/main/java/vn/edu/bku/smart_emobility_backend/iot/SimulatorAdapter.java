package vn.edu.bku.smart_emobility_backend.iot;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.edu.bku.smart_emobility_backend.common.domain.ChargingAllocation;
import vn.edu.bku.smart_emobility_backend.common.domain.ChargingAllocationStatus;
import vn.edu.bku.smart_emobility_backend.common.domain.ChargingPortStatus;
import vn.edu.bku.smart_emobility_backend.common.domain.CommandStatus;
import vn.edu.bku.smart_emobility_backend.common.domain.DataQuality;
import vn.edu.bku.smart_emobility_backend.common.store.InMemoryDataStore;
import vn.edu.bku.smart_emobility_backend.common.store.StateCoordinator;

@Component
public class SimulatorAdapter implements IoTAdapter {
    public enum CommandMode { NORMAL, REJECT, DROP_ACK }

    private final Map<String, VehicleReading> latest = new HashMap<>();
    private final Map<String, DeviceReceipt> localReceipts = new HashMap<>();
    private final Clock clock;
    private final CommandMode commandMode;
    private final Duration responseDelay;
    private final Duration commandTimeout;
    private final StationSensors sensors;
    private final InMemoryDataStore store;
    private final StateCoordinator coordinator;
    private long localSequence;

    public SimulatorAdapter(Clock clock) {
        this(clock, Duration.ZERO, Duration.ofSeconds(5));
    }

    public SimulatorAdapter(Clock clock, Duration responseDelay, Duration commandTimeout) {
        this(clock, responseDelay, commandTimeout, CommandMode.NORMAL);
    }

    public SimulatorAdapter(Clock clock, Duration responseDelay, Duration commandTimeout, CommandMode mode) {
        this(clock, responseDelay, commandTimeout, mode, new StationSensors(clock), null, null);
    }

    @Autowired
    public SimulatorAdapter(Clock clock,
            @Value("${app.iot.simulator.response-delay:PT0S}") Duration responseDelay,
            @Value("${app.iot.simulator.command-timeout:PT5S}") Duration commandTimeout,
            @Value("${app.iot.simulator.command-mode:NORMAL}") CommandMode mode,
            StationSensors sensors, InMemoryDataStore store, StateCoordinator coordinator) {
        if (responseDelay.isNegative() || commandTimeout.isNegative() || commandTimeout.isZero()) {
            throw new IllegalArgumentException("Response delay must be nonnegative and timeout must be positive");
        }
        if ((store == null) != (coordinator == null)) {
            throw new IllegalArgumentException("Store and coordinator must be supplied together");
        }
        this.commandMode = java.util.Objects.requireNonNull(mode, "mode");
        this.clock = clock;
        this.responseDelay = responseDelay;
        this.commandTimeout = commandTimeout;
        this.sensors = sensors;
        this.store = store;
        this.coordinator = coordinator;
    }

    public synchronized void emitTelemetry(VehicleReading sample) {
        if (sample.observedAt().isAfter(clock.instant())) {
            throw new IllegalArgumentException("observedAt is in the future");
        }
        VehicleReading previous = latest.get(sample.vehicleId());
        if (previous == null || sample.observedAt().isAfter(previous.observedAt())) {
            latest.put(sample.vehicleId(), sample);
        }
    }

    @Override
    public synchronized DeviceResponse send(DeviceCommand command) {
        // All durable device effects and their receipt share one coordinator commit.
        synchronized (store == null ? localReceipts : store) {
            Optional<DeviceReceipt> previous = receipt(command.commandId());
            if (previous.isPresent()) {
                if (!previous.get().result().command().equals(command)) {
                    throw new IllegalArgumentException("Command ID already belongs to a different command");
                }
                return visibleResult(previous.get(), clock.instant());
            }
            Instant now = clock.instant();
            DeviceResponse result = commandMode == CommandMode.REJECT
                    ? response(command, CommandStatus.FAILED, now, "Simulated device rejection")
                    : execute(command, now);
            long sequence = store == null ? localSequence + 1 : store.version() + 1;
            DeviceReceipt receipt = new DeviceReceipt(result, now, now.plus(responseDelay), commandTimeout,
                    commandMode == CommandMode.DROP_ACK, sequence);
            if (store == null) {
                localReceipts.put(command.commandId(), receipt);
                localSequence = sequence;
            } else if (!coordinator.commit(sequence - 1, data -> data.put(receipt))) {
                throw new IllegalStateException("Simulator receipt version conflict");
            }
            return visibleResult(receipt, now);
        }
    }

    private DeviceResponse visibleResult(DeviceReceipt receipt, Instant now) {
        if (!receipt.dropAcknowledgement() && !now.isBefore(receipt.availableAt())) {
            return receipt.result();
        }
        if (!now.isBefore(receipt.acceptedAt().plus(receipt.timeout()))) {
            return response(receipt.result().command(), CommandStatus.AWAITING_RECONCILIATION,
                    now, "Timed out waiting for device acknowledgement");
        }
        return response(receipt.result().command(), CommandStatus.PENDING, receipt.acceptedAt(), null);
    }

    private DeviceResponse execute(DeviceCommand command, Instant now) {
        if (command.type() == DeviceCommand.Type.RESERVE_PORT || command.type() == DeviceCommand.Type.RELEASE_PORT) {
            return executePort(command, now);
        }
        VehicleReading sample = latest.get(command.vehicleId());
        if (sample == null || !sample.isCurrentAt(now) || sample.lockStatus() == null) {
            return response(command, CommandStatus.AWAITING_RECONCILIATION, now, "No current lock sensor reading");
        }
        if (command.type() == DeviceCommand.Type.LOCK) {
            if (sample.spaceId() == null) {
                return response(command, CommandStatus.AWAITING_RECONCILIATION, now, "Parking space reading unavailable");
            }
            if (!command.spaceId().equals(sample.spaceId())) {
                return response(command, CommandStatus.FAILED, now, "Vehicle is at a different space");
            }
        }
        return new DeviceResponse(command, CommandStatus.ACKNOWLEDGED, now, sample.batteryPct(), null);
    }

    private DeviceResponse executePort(DeviceCommand command, Instant now) {
        List<DeviceCommand> history = receipts().stream()
                .filter(receipt -> receipt.result().status() == CommandStatus.ACKNOWLEDGED)
                .map(receipt -> receipt.result().command())
                .filter(previous -> previous.type() == DeviceCommand.Type.RESERVE_PORT
                        && previous.reservationId().equals(command.reservationId())).toList();
        if (command.type() == DeviceCommand.Type.RELEASE_PORT) {
            boolean known = history.stream().anyMatch(previous -> samePort(previous, command));
            return response(command, known ? CommandStatus.ACKNOWLEDGED : CommandStatus.FAILED, now,
                    known ? null : "Reservation does not belong to this port");
        }
        if (command.startAt().isBefore(now)) {
            return response(command, CommandStatus.FAILED, now, "Reservation start is in the past");
        }
        List<DeviceCommand> active = activePortReservations();
        if (!history.isEmpty()) {
            boolean same = active.stream().anyMatch(previous -> sameReservation(previous, command));
            return response(command, same ? CommandStatus.ACKNOWLEDGED : CommandStatus.FAILED, now,
                    same ? null : "Reservation identifier already used");
        }
        if (store != null && store.findAll(ChargingAllocation.class).stream().anyMatch(allocation ->
                command.portId().equals(allocation.chargingPortId())
                        && (allocation.status() == ChargingAllocationStatus.ACTIVE
                            || allocation.status() == ChargingAllocationStatus.RESERVED)
                        && (allocation.startsAt() == null || allocation.endsAt() == null
                            || !allocation.startsAt().isBefore(allocation.endsAt())
                            || (allocation.startsAt().isBefore(command.endAt())
                                && command.startAt().isBefore(allocation.endsAt()))))) {
            return response(command, CommandStatus.FAILED, now, "Port already allocated");
        }
        var reading = sensors.readPort(command.hubId(), command.portId());
        if (reading.quality() != DataQuality.FRESH) {
            return response(command, CommandStatus.AWAITING_RECONCILIATION, now, "No current port sensor reading");
        }
        if (reading.value().status() != ChargingPortStatus.AVAILABLE) {
            return response(command, CommandStatus.FAILED, now, "Port is not available");
        }
        boolean overlaps = active.stream().anyMatch(previous -> samePort(previous, command)
                && previous.startAt().isBefore(command.endAt()) && command.startAt().isBefore(previous.endAt()));
        return response(command, overlaps ? CommandStatus.FAILED : CommandStatus.ACKNOWLEDGED, now,
                overlaps ? "Port reservation overlaps" : null);
    }

    public synchronized List<DeviceCommand> activePortReservations() {
        return DeviceReceipt.activePortReservations(receipts(), clock.instant());
    }

    private boolean samePort(DeviceCommand left, DeviceCommand right) {
        return left.hubId().equals(right.hubId()) && left.portId().equals(right.portId());
    }

    private boolean sameReservation(DeviceCommand left, DeviceCommand right) {
        return left.reservationId().equals(right.reservationId()) && samePort(left, right)
                && left.startAt().equals(right.startAt()) && left.endAt().equals(right.endAt());
    }

    private DeviceResponse response(DeviceCommand command, CommandStatus status, Instant now, String reason) {
        return new DeviceResponse(command, status, now, null, reason);
    }

    private Optional<DeviceReceipt> receipt(String commandId) {
        return store == null ? Optional.ofNullable(localReceipts.get(commandId))
                : store.findById(DeviceReceipt.class, commandId);
    }

    private List<DeviceReceipt> receipts() {
        return store == null ? List.copyOf(localReceipts.values()) : store.findAll(DeviceReceipt.class);
    }

    private List<DeviceReceipt> orderedReceipts() {
        return receipts().stream().sorted(Comparator.comparingLong(DeviceReceipt::sequence)).toList();
    }

    @Override
    public synchronized Optional<DeviceResponse> getResult(String commandId) {
        return receipt(commandId).map(receipt -> visibleResult(receipt, clock.instant()));
    }

    @Override
    public synchronized Optional<DeviceResponse> reconcileResult(String commandId) {
        return receipt(commandId).map(DeviceReceipt::result);
    }

    @Override
    public synchronized Optional<VehicleReading> readVehicleStatus(String vehicleId) {
        VehicleReading sample = latest.get(vehicleId);
        if (sample == null || sample.lockStatus() == null) {
            return Optional.ofNullable(sample);
        }
        VehicleReading.LockStatus state = sample.lockStatus();
        for (DeviceReceipt receipt : orderedReceipts()) {
            DeviceCommand command = receipt.result().command();
            if (receipt.result().status() == CommandStatus.ACKNOWLEDGED && vehicleId.equals(command.vehicleId())) {
                state = command.type() == DeviceCommand.Type.UNLOCK
                        ? VehicleReading.LockStatus.UNLOCKED : VehicleReading.LockStatus.LOCKED;
            }
        }
        return Optional.of(sample.withLockStatus(state));
    }
}
