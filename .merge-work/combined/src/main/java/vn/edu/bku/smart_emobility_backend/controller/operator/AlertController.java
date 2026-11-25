package vn.edu.bku.smart_emobility_backend.controller.operator;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.bku.smart_emobility_backend.common.security.OperatorAccessGuard;
import vn.edu.bku.smart_emobility_backend.common.web.QueryValidationUtils;
import vn.edu.bku.smart_emobility_backend.dto.operator.AlertDimension;
import vn.edu.bku.smart_emobility_backend.dto.operator.AlertSeverity;
import vn.edu.bku.smart_emobility_backend.dto.operator.OverloadAlertDTO.AlertListResponseDTO;
import vn.edu.bku.smart_emobility_backend.service.operator.OverloadMonitor;

@RestController
@RequestMapping("/api/operator")
public class AlertController {
    private final OverloadMonitor overloadMonitor;
    private final OperatorAccessGuard accessGuard;

    public AlertController(
            OverloadMonitor overloadMonitor,
            OperatorAccessGuard accessGuard) {
        this.overloadMonitor = overloadMonitor;
        this.accessGuard = accessGuard;
    }

    @GetMapping("/alerts")
    public ResponseEntity<AlertListResponseDTO> listAlerts(
            @RequestParam(required = false) String hubId,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String dimension,
            HttpServletRequest request) {
        accessGuard.requireOperator(request);
        QueryValidationUtils.validateQueryParams(request, Set.of("hubId", "severity", "dimension"));

        AlertSeverity parsedSeverity = QueryValidationUtils.parseEnumParam(
                AlertSeverity.class, "severity", severity);
        AlertDimension parsedDimension = QueryValidationUtils.parseEnumParam(
                AlertDimension.class, "dimension", dimension);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(overloadMonitor.listAlerts(hubId, parsedSeverity, parsedDimension));
    }
}
