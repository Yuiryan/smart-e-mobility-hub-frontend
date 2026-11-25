package vn.edu.bku.smart_emobility_backend.simulation;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import vn.edu.bku.smart_emobility_backend.common.domain.*;
import vn.edu.bku.smart_emobility_backend.common.exception.SimulationException;
import vn.edu.bku.smart_emobility_backend.common.store.*;
import vn.edu.bku.smart_emobility_backend.iot.IoTGateway;

@Service
@EnableConfigurationProperties(SimulationProperties.class)
public class SimulationEngine {
    private static final double MAX_HOURS = 168.0;
    private static final double MAX_MULTIPLIER = 100.0;
    private static final double MIN_BATTERY = MobilityRules.MIN_RENTAL_BATTERY_PCT;
    private static final double OVERLOAD_PCT = MobilityRules.OVERLOAD_OCCUPANCY_PCT;
    private static final int LOW_VEHICLE_COUNT = MobilityRules.LOW_VEHICLE_COUNT;
    private static final int RESERVE_VEHICLES = LOW_VEHICLE_COUNT + 1;
    private static final double DEMAND_ROUNDING_TOLERANCE = 1e-12;
    private static final double SECONDS_PER_HOUR = 3600.0;
    private final InMemoryDataStore liveStore;
    private final IoTGateway telemetry;
    private final Clock clock;
    private final SimulationProperties properties;

    public SimulationEngine(InMemoryDataStore liveStore, IoTGateway telemetry, Clock clock, SimulationProperties properties) {
        this.liveStore = liveStore;
        this.telemetry = telemetry;
        this.clock = clock;
        this.properties = properties;
    }

    private record RouteKey(String from, String to) {}
    private static final class Route {
        final RouteKey key;
        int count;
        double totalSeconds;
        double totalDistance;
        double carry;
        Route(RouteKey key) { this.key = key; }
        long durationSeconds() { return Math.max(1, (long) Math.ceil(totalSeconds / count)); }
        double distanceKm() { return totalDistance / count; }
    }
    private record Arrival(Instant at, String hubId, SimulationState.Vehicle vehicle) {}
    private record TimeStepResult(long failedBookings, long failedReturns) {}

    public SimulationResult runSimulation(Scenario scenario, String operatorId) {
        validateParameters(scenario);
        Instant now = clock.instant();
        SimulationState state = createStateSnapshot();
        StoreSnapshot snapshot = state.snapshot();
        if (snapshot.accounts().stream().noneMatch(account -> account.id().equals(operatorId)
                && account.role() == AccountRole.OPERATOR && account.isActive())) {
            throw error(403, "ACCESS_DENIED", "Active Operator account required");
        }
        String outage = scenario.parameters().hubOutageId();
        if (outage != null) {
            if (!state.hubs.containsKey(outage)) {
                throw error(404, "RESOURCE_NOT_FOUND", "Unknown or incomplete outage hub");
            }
            state.hubs.get(outage).operational = false;
        }
        List<Route> routes = baseline(snapshot, state, now);
        double totalSeconds = scenario.parameters().simulatedDurationHours() * SECONDS_PER_HOUR;
        double multiplier = scenario.parameters().rentalMultiplier();
        double expected = routes.stream().mapToDouble(route -> route.count * multiplier
                * totalSeconds / properties.lookback().toSeconds()).sum();
        if (!Double.isFinite(expected) || expected > properties.maxGeneratedTrips()) {
            throw error(400, "SIMULATION_TOO_LARGE", "Forecast exceeds configured trip limit");
        }
        Instant end = now.plusSeconds(Math.max(1, (long) Math.ceil(totalSeconds)));
        PriorityQueue<Arrival> arrivals = new PriorityQueue<>(Comparator.comparing(Arrival::at)
                .thenComparing(arrival -> arrival.vehicle().id));
        seedActiveRides(snapshot, state, routes, now, arrivals);
        List<TimeStepResult> steps = new ArrayList<>();
        for (Instant start = now; start.isBefore(end);) {
            Instant next = start.plus(properties.step());
            if (next.isAfter(end)) {
                next = end;
            }
            long failedReturns = arrive(state, arrivals, start);
            long failedBookings = 0;
            double seconds = Duration.between(start, next).toSeconds();
            for (Route route : routes) {
                route.carry += route.count * multiplier * seconds / properties.lookback().toSeconds();
                long requests = (long) Math.floor(route.carry + DEMAND_ROUNDING_TOLERANCE);
                route.carry -= requests;
                for (long request = 0; request < requests; request++) {
                    SimulationState.Hub origin = state.hubs.get(route.key.from());
                    SimulationState.Hub destination = state.hubs.get(route.key.to());
                    if (!origin.operational || !destination.operational || origin.available.isEmpty()) {
                        failedBookings++;
                        continue;
                    }
                    var vehicle = origin.available.removeFirst();
                    origin.occupied--;
                    vehicle.simulateTrip(route.distanceKm(), properties.consumptionPercentPerKm());
                    arrivals.add(new Arrival(start.plusSeconds(route.durationSeconds()), destination.source.id(), vehicle));
                }
            }
            failedReturns += arrive(state, arrivals, next);
            state.hubs.values().forEach(SimulationState.Hub::observe);
            steps.add(new TimeStepResult(failedBookings, failedReturns));
            start = next;
        }
        return aggregateResults(state, steps, now);
    }

