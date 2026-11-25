package vn.edu.bku.smart_emobility_backend.common.store;

import java.util.Optional;
import vn.edu.bku.smart_emobility_backend.common.domain.RecoveryCode;
import vn.edu.bku.smart_emobility_backend.common.domain.RecoverySession;
import vn.edu.bku.smart_emobility_backend.common.domain.Account;
import vn.edu.bku.smart_emobility_backend.common.domain.Session;
import java.util.List;

public interface AccountDataStore {

    Optional<Account> findByEmail(String email);
    void saveAccount(Account account);
    Optional<Session> findSession(String sessionId);
    void saveSession(Session session);
    List<Session> findSessionsByAccountId(String accountId);
    void deleteSession(String sessionId);
    Optional<Account> findById(String accountId);

    Optional<RecoveryCode> findRecoveryCode(String accountId);
    void saveRecoveryCode(RecoveryCode code);
    void deleteRecoveryCode(String tokenId);

    Optional<RecoverySession> findRecoverySession(String tokenHash);
    void saveRecoverySession(RecoverySession session);
    List<RecoverySession> findRecoverySessionsByAccountId(String accountId);
    void deleteRecoverySession(String tokenHash);
}
