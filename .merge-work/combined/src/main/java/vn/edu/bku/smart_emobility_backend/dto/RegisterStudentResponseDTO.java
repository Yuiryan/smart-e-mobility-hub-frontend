package vn.edu.bku.smart_emobility_backend.dto;

import java.time.Instant;

import vn.edu.bku.smart_emobility_backend.common.domain.AccountRole;
import vn.edu.bku.smart_emobility_backend.common.domain.AccountStatus;

public record RegisterStudentResponseDTO(
        String accountId,
        String studentId,
        String email,
        AccountRole role,
        AccountStatus status,
        Instant createdAt
) {
}
