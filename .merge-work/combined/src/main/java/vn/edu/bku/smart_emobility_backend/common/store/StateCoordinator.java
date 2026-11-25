package vn.edu.bku.smart_emobility_backend.common.store;

import java.util.function.Consumer;
import org.springframework.stereotype.Component;

@Component
public class StateCoordinator {
    private final InMemoryDataStore dataStore;
    private final JsonStore jsonStore;

    public StateCoordinator(InMemoryDataStore dataStore, JsonStore jsonStore) {
        this.dataStore = dataStore;
        this.jsonStore = jsonStore;
    }

    public long currentVersion() {
        return dataStore.version();
    }

    /** Returns false when expectedVersion is stale; successful changes are persisted atomically. */
    public boolean commit(long expectedVersion, Consumer<InMemoryDataStore> change) {
        synchronized (dataStore) {
            if (dataStore.version() != expectedVersion) return false;
            StoreSnapshot previous = dataStore.snapshot();
            try {
                change.accept(dataStore);
                dataStore.setVersion(expectedVersion + 1);
                jsonStore.save(dataStore.snapshot());
                return true;
            } catch (RuntimeException | Error ex) {
                dataStore.restore(previous);
                throw ex;
            }
        }
    }
}
