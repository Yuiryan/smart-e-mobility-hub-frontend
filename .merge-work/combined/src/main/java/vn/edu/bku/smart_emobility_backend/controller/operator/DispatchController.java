package vn.edu.bku.smart_emobility_backend.controller.operator;

import jakarta.servlet.http.HttpServletRequest;
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
import vn.edu.bku.smart_emobility_backend.common.security.OperatorAccessGuard;
import vn.edu.bku.smart_emobility_backend.common.security.OperatorAccessGuard.AuthenticatedActor;
import vn.edu.bku.smart_emobility_backend.common.web.QueryValidationUtils;
import vn.edu.bku.smart_emobility_backend.dto.operator.*;
import vn.edu.bku.smart_emobility_backend.dto.operator.DispatchOrderDTO.DispatchOrderListResponseDTO;
import vn.edu.bku.smart_emobility_backend.service.operator.DispatchService;

@RestController
@RequestMapping("/api/operator")
public class DispatchController {
    private final DispatchService dispatchService;
    private final OperatorAccessGuard accessGuard;

    public DispatchController(
            DispatchService dispatchService,
            OperatorAccessGuard accessGuard) {
        this.dispatchService = dispatchService;
        this.accessGuard = accessGuard;
    }

    @GetMapping("/dispatch-candidates")
    public ResponseEntity<DispatchCandidatesResponseDTO> getDispatchCandidates(
            @RequestParam(required = false) String sourceHubId,
            @RequestParam(required = false) String transportMode,
            HttpServletRequest request) {
        accessGuard.requireOperator(request);
        QueryValidationUtils.validateQueryParams(request, Set.of("sourceHubId", "transportMode"));
        TransportMode parsedMode = QueryValidationUtils.parseEnumParam(
                TransportMode.class, "transportMode", transportMode);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(dispatchService.getDispatchCandidates(sourceHubId, parsedMode));
    }

    @PostMapping("/dispatch-orders")
    public ResponseEntity<DispatchOrderDTO> createDispatchOrder(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) CreateDispatchOrderRequestDTO requestBody,
            HttpServletRequest request) {
        AuthenticatedActor actor = accessGuard.requireOperator(request);
        QueryValidationUtils.validateQueryParams(request, Set.of());
        DispatchOrderDTO created = dispatchService.createDispatchOrder(
                actor.accountId(), idempotencyKey, requestBody);
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(created);
    }

    @GetMapping("/dispatch-orders")
    public ResponseEntity<DispatchOrderListResponseDTO> listDispatchOrders(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String hubId,
            HttpServletRequest request) {
        accessGuard.requireOperator(request);
        QueryValidationUtils.validateQueryParams(request, Set.of("status", "hubId"));
        OperatorDispatchStatus parsedStatus = QueryValidationUtils.parseEnumParam(
                OperatorDispatchStatus.class, "status", status);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(dispatchService.listDispatchOrders(parsedStatus, hubId));
    }

    @GetMapping("/dispatch-orders/{id}")
    public ResponseEntity<DispatchOrderDTO> getDispatchOrder(
            @PathVariable String id,
            HttpServletRequest request) {
        accessGuard.requireOperator(request);
        QueryValidationUtils.validateQueryParams(request, Set.of());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(dispatchService.getDispatchOrder(id));
    }

    @PostMapping("/dispatch-orders/{id}/arrival")
    public ResponseEntity<DispatchOrderDTO> confirmArrival(
            @PathVariable String id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) DispatchVersionRequestDTO requestBody,
            HttpServletRequest request) {
        AuthenticatedActor actor = accessGuard.requireOperator(request);
        QueryValidationUtils.validateQueryParams(request, Set.of());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(dispatchService.confirmArrival(actor.accountId(), idempotencyKey, id, requestBody));
    }

    @PostMapping("/dispatch-orders/{id}/cancel")
    public ResponseEntity<DispatchOrderDTO> cancelDispatchOrder(
            @PathVariable String id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) DispatchVersionRequestDTO requestBody,
            HttpServletRequest request) {
        AuthenticatedActor actor = accessGuard.requireOperator(request);
        QueryValidationUtils.validateQueryParams(request, Set.of());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(dispatchService.cancelDispatchOrder(actor.accountId(), idempotencyKey, id, requestBody));
    }
}
