package vn.edu.bku.smart_emobility_backend.dto.operator;

import vn.edu.bku.smart_emobility_backend.common.domain.ChargingPortStatus;

public enum OperatorPortStatus {
    AVAILABLE,
    SCHEDULED,
    CHARGING,
    MAINTENANCE;

    public static OperatorPortStatus fromDomain(ChargingPortStatus status) {
        if (status == null) {
            return AVAILABLE;
        }
        return switch (status) {
            case AVAILABLE -> AVAILABLE;
            case SCHEDULED -> SCHEDULED;
            case CHARGING -> CHARGING;
            case MAINTENANCE -> MAINTENANCE;
        };
    }

    public ChargingPortStatus toDomain() {
        return switch (this) {
            case AVAILABLE -> ChargingPortStatus.AVAILABLE;
            case SCHEDULED -> ChargingPortStatus.SCHEDULED;
            case CHARGING -> ChargingPortStatus.CHARGING;
            case MAINTENANCE -> ChargingPortStatus.MAINTENANCE;
        };
    }
}