    public SimulationState createStateSnapshot() {
        StoreSnapshot snapshot = liveStore.snapshot();
        SimulationState state = new SimulationState(snapshot);
        for (MobilityHub hub : snapshot.hubs().stream().sorted(Comparator.comparing(MobilityHub::id)).toList()) {
            List<ParkingSpace> spaces = snapshot.parkingSpaces().stream().filter(space -> hub.id().equals(space.hubId())).toList();
            if (hub.parkingCapacity() <= 0 || spaces.size() != hub.parkingCapacity()
                    || spaces.stream().anyMatch(space -> space.status() == null)
                    || !validCoordinates(hub)) {
                state.missingData.add("INCOMPLETE_HUB:" + hub.id());
                continue;
            }
            int occupied = (int) spaces.stream().filter(space -> space.status() == ParkingSpaceStatus.OCCUPIED).count();
            state.hubs.put(hub.id(), new SimulationState.Hub(hub, occupied, hub.status() == HubStatus.ACTIVE));
        }
        for (Vehicle vehicle : snapshot.vehicles().stream().sorted(Comparator.comparing(Vehicle::id)).toList()) {
            SimulationState.Hub hub = vehicle.hubId() == null ? null : state.hubs.get(vehicle.hubId());
            if (hub == null || vehicle.status() != VehicleStatus.AVAILABLE || !hub.operational) {
                continue;
            }
            var reading = telemetry.getVehicleTelemetry(vehicle.id());
            if (reading.quality() != DataQuality.FRESH || reading.batteryPct() == null) {
                state.missingData.add("TELEMETRY_UNAVAILABLE:" + vehicle.id());
            } else if (reading.batteryPct() >= MIN_BATTERY) {
                hub.available.add(new SimulationState.Vehicle(vehicle.id(), reading.batteryPct()));
            }
        }
        for (SimulationState.Hub hub : state.hubs.values()) {
            if (hub.available.size() > hub.occupied) {
                state.missingData.add("OCCUPANCY_INCONSISTENT:" + hub.source.id());
                hub.available.clear();
                hub.operational = false;
            }
        }
        return state;
    }

