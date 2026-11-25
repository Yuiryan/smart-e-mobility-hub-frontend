package vn.edu.bku.smart_emobility_backend.common.store;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import vn.edu.bku.smart_emobility_backend.common.domain.Reservation;

@Repository
public class InMemoryReservationDataStore implements ReservationDataStore {
    private final InMemoryDataStore dataStore;

    public InMemoryReservationDataStore(InMemoryDataStore dataStore) {
        this.dataStore = dataStore;
    }

    @Override
    public List<Reservation> listAll() {
        return dataStore.findAll(Reservation.class);
    }

    @Override
    public Optional<Reservation> findById(String reservationId) {
        return dataStore.findById(Reservation.class, reservationId);
    }
}
