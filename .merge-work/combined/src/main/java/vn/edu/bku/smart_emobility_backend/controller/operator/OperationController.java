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
import vn.edu.bku.smart_emobility_backend.dto.operator.OperationDTO;
import vn.edu.bku.smart_emobility_backend.service.operator.OperationOrchestrator;

public class OperationController {
    private final OperationOrchestrator operationOrchestrator;
    private final OperatorAccessGuard accessGuard;

    public OperationController(
            OperationOrchestrator operationOrchestrator,
            OperatorAccessGuard accessGuard) {
        this.operationOrchestrator = operationOrchestrator;
        this.accessGuard = accessGuard;
    }

    @GetMapping("/{operationId}")
    public ResponseEntity<OperationDTO> getOperation(
            @PathVariable String operationId,
            HttpServletRequest request) {
        accessGuard.requireOperator(request);
        QueryValidationUtils.validateQueryParams(request, Set.of());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(operationOrchestrator.getOperation(operationId));
    }
}
