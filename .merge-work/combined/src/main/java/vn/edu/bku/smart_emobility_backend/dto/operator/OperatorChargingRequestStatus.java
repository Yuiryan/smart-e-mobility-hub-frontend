package vn.edu.bku.smart_emobility_backend.dto.operator;

import vn.edu.bku.smart_emobility_backend.common.domain.ChargingRequestStatus;

public enum OperatorChargingRequestStatus {
    WAITING,
    SCHEDULED,
    CHARGING,
    COMPLETED,
    CANCELLED;

    public static OperatorChargingRequestStatus fromDomain(ChargingRequestStatus status) {
        if (status == null) {
            return WAITING;
        }
        return switch (status) {
            case PENDING -> WAITING;
            case SCHEDULED -> SCHEDULED;
            case CHARGING -> CHARGING;
            case COMPLETED -> COMPLETED;
            case CANCEL_PENDING, CANCELLED -> CANCELLED;
        };
    }

    public ChargingRequestStatus toDomain() {
        return switch (this) {
            case WAITING -> ChargingRequestStatus.PENDING;
            case SCHEDULED -> ChargingRequestStatus.SCHEDULED;
            case CHARGING -> ChargingRequestStatus.CHARGING;
            case COMPLETED -> ChargingRequestStatus.COMPLETED;
            case CANCELLED -> ChargingRequestStatus.CANCELLED;
        };
    }
}
