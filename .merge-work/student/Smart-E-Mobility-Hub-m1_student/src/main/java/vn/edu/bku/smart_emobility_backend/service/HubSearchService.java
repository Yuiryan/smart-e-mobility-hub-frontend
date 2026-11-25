package vn.edu.bku.smart_emobility_backend.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import vn.edu.bku.smart_emobility_backend.common.store.ParkingSpaceDataStore;
import vn.edu.bku.smart_emobility_backend.common.domain.ParkingSpaceStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.edu.bku.smart_emobility_backend.common.domain.DataQuality;
import vn.edu.bku.smart_emobility_backend.common.domain.MobilityHub;
import vn.edu.bku.smart_emobility_backend.common.domain.SearchCriteria;
import vn.edu.bku.smart_emobility_backend.common.domain.Vehicle;
import vn.edu.bku.smart_emobility_backend.common.store.HubDataStore;
import vn.edu.bku.smart_emobility_backend.common.store.VehicleDataStore;
import vn.edu.bku.smart_emobility_backend.dto.HubSearchResultDTO;
import vn.edu.bku.smart_emobility_backend.dto.VehicleSummaryDTO;

@Service
public class HubSearchService {
    private final HubDataStore hubDataStore;
    private final VehicleDataStore vehicleDataStore;
    private final ParkingSpaceDataStore spaces;
    private final Clock clock;
    private final Duration freshnessLimit;
    private final double minBatteryPct;
    private final double maxRadiusKm;

    public HubSearchService(HubDataStore hubDataStore, VehicleDataStore vehicleDataStore,
            ParkingSpaceDataStore spaces, Clock clock,
            @Value("${app.search.freshness-limit:PT10M}") Duration freshnessLimit,
            @Value("${app.search.min-battery-pct:20}") double minBatteryPct,
            @Value("${app.search.max-radius-km:50}") double maxRadiusKm) {
        this.hubDataStore = hubDataStore;
        this.vehicleDataStore = vehicleDataStore;
        this.spaces = spaces;
        this.clock = clock;
        this.freshnessLimit = freshnessLimit;
        this.minBatteryPct = minBatteryPct;
        this.maxRadiusKm = maxRadiusKm;
    }

    public List<HubSearchResultDTO> findSuitableHubs(SearchCriteria criteria) {
        validateSearchCriteria(criteria);
        Instant now = clock.instant();

        return hubDataStore.findNearby(criteria).stream()
                .map(hub -> buildResult(hub, criteria, now))
                .sorted(Comparator.comparingDouble(HubSearchResultDTO::distanceKm)
                        .thenComparing(HubSearchResultDTO::hubId))
                .toList();
    }

    private HubSearchResultDTO buildResult(MobilityHub hub, SearchCriteria criteria, Instant now) {
        List<Vehicle> allVehicles = vehicleDataStore.listByHub(hub.hubId());
        List<VehicleSummaryDTO> availableVehicles = allVehicles.stream()
                .filter(vehicle -> vehicle.isSearchable(now, freshnessLimit, minBatteryPct))
                .map(vehicle -> new VehicleSummaryDTO(vehicle.vehicleId(), vehicle.vehicleType(),
                        vehicle.batteryPct(), vehicle.status()))
                .toList();
        DataQuality telemetryStatus = telemetryStatus(allVehicles, now);

        int availableSpaces = (int) spaces.listByHub(hub.id()).stream()
                .filter(space -> space.status() == ParkingSpaceStatus.AVAILABLE && space.occupiedVehicleId() == null)
                .count();
        return new HubSearchResultDTO(hub.hubId(), hub.name(), hub.location(),
                hub.distanceTo(criteria.referencePoint()), availableVehicles.size(),
                availableSpaces, availableVehicles, telemetryStatus);
    }

    private DataQuality telemetryStatus(List<Vehicle> vehicles, Instant now) {
        if (vehicles.isEmpty()
                || vehicles.stream().anyMatch(vehicle -> vehicle.telemetryAt() == null || vehicle.batteryPct() == null || !Double.isFinite(vehicle.batteryPct()) || vehicle.batteryPct() < 0 || vehicle.batteryPct() > 100)) {
            return DataQuality.MISSING;
        }
        boolean allFresh = vehicles.stream().allMatch(vehicle -> vehicle.telemetryAt() != null
                && !Duration.between(vehicle.telemetryAt(), now).isNegative()
                && Duration.between(vehicle.telemetryAt(), now).compareTo(freshnessLimit) <= 0);
        return allFresh ? DataQuality.FRESH : DataQuality.STALE;
    }

    private void validateSearchCriteria(SearchCriteria criteria) {
        if (criteria == null || criteria.referencePoint() == null) {
            throw new IllegalArgumentException("origin or destination is required");
        }
        if (!Double.isFinite(criteria.radiusKm()) || criteria.radiusKm() <= 0
                || criteria.radiusKm() > maxRadiusKm) {
            throw new IllegalArgumentException("radiusKm is outside the allowed range");
        }
        if (!Double.isFinite(maxRadiusKm) || maxRadiusKm <= 0
                || !Double.isFinite(minBatteryPct) || minBatteryPct < 0 || minBatteryPct > 100
                || freshnessLimit == null || freshnessLimit.isNegative() || freshnessLimit.isZero()) {
            throw new IllegalStateException("invalid UC-01 search configuration");
        }
    }
}