    private List<Route> baseline(StoreSnapshot snapshot, SimulationState state, Instant now) {
        Instant earliest = now.minus(properties.lookback());
        Map<RouteKey, Route> routes = new HashMap<>();
        for (Ride ride : snapshot.rides().stream().sorted(Comparator.comparing(Ride::id)).toList()) {
            if (ride.status() != RideStatus.COMPLETED || ride.endedAt() == null
                    || ride.endedAt().isBefore(earliest) || ride.endedAt().isAfter(now)) {
                continue;
            }
            if (ride.startedAt() == null || !ride.startedAt().isBefore(ride.endedAt())
                    || ride.originHubId() == null || ride.destinationHubId() == null
                    || !state.hubs.containsKey(ride.originHubId()) || !state.hubs.containsKey(ride.destinationHubId())
                    || ride.distanceKm() == null || !Double.isFinite(ride.distanceKm()) || ride.distanceKm() < 0) {
                state.missingData.add("INCOMPLETE_RIDE_HISTORY:" + ride.id());
                continue;
            }
            RouteKey key = new RouteKey(ride.originHubId(), ride.destinationHubId());
            Route route = routes.computeIfAbsent(key, Route::new);
            route.count++;
            route.totalSeconds += Duration.between(ride.startedAt(), ride.endedAt()).toSeconds();
            route.totalDistance += ride.distanceKm();
        }
        if (routes.isEmpty()) {
            state.missingData.add("MISSING_DEMAND_BASELINE");
        }
        return routes.values().stream().sorted(Comparator.comparing((Route route) -> route.key.from())
                .thenComparing(route -> route.key.to())).toList();
    }

    private void seedActiveRides(StoreSnapshot snapshot, SimulationState state, List<Route> routes,
            Instant now, PriorityQueue<Arrival> arrivals) {
        Set<String> seen = new HashSet<>();
        for (Ride ride : snapshot.rides().stream().filter(ride -> ride.status() == RideStatus.ACTIVE)
                .sorted(Comparator.comparing(Ride::id)).toList()) {
            Route route = routes.stream().filter(r -> r.key.from().equals(ride.originHubId())
                    && r.key.to().equals(ride.destinationHubId())).findFirst().orElse(null);
            Vehicle vehicle = snapshot.vehicles().stream().filter(v -> v.id().equals(ride.vehicleId())).findFirst().orElse(null);
            if (route == null || ride.startedAt() == null || ride.startedAt().isAfter(now)
                    || vehicle == null || vehicle.status() != VehicleStatus.IN_USE || !seen.add(vehicle.id())) {
                state.missingData.add("INCOMPLETE_ACTIVE_RIDE:" + ride.id());
                continue;
            }
            var reading = telemetry.getVehicleTelemetry(vehicle.id());
            if (reading.quality() != DataQuality.FRESH || reading.batteryPct() == null) {
                state.missingData.add("TELEMETRY_UNAVAILABLE:" + vehicle.id());
                continue;
            }
            Instant arrival = ride.startedAt().plusSeconds(route.durationSeconds());
            arrivals.add(new Arrival(arrival.isBefore(now) ? now : arrival, ride.destinationHubId(),
                    new SimulationState.Vehicle(vehicle.id(), reading.batteryPct())));
        }
    }

    private long arrive(SimulationState state, PriorityQueue<Arrival> arrivals, Instant until) {
        long failed = 0;
        while (!arrivals.isEmpty() && !arrivals.peek().at().isAfter(until)) {
            Arrival arrival = arrivals.remove();
            SimulationState.Hub hub = state.hubs.get(arrival.hubId());
            if (!hub.operational || hub.occupied >= hub.capacity) {
                failed++;
                continue;
            }
            hub.occupied++;
            if (arrival.vehicle().battery >= MIN_BATTERY) {
                hub.available.add(arrival.vehicle());
            }
            hub.observe();
        }
        return failed;
    }

    private SimulationResult aggregateResults(SimulationState state, List<TimeStepResult> steps, Instant generatedAt) {
        List<String> overloaded = state.hubs.values().stream().filter(hub -> hub.peakPct >= OVERLOAD_PCT)
                .map(hub -> hub.source.id()).toList();
        Map<String, Double> metrics = new TreeMap<>();
        state.hubs.forEach((id, hub) -> metrics.put(id, hub.peakPct));
        return new SimulationResult(UUID.randomUUID().toString(), "COMPLETED", overloaded, optimalProposals(state),
                "Simulated in isolated in-memory snapshot. Live system state untouched.",
                state.sourceVersion, generatedAt, steps.stream().mapToLong(TimeStepResult::failedBookings).sum(),
                steps.stream().mapToLong(TimeStepResult::failedReturns).sum(), metrics, state.missingData);
    }

