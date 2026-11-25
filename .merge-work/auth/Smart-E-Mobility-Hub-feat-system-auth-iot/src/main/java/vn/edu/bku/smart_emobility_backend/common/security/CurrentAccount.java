package vn.edu.bku.smart_emobility_backend.common.security;

import java.security.Principal;

import vn.edu.bku.smart_emobility_backend.common.domain.AccountRole;

public record CurrentAccount(
        String accountId, String studentId, String email,
        AccountRole role, String sessionId) implements Principal {

    @Override
    public String getName() {
        return accountId;
    }

    @Override
    public String toString() {
        return "CurrentAccount[accountId=" + accountId + ", role=" + role + "]";
    }
}
