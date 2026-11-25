package vn.edu.bku.smart_emobility_backend.common.domain;

import java.time.Instant;

/** Durable claim and receipt for one physical command. No raw pickup credential is stored. */
public record StudentOperation(String id, String studentId, String vehicleId, String spaceId, String rideId,
        String reservationId, Kind kind, String payloadHash, boolean issueReported, Instant createdAt,
        Status status, Double finalBatteryPct, VehicleStatus finalVehicleStatus, Instant finishedAt) implements IdentifiedEntity {
    public enum Kind { PICKUP, RETURN }
    public enum Status { PENDING, FAILED, SUCCEEDED }
    public StudentOperation finish(Status outcome, Double battery, VehicleStatus vehicleStatus, Instant at) {
        return new StudentOperation(id, studentId, vehicleId, spaceId, rideId, reservationId, kind,
                payloadHash, issueReported, createdAt, outcome, battery, vehicleStatus, at);
    }
}
