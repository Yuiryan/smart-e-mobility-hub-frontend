package vn.edu.bku.smart_emobility_backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.bku.smart_emobility_backend.common.security.CurrentAccount;
import vn.edu.bku.smart_emobility_backend.common.domain.AccountRole;
import vn.edu.bku.smart_emobility_backend.common.exception.ResourceNotFoundException;
import vn.edu.bku.smart_emobility_backend.common.security.OperatorAccessGuard;
import vn.edu.bku.smart_emobility_backend.common.web.QueryValidationUtils;
import vn.edu.bku.smart_emobility_backend.dto.OperationResponseDTO;
import vn.edu.bku.smart_emobility_backend.service.OperationService;
import vn.edu.bku.smart_emobility_backend.service.StudentAccess;
import vn.edu.bku.smart_emobility_backend.service.StudentRideCommands;
import vn.edu.bku.smart_emobility_backend.service.operator.OperationOrchestrator;

@RestController
@RequestMapping("/api/operations")
public class OperationController {
    private final OperationService operations;
    private final OperationOrchestrator operatorOperations;
    private final OperatorAccessGuard operatorAccess;
    private final StudentAccess studentAccess;
    private final StudentRideCommands studentOperations;
    private final RideResponse rideResponses;

    public OperationController(OperationService operations, OperationOrchestrator operatorOperations,
            OperatorAccessGuard operatorAccess, StudentAccess studentAccess,
            StudentRideCommands studentOperations, RideResponse rideResponses) {
        this.operations = operations;
        this.operatorOperations = operatorOperations;
        this.operatorAccess = operatorAccess;
        this.studentAccess = studentAccess;
        this.studentOperations = studentOperations;
        this.rideResponses = rideResponses;
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> find(@PathVariable String id,
            @AuthenticationPrincipal CurrentAccount account,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            HttpServletRequest request) {
        if (studentOperations.exists(id)) {
            QueryValidationUtils.validateQueryParams(request, Set.of());
            return ResponseEntity.ok().header("Cache-Control", "no-store")
                    .body(rideResponses.operation(
                            studentOperations.lookup(studentAccess.fromHeader(authorization), id)));
        }
        if (operations.exists(id)) {
            var result = operations.find(id, account.accountId());
            QueryValidationUtils.validateQueryParams(request, Set.of());
            return ResponseEntity.ok().header("Cache-Control", "no-store").body(result);
        }
        QueryValidationUtils.validateQueryParams(request, Set.of());
        if (account != null && account.role() != AccountRole.OPERATOR) {
            throw new ResourceNotFoundException("Operation was not found");
        }
        operatorAccess.requireOperator(request);
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(operatorOperations.getOperation(id));
    }
}
