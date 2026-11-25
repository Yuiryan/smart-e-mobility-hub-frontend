package vn.edu.bku.smart_emobility_backend.common.store;

import java.util.List;
import org.springframework.stereotype.Repository;
import vn.edu.bku.smart_emobility_backend.common.domain.MobilityHub;
import vn.edu.bku.smart_emobility_backend.common.domain.SearchCriteria;

@Repository
public class InMemoryHubDataStore implements HubDataStore {
    private final InMemoryDataStore dataStore;

    public InMemoryHubDataStore(InMemoryDataStore dataStore) {
        this.dataStore = dataStore;
    }

    @Override
    public List<MobilityHub> findNearby(SearchCriteria criteria) {
        return dataStore.findAll(MobilityHub.class).stream()
                .filter(hub -> hub.distanceTo(criteria.referencePoint()) <= criteria.radiusKm())
                .toList();
    }
}
