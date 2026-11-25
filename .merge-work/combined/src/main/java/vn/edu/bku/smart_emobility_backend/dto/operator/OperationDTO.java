package vn.edu.bku.smart_emobility_backend.dto.operator;

import vn.edu.bku.smart_emobility_backend.common.error.ApiErrorResponseDTO.ErrorBody;

public record OperationDTO(
        String operationId,
        OperationKind kind,
        OperationStatus status,
        ResourceType resourceType,
        String resourceId,
        String createdAt,
        String updatedAt,
        OperationResultType resultType,
        Object result,
        ErrorBody error,
        Integer retryAfterSeconds) {
}
