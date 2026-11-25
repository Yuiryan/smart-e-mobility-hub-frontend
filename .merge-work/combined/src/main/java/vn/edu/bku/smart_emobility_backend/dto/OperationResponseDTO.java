package vn.edu.bku.smart_emobility_backend.dto;

import java.time.Instant;
import java.util.Map;

public record OperationResponseDTO(String operationId, String type, String status,
        Instant createdAt, Instant updatedAt, Map<String, String> result, OperationError error) {
    public OperationResponseDTO {
        result = result == null ? null : Map.copyOf(result);
    }

    public record OperationError(String code, String message) {}
}
