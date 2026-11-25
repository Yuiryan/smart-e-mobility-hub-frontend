package vn.edu.bku.smart_emobility_backend.common.domain;

import java.time.Instant;
import com.fasterxml.jackson.annotation.JsonAlias;

public record Reservation(@JsonAlias("id") String reservationId, String studentId, String vehicleId,
        @JsonAlias("startsAt") Instant startAt, @JsonAlias("endsAt") Instant endAt, @JsonAlias("expiresAt") Instant holdUntil, ReservationStatus status,
        String pickupCodeHash, @com.fasterxml.jackson.annotation.JsonProperty("accountId") String legacyAccountId) implements IdentifiedEntity {
    public Reservation(String reservationId,String studentId,String vehicleId,Instant startAt,Instant endAt,Instant holdUntil,
            ReservationStatus status,String pickupCodeHash) {
        this(reservationId,studentId,vehicleId,startAt,endAt,holdUntil,status,pickupCodeHash,null);
    }
    public Reservation(String id,String accountId,String vehicleId,String hash,Instant startsAt,Instant endsAt,Instant createdAt,Instant expiresAt,ReservationStatus status) {
        this(id,null,vehicleId,startsAt,endsAt,expiresAt,status,hash,accountId);
    }
    public boolean isOwnedBy(Student student) {
        return studentId == null ? student.accountId().equals(legacyAccountId) : student.id().equals(studentId);
    }
    public Reservation withStatus(ReservationStatus next) {
        return new Reservation(id(), studentId, vehicleId, startAt, endAt, holdUntil,next,null,legacyAccountId);
    }
    @Override
    public String id() { return reservationId; }

    public boolean isActiveAt(Instant now) {
        return status == ReservationStatus.ACTIVE && holdUntil != null && now != null
                && now.isBefore(holdUntil);
    }

    public Reservation revokePickupCode() {
        return new Reservation(reservationId, studentId, vehicleId, startAt, endAt,
                holdUntil, status, null,legacyAccountId);
    }

    public Reservation cancel(Instant now) {
        if (status != ReservationStatus.ACTIVE || !isActiveAt(now)) {
            throw new IllegalStateException("reservation is not active");
        }
        return new Reservation(reservationId, studentId, vehicleId, startAt, endAt,
                holdUntil, ReservationStatus.CANCELLED, null,legacyAccountId);
    }
}
