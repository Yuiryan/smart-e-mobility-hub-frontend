package vn.edu.bku.smart_emobility_backend.service;

import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.bku.smart_emobility_backend.common.store.InMemoryDataStore;
import vn.edu.bku.smart_emobility_backend.common.domain.*;

@Service
public record StudentAccess(InMemoryDataStore store, Clock clock) {
    public Student fromHeader(String header) {
        if (header == null || !header.startsWith("Bearer ")) throw unauthorized();
        return fromToken(header.substring(7).trim());
    }
    public Student fromToken(String token) {
        if (token == null || token.isBlank()) throw unauthorized();
        Session session = store.findById(Session.class, token)
                .filter(s -> s.expiresAt() != null && clock.instant().isBefore(s.expiresAt()))
                .orElseThrow(StudentAccess::unauthorized);
        Account account = store.findById(Account.class, session.accountId()).orElseThrow(StudentAccess::unauthorized);
        if (!account.active() || account.role() != AccountRole.STUDENT) throw forbidden();
        return store.findAll(Student.class).stream().filter(s -> account.id().equals(s.accountId()))
                .filter(Student::isAuthenticated).findFirst().orElseThrow(StudentAccess::forbidden);
    }
    static ResponseStatusException unauthorized() { return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired session"); }
    static ResponseStatusException forbidden() { return new ResponseStatusException(HttpStatus.FORBIDDEN, "Student access denied"); }
}
