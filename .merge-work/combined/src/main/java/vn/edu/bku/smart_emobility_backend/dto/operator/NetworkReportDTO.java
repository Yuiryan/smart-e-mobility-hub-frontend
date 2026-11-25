package vn.edu.bku.smart_emobility_backend.dto.operator;

import java.util.List;

public record NetworkReportDTO(
        String generatedAt,
        long stateVersion,
        List<HubMetricsDTO> hubs) {
    public NetworkReportDTO {
        hubs = hubs == null ? List.of() : List.copyOf(hubs);
    }
}
