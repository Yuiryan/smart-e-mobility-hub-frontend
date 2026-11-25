package vn.edu.bku.smart_emobility_backend.dto.operator;

public record HubReportDTO(
        String generatedAt,
        long stateVersion,
        HubMetricsDTO hub) {
}
