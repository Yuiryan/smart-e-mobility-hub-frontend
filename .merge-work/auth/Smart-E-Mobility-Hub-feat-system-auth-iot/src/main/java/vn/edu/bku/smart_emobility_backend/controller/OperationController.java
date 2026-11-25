package vn.edu.bku.smart_emobility_backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.bku.smart_emobility_backend.common.security.CurrentAccount;
import vn.edu.bku.smart_emobility_backend.dto.OperationResponseDTO;
import vn.edu.bku.smart_emobility_backend.service.OperationService;

@RestController
@RequestMapping("/api/operations")
public class OperationController {
    private final OperationService operations;

    public OperationController(OperationService operations) {
        this.operations = operations;
    }

    @GetMapping("/{id}")
    public ResponseEntity<OperationResponseDTO> find(@PathVariable String id,
            @AuthenticationPrincipal CurrentAccount account) {
        return ResponseEntity.ok().header("Cache-Control", "no-store")
                .body(operations.find(id, account.accountId()));
    }
}
