package vn.edu.bku.smart_emobility_backend.controller.operator;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.bku.smart_emobility_backend.common.error.ApiException;
import vn.edu.bku.smart_emobility_backend.common.error.ApiException.FieldErrorDetail;
import vn.edu.bku.smart_emobility_backend.common.security.OperatorAccessGuard;
import vn.edu.bku.smart_emobility_backend.common.security.OperatorAccessGuard.AuthenticatedActor;
import vn.edu.bku.smart_emobility_backend.common.web.QueryValidationUtils;
import vn.edu.bku.smart_emobility_backend.dto.operator.*;
import vn.edu.bku.smart_emobility_backend.dto.operator.OperatorIncidentDTO.IncidentListResponseDTO;
import vn.edu.bku.smart_emobility_backend.service.operator.IncidentService;
import vn.edu.bku.smart_emobility_backend.service.operator.IncidentService.IncidentActionResponse;

@RestController
@RequestMapping("/api/operator/incidents")
public class IncidentController {
    private final IncidentService incidentService;
    private final OperatorAccessGuard accessGuard;

    public IncidentController(
            IncidentService incidentService,
            OperatorAccessGuard accessGuard) {
        this.incidentService = incidentService;
        this.accessGuard = accessGuard;
    }

    @GetMapping
    public ResponseEntity<IncidentListResponseDTO> listIncidents(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String hubId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            HttpServletRequest request) {
        accessGuard.requireOperator(request);
        QueryValidationUtils.validateQueryParams(
                request, Set.of("status", "severity", "hubId", "resourceType", "resourceId"));

        OperatorIncidentStatus parsedStatus = QueryValidationUtils.parseEnumParam(
                OperatorIncidentStatus.class, "status", status);
        OperatorIncidentSeverity parsedSeverity = QueryValidationUtils.parseEnumParam(
                OperatorIncidentSeverity.class, "severity", severity);
        ResourceType parsedType = QueryValidationUtils.parseEnumParam(
                ResourceType.class, "resourceType", resourceType);
        if (parsedType == ResourceType.PARKING_SPACE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "resourceType của incident chỉ cho phép VEHICLE hoặc CHARGING_PORT",
                    List.of(new FieldErrorDetail("resourceType", "Không hợp lệ")));
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(incidentService.listIncidents(
                        parsedStatus, parsedSeverity, hubId, parsedType, resourceId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OperatorIncidentDTO> getIncident(
            @PathVariable String id,
            HttpServletRequest request) {
        accessGuard.requireOperator(request);
        QueryValidationUtils.validateQueryParams(request, Set.of());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(incidentService.getIncident(id));
    }

    @PostMapping
    public ResponseEntity<OperatorIncidentDTO> createIncident(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) CreateIncidentRequestDTO requestBody,
            HttpServletRequest request) {
        AuthenticatedActor actor = accessGuard.requireOperator(request);
        QueryValidationUtils.validateQueryParams(request, Set.of());
        OperatorIncidentDTO created = incidentService.createIncident(
                actor.accountId(), idempotencyKey, requestBody);
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(created);
    }

    @PostMapping("/{id}/maintenance")
    public ResponseEntity<Object> sendToMaintenance(
            @PathVariable String id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) IncidentMaintenanceRequestDTO requestBody,
            HttpServletRequest request) {
        AuthenticatedActor actor = accessGuard.requireOperator(request);
        QueryValidationUtils.validateQueryParams(request, Set.of());
        IncidentActionResponse res = incidentService.sendToMaintenance(
                actor.accountId(), idempotencyKey, id, requestBody);
        return ResponseEntity.status(res.status())
                .cacheControl(CacheControl.noStore())
                .body(res.body());
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<Object> resolveIncident(
            @PathVariable String id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) IncidentResolveRequestDTO requestBody,
            HttpServletRequest request) {
        AuthenticatedActor actor = accessGuard.requireOperator(request);
        QueryValidationUtils.validateQueryParams(request, Set.of());
        IncidentActionResponse res = incidentService.resolveIncident(
                actor.accountId(), idempotencyKey, id, requestBody);
        return ResponseEntity.status(res.status())
                .cacheControl(CacheControl.noStore())
                .body(res.body());
    }

    @PostMapping("/{id}/dismiss")
    public ResponseEntity<OperatorIncidentDTO> dismissIncident(
            @PathVariable String id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) IncidentDismissRequestDTO requestBody,
            HttpServletRequest request) {
        AuthenticatedActor actor = accessGuard.requireOperator(request);
        QueryValidationUtils.validateQueryParams(request, Set.of());
        OperatorIncidentDTO dismissed = incidentService.dismissIncident(
                actor.accountId(), idempotencyKey, id, requestBody);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(dismissed);
    }
}