    public List<SimulationResult.DispatchProposal> optimalProposals(SimulationState state) {
        Map<String, Integer> counts = new HashMap<>();
        Map<String, Integer> occupancy = new HashMap<>();
        state.hubs.forEach((id, hub) -> {
            counts.put(id, hub.available.size());
            occupancy.put(id, hub.occupied);
        });
        List<SimulationResult.DispatchProposal> proposals = new ArrayList<>();
        for (SimulationState.Hub source : state.hubs.values()) {
            if (!source.operational) {
                continue;
            }
            List<SimulationState.Hub> destinations = state.hubs.values().stream()
                    .filter(hub -> hub.operational && hub != source)
                    .sorted(Comparator.comparingDouble((SimulationState.Hub hub) -> angularDistance(source.source, hub.source))
                            .thenComparing(hub -> hub.source.id())).toList();
            for (SimulationState.Hub target : destinations) {
                int spare = counts.get(source.source.id()) - RESERVE_VEHICLES;
                int room = target.capacity - occupancy.get(target.source.id());
                int deficit = Math.max(0, RESERVE_VEHICLES - counts.get(target.source.id()));
                int excess = Math.max(0, occupancy.get(source.source.id())
                        - ((int) Math.ceil(source.capacity * OVERLOAD_PCT / 100.0) - 1));
                int destinationSafeRoom = Math.max(0, (int) Math.ceil(target.capacity * OVERLOAD_PCT / 100.0)
                        - 1 - occupancy.get(target.source.id()));
                int move = Math.min(spare, Math.min(room, Math.min(destinationSafeRoom, Math.max(deficit, excess))));
                if (move <= 0) {
                    continue;
                }
                proposals.add(new SimulationResult.DispatchProposal(UUID.randomUUID().toString(),
                        source.source.id(), target.source.id(), move));
                counts.compute(source.source.id(), (id, count) -> count - move);
                counts.compute(target.source.id(), (id, count) -> count + move);
                occupancy.compute(source.source.id(), (id, count) -> count - move);
                occupancy.compute(target.source.id(), (id, count) -> count + move);
            }
        }
        return proposals;
    }

    private void validateParameters(Scenario scenario) {
        if (scenario == null || scenario.scenarioName() == null || scenario.scenarioName().isBlank()
                || scenario.scenarioName().length() > 120 || scenario.parameters() == null) {
            throw error(400, "VALIDATION_ERROR", "Scenario name and parameters are required");
        }
        var parameters = scenario.parameters();
        if (parameters.simulatedDurationHours() == null || !Double.isFinite(parameters.simulatedDurationHours())
                || parameters.simulatedDurationHours() <= 0 || parameters.simulatedDurationHours() > MAX_HOURS
                || parameters.rentalMultiplier() == null || !Double.isFinite(parameters.rentalMultiplier())
                || parameters.rentalMultiplier() < 0 || parameters.rentalMultiplier() > MAX_MULTIPLIER
                || (parameters.hubOutageId() != null && parameters.hubOutageId().isBlank())) {
            throw error(400, "VALIDATION_ERROR", "Invalid duration, multiplier or outage identifier");
        }
    }

    private boolean validCoordinates(MobilityHub hub) {
        return Double.isFinite(hub.latitude()) && Math.abs(hub.latitude()) <= 90
                && Double.isFinite(hub.longitude()) && Math.abs(hub.longitude()) <= 180;
    }

    private double angularDistance(MobilityHub from, MobilityHub to) {
        double lat = Math.toRadians(from.latitude() - to.latitude());
        double lon = Math.toRadians(from.longitude() - to.longitude());
        double haversine = Math.pow(Math.sin(lat / 2), 2)
                + Math.cos(Math.toRadians(from.latitude())) * Math.cos(Math.toRadians(to.latitude()))
                * Math.pow(Math.sin(lon / 2), 2);
        return Math.asin(Math.sqrt(Math.min(1, haversine)));
    }

    private SimulationException error(int status, String code, String message) {
        return new SimulationException(status, code, message);
    }
}
