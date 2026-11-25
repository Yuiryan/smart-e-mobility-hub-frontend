package vn.edu.bku.smart_emobility_backend.dto.operator;

public record PendingEnvelopeDTO(
        String operationId,
        String status,
        String statusUrl,
        int retryAfterSeconds) {
    public static PendingEnvelopeDTO of(String operationId) {
        return new PendingEnvelopeDTO(operationId, "PENDING", "/api/operations/" + operationId, 2);
    }
}
