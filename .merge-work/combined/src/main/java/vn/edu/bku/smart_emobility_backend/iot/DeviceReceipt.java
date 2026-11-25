package vn.edu.bku.smart_emobility_backend.iot;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import vn.edu.bku.smart_emobility_backend.common.domain.CommandStatus;
import vn.edu.bku.smart_emobility_backend.common.domain.IdentifiedEntity;

/** Durable execution evidence held by the simulated device. */
public record DeviceReceipt(DeviceResponse result, Instant acceptedAt, Instant availableAt,
        Duration timeout, boolean dropAcknowledgement, long sequence) implements IdentifiedEntity {
    public String id() {
        return result.commandId();
    }
    public static List<DeviceCommand> activePortReservations(List<DeviceReceipt> receipts, Instant now) {
        Map<String, DeviceCommand> active = new LinkedHashMap<>();
        for (DeviceReceipt receipt : receipts.stream().sorted(Comparator.comparingLong(DeviceReceipt::sequence)).toList()) {
            if (receipt.result().status() != CommandStatus.ACKNOWLEDGED) {
                continue;
            }
            DeviceCommand command = receipt.result().command();
            if (command.type() == DeviceCommand.Type.RESERVE_PORT) {
                active.put(command.reservationId(), command);
            } else if (command.type() == DeviceCommand.Type.RELEASE_PORT) {
                active.remove(command.reservationId());
            }
        }
        return active.values().stream().filter(command -> command.endAt().isAfter(now)).toList();
    }
}
