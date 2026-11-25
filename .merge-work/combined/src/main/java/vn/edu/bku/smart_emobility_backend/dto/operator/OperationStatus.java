package vn.edu.bku.smart_emobility_backend.dto.operator;

public enum OperationStatus {
    PENDING,
    RECONCILING,
    SUCCEEDED,
    FAILED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED;
    }
}
