package vn.edu.bku.smart_emobility_backend.common.mail;

import java.time.Instant;

public interface RecoveryMailSender {

    void sendRecoveryCode(String email, String code, Instant expiresAt);
}
