package vn.edu.bku.smart_emobility_backend.common.domain;

import java.time.Instant;
import java.util.List;

public record Student(String studentId, String accountId, String name,
        AccountStatus accountStatus, Instant verifiedAt) implements IdentifiedEntity {
    @Override
    public String id() { return studentId; }

    public boolean isAuthenticated() {
        return accountStatus == AccountStatus.ACTIVE && verifiedAt != null;
    }

    public boolean hasActiveReservation(List<Reservation> reservations, Instant now) {
        return reservations.stream().anyMatch(r -> r.isOwnedBy(this)
                && r.isActiveAt(now));
    }

    public boolean hasActiveRide(List<Ride> rides) {
        return rides.stream().anyMatch(r -> accountId.equals(r.accountId())
                && r.status() == RideStatus.ACTIVE);
    }
}
