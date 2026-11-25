package vn.edu.bku.smart_emobility_backend.service;

import java.time.Instant;
import vn.edu.bku.smart_emobility_backend.common.domain.StudentOperation.Kind;

/** Student-facing contract for the system team's adapter. Retrying lookup must never send a command. */
public interface StudentDevicePort {
    /** Adapter signals a transport failure when no authoritative reply is available. */
    final class Unavailable extends RuntimeException {
        public Unavailable(String message, Throwable cause) { super(message,cause); }
    }
    enum Outcome { ACK_SUCCESS, REJECTED, UNKNOWN }
    record Reply(String commandId, String vehicleId, String spaceId, Outcome outcome, Double batteryPct) {}
    record Reading(String vehicleId, Double batteryPct, Instant observedAt) {}
    Reading readVehicleStatus(String vehicleId);
    Reply send(String commandId, Kind kind, String vehicleId, String spaceId);
    Reply getResult(String commandId);
}
