package vn.edu.bku.smart_emobility_backend.service;

import java.time.Instant;
import java.util.Set;

/** Integration contract. Each reservation ID identifies one idempotent booking at the device. */
public interface StudentChargingPort {
    enum Result { CONFIRMED, REJECTED, UNKNOWN }
    record HubStatus(String hubId, Instant observedAt, Set<String> availablePortIds) {}
    HubStatus readHubStatus(String hubId);
    Result reservePort(String requestId,String portId,Instant start,Instant end);
    Result releasePort(String requestId,String portId);
}
