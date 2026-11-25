package vn.edu.bku.smart_emobility_backend.common.store;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DataStoreBootstrap implements ApplicationRunner {
    private final InMemoryDataStore dataStore;
    private final JsonStore jsonStore;

    public DataStoreBootstrap(InMemoryDataStore dataStore, JsonStore jsonStore) {
        this.dataStore = dataStore;
        this.jsonStore = jsonStore;
    }

    @Override
    public void run(ApplicationArguments args) {
        jsonStore.load().ifPresent(dataStore::restore);
    }
}
