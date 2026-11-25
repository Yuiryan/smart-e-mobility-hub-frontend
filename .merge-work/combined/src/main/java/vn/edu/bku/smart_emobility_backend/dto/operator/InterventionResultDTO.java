package vn.edu.bku.smart_emobility_backend.dto.operator;

import java.util.List;

public record InterventionResultDTO(
        String interventionId,
        String auditId,
        ResourceType resourceType,
        String resourceId,
        InterventionAction action,
        String status,
        long resourceVersion,
        String actorId,
        String completedAt,
        List<AffectedResourceDTO> affectedResources) {
    public InterventionResultDTO {
        affectedResources = affectedResources == null ? List.of() : List.copyOf(affectedResources);
    }

    public record AffectedResourceDTO(
            String resourceType,
            String resourceId,
            long version) {
    }
}
