package vn.edu.bku.smart_emobility_backend.common.store;

import java.util.List;
import java.util.Optional;
import vn.edu.bku.smart_emobility_backend.common.domain.Vehicle;

public interface VehicleDataStore {
    List<Vehicle> listByHub(String hubId);
    Optional<Vehicle> findById(String vehicleId);
}
