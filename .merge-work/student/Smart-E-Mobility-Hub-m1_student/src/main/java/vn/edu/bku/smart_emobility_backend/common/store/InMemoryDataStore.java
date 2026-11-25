package vn.edu.bku.smart_emobility_backend.common.store;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import vn.edu.bku.smart_emobility_backend.common.domain.*;

@Component
public class InMemoryDataStore {
    private final ConcurrentHashMap<Class<?>, ConcurrentHashMap<String, IdentifiedEntity>> tables =
            new ConcurrentHashMap<>();
    private long version;

    public synchronized <T extends IdentifiedEntity> void put(T entity) {
        tables.computeIfAbsent(entity.getClass(), ignored -> new ConcurrentHashMap<>())
                .put(entity.id(), entity);
    }

    public synchronized <T extends IdentifiedEntity> Optional<T> findById(Class<T> type, String id) {
        return Optional.ofNullable(tables.computeIfAbsent(type, ignored -> new ConcurrentHashMap<>())
                .get(id)).map(type::cast);
    }

    public synchronized <T extends IdentifiedEntity> List<T> findAll(Class<T> type) {
        return tables.computeIfAbsent(type, ignored -> new ConcurrentHashMap<>()).values()
                .stream().map(type::cast).toList();
    }

    public synchronized long version() { return version; }

    public synchronized StoreSnapshot snapshot() {
        return new StoreSnapshot(version, findAll(Account.class), findAll(Student.class), findAll(Session.class),
                findAll(MobilityHub.class), findAll(Vehicle.class), findAll(ParkingSpace.class),
                findAll(Reservation.class), findAll(ParkingReservation.class), findAll(Ride.class),
                findAll(ChargingPort.class), findAll(ChargingRequest.class),
                findAll(ChargingAllocation.class), findAll(DispatchOrder.class),
                findAll(Incident.class), findAll(StudentOperation.class), findAll(ChargingSession.class));
    }

    public synchronized void restore(StoreSnapshot snapshot) {
        tables.clear();
        addAll(snapshot.accounts()); addAll(snapshot.students()); addAll(snapshot.sessions()); addAll(snapshot.hubs());
        addAll(snapshot.vehicles()); addAll(snapshot.parkingSpaces());
        addAll(snapshot.reservations()); addAll(snapshot.parkingReservations());
        addAll(snapshot.rides()); addAll(snapshot.chargingPorts()); addAll(snapshot.chargingRequests());
        addAll(snapshot.chargingAllocations()); addAll(snapshot.dispatchOrders());
        addAll(snapshot.incidents()); addAll(snapshot.studentOperations()); addAll(snapshot.chargingSessions());
        version = snapshot.version();
    }

    synchronized void setVersion(long value) { version = value; }

    private <T extends IdentifiedEntity> void addAll(List<T> entities) {
        entities.forEach(this::put);
    }
}
