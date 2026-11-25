package vn.edu.bku.smart_emobility_backend.dto.operator;

import vn.edu.bku.smart_emobility_backend.common.domain.DispatchOrderStatus;

public enum OperatorDispatchStatus {
    IN_TRANSIT,
    COMPLETED,
    CANCELLED;

    public static OperatorDispatchStatus fromDomain(DispatchOrderStatus status) {
        if (status == null) {
            return IN_TRANSIT;
        }
        return switch (status) {
            case COMPLETED -> COMPLETED;
            case CANCELLED -> CANCELLED;
            default -> IN_TRANSIT;
        };
    }

    public DispatchOrderStatus toDomain() {
        return switch (this) {
            case IN_TRANSIT -> DispatchOrderStatus.IN_TRANSIT;
            case COMPLETED -> DispatchOrderStatus.COMPLETED;
            case CANCELLED -> DispatchOrderStatus.CANCELLED;
        };
    }
}
