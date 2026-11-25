package vn.edu.bku.smart_emobility_backend.dto.operator;

import vn.edu.bku.smart_emobility_backend.common.domain.IncidentStatus;

public enum OperatorIncidentStatus {
    OPEN,
    MAINTENANCE,
    RESOLVED,
    DISMISSED;

    public static OperatorIncidentStatus fromDomain(IncidentStatus status) {
        if (status == null) {
            return OPEN;
        }
        return switch (status) {
            case OPEN -> OPEN;
            case INVESTIGATING -> MAINTENANCE;
            case RESOLVED -> RESOLVED;
            case DISMISSED -> DISMISSED;
        };
    }

    public IncidentStatus toDomain() {
        return switch (this) {
            case OPEN -> IncidentStatus.OPEN;
            case MAINTENANCE -> IncidentStatus.INVESTIGATING;
            case RESOLVED -> IncidentStatus.RESOLVED;
            case DISMISSED -> IncidentStatus.DISMISSED;
        };
    }
}
