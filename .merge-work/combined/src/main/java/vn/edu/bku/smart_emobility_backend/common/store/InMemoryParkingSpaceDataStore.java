package vn.edu.bku.smart_emobility_backend.common.store;

import java.util.List;
import org.springframework.stereotype.Repository;
import vn.edu.bku.smart_emobility_backend.common.domain.ParkingSpace;

@Repository
public class InMemoryParkingSpaceDataStore implements ParkingSpaceDataStore {
    private final InMemoryDataStore dataStore;

    public InMemoryParkingSpaceDataStore(InMemoryDataStore dataStore) {
        this.dataStore = dataStore;
    }

    @Override
    public List<ParkingSpace> listByHub(String hubId) {
        return dataStore.findAll(ParkingSpace.class).stream()
                .filter(space -> hubId.equals(space.hubId()))
                .toList();
    }
}
