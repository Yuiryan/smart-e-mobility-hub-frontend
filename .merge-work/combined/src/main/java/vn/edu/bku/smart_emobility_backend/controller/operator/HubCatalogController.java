package vn.edu.bku.smart_emobility_backend.controller.operator;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.bku.smart_emobility_backend.common.security.OperatorAccessGuard;
import vn.edu.bku.smart_emobility_backend.common.web.QueryValidationUtils;
import vn.edu.bku.smart_emobility_backend.dto.operator.HubSummaryDTO;
import vn.edu.bku.smart_emobility_backend.dto.operator.OperatorParkingSpaceDTO;
import vn.edu.bku.smart_emobility_backend.dto.operator.SnapshotListResponseDTO;
import vn.edu.bku.smart_emobility_backend.service.operator.MonitoringAggregator;

@RestController
@RequestMapping("/api/hubs")
public class HubCatalogController {
    private final MonitoringAggregator monitoringAggregator;
    private final OperatorAccessGuard accessGuard;

    public HubCatalogController(
            MonitoringAggregator monitoringAggregator,
            OperatorAccessGuard accessGuard) {
        this.monitoringAggregator = monitoringAggregator;
        this.accessGuard = accessGuard;
    }

    @GetMapping
    public ResponseEntity<SnapshotListResponseDTO<HubSummaryDTO>> listHubs(HttpServletRequest request) {
        accessGuard.requireAuthenticated(request);
        QueryValidationUtils.validateQueryParams(request, Set.of());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(monitoringAggregator.listHubSummaries());
    }

    @GetMapping("/{hubId}/spaces")
    public ResponseEntity<SnapshotListResponseDTO<OperatorParkingSpaceDTO>> listHubSpaces(
            @PathVariable String hubId,
            HttpServletRequest request) {
        accessGuard.requireAuthenticated(request);
        QueryValidationUtils.validateQueryParams(request, Set.of());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(monitoringAggregator.listHubSpaces(hubId));
    }
}
