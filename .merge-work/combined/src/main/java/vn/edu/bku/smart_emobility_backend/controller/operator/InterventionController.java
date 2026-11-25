package vn.edu.bku.smart_emobility_backend.controller.operator;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.bku.smart_emobility_backend.common.security.OperatorAccessGuard;
import vn.edu.bku.smart_emobility_backend.common.security.OperatorAccessGuard.AuthenticatedActor;
import vn.edu.bku.smart_emobility_backend.common.web.QueryValidationUtils;
import vn.edu.bku.smart_emobility_backend.dto.operator.AuditLogDTO.AuditLogListResponseDTO;
import vn.edu.bku.smart_emobility_backend.dto.operator.AuditOutcome;
import vn.edu.bku.smart_emobility_backend.dto.operator.InterventionRequestDTO;
import vn.edu.bku.smart_emobility_backend.dto.operator.PendingEnvelopeDTO;
import vn.edu.bku.smart_emobility_backend.dto.operator.ResourceType;
import vn.edu.bku.smart_emobility_backend.service.operator.AuditLogService;
import vn.edu.bku.smart_emobility_backend.service.operator.InterventionService;

@RestController
@RequestMapping("/api/operator")
public class InterventionController {
    private final InterventionService interventionService;
    private final AuditLogService auditLogService;
    private final OperatorAccessGuard accessGuard;

    public InterventionController(
            InterventionService interventionService,
            AuditLogService auditLogService,
            OperatorAccessGuard accessGuard) {
        this.interventionService = interventionService;
        this.auditLogService = auditLogService;
        this.accessGuard = accessGuard;
    }

    @PostMapping("/interventions")
    public ResponseEntity<PendingEnvelopeDTO> executeIntervention(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) InterventionRequestDTO requestBody,
            HttpServletRequest request) {
        AuthenticatedActor actor = accessGuard.requireOperator(request);
        QueryValidationUtils.validateQueryParams(request, Set.of());
        PendingEnvelopeDTO envelope = interventionService.executeIntervention(
                actor.accountId(), idempotencyKey, requestBody);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .cacheControl(CacheControl.noStore())
                .body(envelope);
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<AuditLogListResponseDTO> listAuditLogs(
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) String operationId,
            @RequestParam(required = false) String outcome,
            HttpServletRequest request) {
        accessGuard.requireOperator(request);
        QueryValidationUtils.validateQueryParams(
                request, Set.of("resourceType", "resourceId", "operationId", "outcome"));

        ResourceType parsedType = QueryValidationUtils.parseEnumParam(
                ResourceType.class, "resourceType", resourceType);
        AuditOutcome parsedOutcome = QueryValidationUtils.parseEnumParam(
                AuditOutcome.class, "outcome", outcome);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(auditLogService.listAuditLogs(parsedType, resourceId, operationId, parsedOutcome));
    }
}
