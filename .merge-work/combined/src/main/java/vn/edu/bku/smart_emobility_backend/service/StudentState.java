package vn.edu.bku.smart_emobility_backend.service;

import java.time.Instant;
import java.util.function.Consumer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.bku.smart_emobility_backend.common.domain.IdentifiedEntity;
import vn.edu.bku.smart_emobility_backend.common.store.*;

@Component
public record StudentState(InMemoryDataStore store, StateCoordinator coordinator) {
    public <T extends IdentifiedEntity> T get(Class<T> type, String id) {
        if (id == null || id.isBlank()) throw bad("Missing " + type.getSimpleName() + " ID");
        return store.findById(type, id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, type.getSimpleName() + " not found"));
    }
    public void change(long version, Consumer<InMemoryDataStore> change) {
        if (!coordinator.commit(version, change)) throw new StudentFailure(HttpStatus.CONFLICT,"OPTIMISTIC_LOCK_CONFLICT","State changed, reload and retry");
    }
    static void window(Instant start, Instant end) {
        if (start == null || end == null || !start.isBefore(end)) throw bad("End must follow start");
    }
    static boolean overlaps(Instant start, Instant end, Instant otherStart, Instant otherEnd) {
        return start.isBefore(otherEnd) && otherStart.isBefore(end);
    }
    static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    static ResponseStatusException invalid(String message) { return new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, message); }
}
