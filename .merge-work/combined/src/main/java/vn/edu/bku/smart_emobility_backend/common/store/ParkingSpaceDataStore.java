package vn.edu.bku.smart_emobility_backend.common.store;

import java.util.List;
import vn.edu.bku.smart_emobility_backend.common.domain.ParkingSpace;

public interface ParkingSpaceDataStore {
    List<ParkingSpace> listByHub(String hubId);
}
