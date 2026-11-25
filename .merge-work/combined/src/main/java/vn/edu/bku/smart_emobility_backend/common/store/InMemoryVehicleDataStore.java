package vn.edu.bku.smart_emobility_backend.common.store;

import java.util.List;
import java.util.stream.Collectors;
import vn.edu.bku.smart_emobility_backend.common.domain.StudentOperation;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import vn.edu.bku.smart_emobility_backend.common.domain.Vehicle;

@Repository
public class InMemoryVehicleDataStore implements VehicleDataStore {
    private final InMemoryDataStore dataStore;

    public InMemoryVehicleDataStore(InMemoryDataStore dataStore) {
        this.dataStore = dataStore;
    }

    @Override
    public List<Vehicle> listByHub(String hubId) {
        var claimed = dataStore.findAll(StudentOperation.class).stream()
                .filter(op -> op.status() == StudentOperation.Status.PENDING)
                .map(StudentOperation::vehicleId).collect(Collectors.toSet());
        return dataStore.findAll(Vehicle.class).stream().filter(vehicle -> !claimed.contains(vehicle.id()))
                .filter(vehicle -> hubId.equals(vehicle.hubId()))
                .toList();
    }

    @Override
    public Optional<Vehicle> findById(String vehicleId) {
        return dataStore.findById(Vehicle.class, vehicleId);
    }
}
