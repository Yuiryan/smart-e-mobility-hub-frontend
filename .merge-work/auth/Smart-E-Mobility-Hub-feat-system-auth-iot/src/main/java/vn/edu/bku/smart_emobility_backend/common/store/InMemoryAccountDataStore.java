package vn.edu.bku.smart_emobility_backend.common.store;

import java.util.Optional;
import vn.edu.bku.smart_emobility_backend.common.domain.RecoveryCode;
import vn.edu.bku.smart_emobility_backend.common.domain.RecoverySession;
import org.springframework.stereotype.Component;
import vn.edu.bku.smart_emobility_backend.common.domain.Account;
import vn.edu.bku.smart_emobility_backend.common.domain.Session;
import java.util.List;

@Component
public class InMemoryAccountDataStore implements AccountDataStore {

    private final InMemoryDataStore dataStore;

    public InMemoryAccountDataStore(InMemoryDataStore dataStore) {
        this.dataStore = dataStore;
    }

    @Override
    public Optional<Account> findByEmail(String email) {
        return dataStore.findAll(Account.class)
                .stream()
                .filter(account -> account.email().equalsIgnoreCase(email))
                .findFirst();
    }
    
    @Override
    public void saveAccount(Account account) {
        dataStore.put(account);
    }

    @Override
    public Optional<Session> findSession(String sessionId) {
        return dataStore.findById(Session.class, sessionId);
    }

    @Override
    public void saveSession(Session session) {
        dataStore.put(session);
    }

    @Override
    public List<Session> findSessionsByAccountId(String accountId) {
        return dataStore.findAll(Session.class)
                .stream()
                .filter(session -> session.accountId().equals(accountId))
                .toList();
    }

    @Override
    public void deleteSession(String sessionId) {
        dataStore.remove(Session.class, sessionId);
    }

    @Override
    public Optional<Account> findById(String accountId) {
        return dataStore.findById(Account.class, accountId);
    }


    @Override
    public Optional<RecoveryCode> findRecoveryCode(String accountId) {
        return dataStore.findAll(RecoveryCode.class)
                .stream()
                .filter(code -> code.accountId().equals(accountId))
                .findFirst();
    }

    @Override
    public void saveRecoveryCode(RecoveryCode code) {
        dataStore.put(code);
    }

    @Override
    public void deleteRecoveryCode(String tokenId) {
        dataStore.remove(RecoveryCode.class, tokenId);
    }

    @Override
    public Optional<RecoverySession> findRecoverySession(String tokenHash) {
        return dataStore.findById(RecoverySession.class, tokenHash);
    }

    @Override
    public void saveRecoverySession(RecoverySession session) {
        dataStore.put(session);
    }

    @Override
    public List<RecoverySession> findRecoverySessionsByAccountId(String accountId) {
        return dataStore.findAll(RecoverySession.class)
                .stream()
                .filter(session -> session.accountId().equals(accountId))
                .toList();
    }

    @Override
    public void deleteRecoverySession(String tokenHash) {
        dataStore.remove(RecoverySession.class, tokenHash);
    }
}
