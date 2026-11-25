package vn.edu.bku.smart_emobility_backend.common.store;

import java.util.List;
import java.util.Optional;
import vn.edu.bku.smart_emobility_backend.common.domain.Reservation;

public interface ReservationDataStore {
    List<Reservation> listAll();
    Optional<Reservation> findById(String reservationId);
}
