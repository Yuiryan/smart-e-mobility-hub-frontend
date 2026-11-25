package vn.edu.bku.smart_emobility_backend.dto;

import java.util.List;

public record SearchHubsResponseDTO(List<HubSearchResultDTO> hubs) {
    public SearchHubsResponseDTO {
        hubs = List.copyOf(hubs);
    }
}
