package vn.edu.bku.smart_emobility_backend.iot;

import java.time.Instant;
import java.util.Objects;

public record DeviceCommand(String commandId, Type type, String vehicleId, String spaceId,
        String hubId, String portId, String reservationId, Instant startAt, Instant endAt) {
    public enum Type { LOCK, UNLOCK, FORCE_LOCK, RESERVE_PORT, RELEASE_PORT }

    public DeviceCommand(String commandId, Type type, String vehicleId, String spaceId) {
        this(commandId, type, vehicleId, spaceId, null, null, null, null, null);
    }

    public DeviceCommand {
        Objects.requireNonNull(type, "type");
        requireId(commandId, "commandId");
        boolean portCommand = type == Type.RESERVE_PORT || type == Type.RELEASE_PORT;
        if (portCommand) {
            requireId(hubId, "hubId");
            requireId(portId, "portId");
            requireId(reservationId, "reservationId");
            if (vehicleId != null || spaceId != null) {
                throw new IllegalArgumentException("Port commands cannot target a vehicle or parking space");
            }
            if (type == Type.RESERVE_PORT) {
                if (startAt == null || endAt == null || !startAt.isBefore(endAt)) {
                    throw new IllegalArgumentException("Port reservation requires startAt before endAt");
                }
            } else if (startAt != null || endAt != null) {
                throw new IllegalArgumentException("Release identifies an existing reservation");
            }
        } else {
            requireId(vehicleId, "vehicleId");
            if (hubId != null || portId != null || reservationId != null || startAt != null || endAt != null) {
                throw new IllegalArgumentException("Vehicle commands cannot include a port reservation");
            }
            if (type == Type.LOCK) {
                requireId(spaceId, "spaceId");
            } else if (spaceId != null) {
                throw new IllegalArgumentException("Only return locking specifies a parking space");
            }
        }
    }

    private static void requireId(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
