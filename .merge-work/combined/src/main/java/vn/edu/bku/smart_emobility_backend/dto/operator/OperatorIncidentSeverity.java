package vn.edu.bku.smart_emobility_backend.dto.operator;

import vn.edu.bku.smart_emobility_backend.common.domain.IncidentSeverity;

public enum OperatorIncidentSeverity {
    LOW,
    MEDIUM,
    HIGH;

    public static OperatorIncidentSeverity fromDomain(IncidentSeverity severity) {
        if (severity == null) {
            return LOW;
        }
        return switch (severity) {
            case LOW -> LOW;
            case MEDIUM -> MEDIUM;
            case HIGH, CRITICAL -> HIGH;
        };
    }

    public IncidentSeverity toDomain() {
        return switch (this) {
            case LOW -> IncidentSeverity.LOW;
            case MEDIUM -> IncidentSeverity.MEDIUM;
            case HIGH -> IncidentSeverity.HIGH;
        };
    }
}
