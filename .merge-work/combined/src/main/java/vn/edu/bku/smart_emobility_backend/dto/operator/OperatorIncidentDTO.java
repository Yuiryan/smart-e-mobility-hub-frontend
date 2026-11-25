package vn.edu.bku.smart_emobility_backend.dto.operator;

import java.util.List;

public record OperatorIncidentDTO(
        String incidentId,
        ResourceType resourceType,
        String resourceId,
        String hubId,
        OperatorIncidentSeverity severity,
        OperatorIncidentStatus status,
        String description,
        IncidentSource source,
        String actorId,
        String createdAt,
        String updatedAt,
        String resolvedAt,
        String dismissedAt,
        String resolutionNote,
        String dismissNote,
        long version,
        long resourceVersion,
        String pendingOperationId,
        List<IncidentAction> allowedActions) {
    public OperatorIncidentDTO {
        allowedActions = allowedActions == null ? List.of() : List.copyOf(allowedActions);
    }

    public record IncidentListResponseDTO(List<OperatorIncidentDTO> items) {
        public IncidentListResponseDTO {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }
}
