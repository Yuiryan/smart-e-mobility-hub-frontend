package vn.edu.bku.smart_emobility_backend.dto.operator;

import java.util.List;

public record AuditLogDTO(
        String auditId,
        String actorId,
        ResourceType resourceType,
        String resourceId,
        String action,
        String reason,
        String createdAt,
        String updatedAt,
        String operationId,
        AuditOutcome outcome,
        String errorCode) {

    public record AuditLogListResponseDTO(
            List<AuditLogDTO> items,
            boolean truncated) {
        public AuditLogListResponseDTO {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }
}
