package vn.edu.bku.smart_emobility_backend.common.domain;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record Account(
        @JsonProperty("accountId") @JsonAlias("id") String id,
        String fullName,
        String studentId,
        String email,
        String passwordHash,
        AccountRole role,
        AccountStatus status,
        Instant createdAt,
        Instant updatedAt
) implements IdentifiedEntity {

    public Account(String id, String email, String passwordHash, AccountRole role,
            boolean active, Instant createdAt) {
        this(id, null, null, email, passwordHash, role,
                active ? AccountStatus.ACTIVE : AccountStatus.SUSPENDED,
                createdAt, createdAt);
    }

    public Account {
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    /** Compatibility accessor for modules merged from the legacy boolean model. */
    public boolean active() {
        return isActive();
    }

    @Override
    public String toString() {
        return "Account[id=" + id
                + ", role=" + role
                + ", status=" + status + "]";
    }
}
