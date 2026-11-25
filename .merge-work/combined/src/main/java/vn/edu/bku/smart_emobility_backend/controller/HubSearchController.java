package vn.edu.bku.smart_emobility_backend.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import jakarta.validation.Valid;
import vn.edu.bku.smart_emobility_backend.common.domain.SearchCriteria;
import vn.edu.bku.smart_emobility_backend.dto.HubSearchResultDTO;
import vn.edu.bku.smart_emobility_backend.dto.SearchHubsRequestDTO;
import vn.edu.bku.smart_emobility_backend.dto.SearchHubsResponseDTO;
import vn.edu.bku.smart_emobility_backend.service.HubSearchService;

@RestController
@RequestMapping("/api/search/hubs")
public class HubSearchController {
    private final HubSearchService hubSearchService;

    public HubSearchController(HubSearchService hubSearchService) {
        this.hubSearchService = hubSearchService;
    }

    @PostMapping
    public SearchHubsResponseDTO searchNearbyHubs(@Valid @RequestBody SearchHubsRequestDTO request) {
        SearchCriteria criteria = new SearchCriteria(request.origin(), request.destination(),
                request.radiusKm());
        List<HubSearchResultDTO> hubs = hubSearchService.findSuitableHubs(criteria);
        return new SearchHubsResponseDTO(hubs);
    }

}
