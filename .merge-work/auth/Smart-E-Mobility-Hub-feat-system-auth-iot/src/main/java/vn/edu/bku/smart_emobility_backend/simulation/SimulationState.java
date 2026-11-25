package vn.edu.bku.smart_emobility_backend.simulation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import vn.edu.bku.smart_emobility_backend.common.domain.*;
import vn.edu.bku.smart_emobility_backend.common.store.*;

public final class SimulationState {
    final InMemoryDataStore isolatedStore;
    final Map<String, Hub> hubs = new TreeMap<>();
    final List<String> missingData = new ArrayList<>();
    final long sourceVersion;

    SimulationState(StoreSnapshot snapshot) {
        isolatedStore = new InMemoryDataStore();
        isolatedStore.restore(snapshot);
        sourceVersion = snapshot.version();
    }

    public StoreSnapshot snapshot() {
        return isolatedStore.snapshot();
    }

    static final class Hub {
        final MobilityHub source;
        final List<Vehicle> available = new ArrayList<>();
        final int capacity;
        boolean operational;
        int occupied;
        double peakPct;

        Hub(MobilityHub source, int occupied, boolean operational) {
            this.source = source;
            this.capacity = source.parkingCapacity();
            this.occupied = occupied;
            this.operational = operational;
            observe();
        }

        void observe() {
            peakPct = Math.max(peakPct, occupied * 100.0 / capacity);
        }
    }

    static final class Vehicle {
        final String id;
        double battery;

        Vehicle(String id, double battery) {
            this.id = id;
            this.battery = battery;
        }

        void simulateTrip(double distanceKm, double consumptionPercentPerKm) {
            battery = Math.max(0, battery - distanceKm * consumptionPercentPerKm);
        }
    }
}
