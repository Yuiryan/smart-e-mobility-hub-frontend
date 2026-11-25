package vn.edu.bku.smart_emobility_backend.dto;

import vn.edu.bku.smart_emobility_backend.common.domain.AccountRole;

public record CurrentAccountResponseDTO(
        String accountId, String studentId, String email, AccountRole role) {
}
