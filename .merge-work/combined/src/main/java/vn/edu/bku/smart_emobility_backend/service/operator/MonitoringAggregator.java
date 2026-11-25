package vn.edu.bku.smart_emobility_backend.service.operator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.edu.bku.smart_emobility_backend.common.domain.*;
import vn.edu.bku.smart_emobility_backend.common.error.ApiException;
import vn.edu.bku.smart_emobility_backend.common.error.GlobalExceptionHandler;
import vn.edu.bku.smart_emobility_backend.common.store.InMemoryDataStore;
import vn.edu.bku.smart_emobility_backend.common.store.LatestDataStore;
import vn.edu.bku.smart_emobility_backend.common.store.OperatorStateRegistry;
import vn.edu.bku.smart_emobility_backend.common.store.OperatorStateRegistry.PortMeta;
import vn.edu.bku.smart_emobility_backend.common.store.OperatorStateRegistry.SpaceMeta;
import vn.edu.bku.smart_emobility_backend.common.store.OperatorStateRegistry.VehicleMeta;
import vn.edu.bku.smart_emobility_backend.common.store.StoreSnapshot;
import vn.edu.bku.smart_emobility_backend.dto.operator.*;
import vn.edu.bku.smart_emobility_backend.dto.operator.OperatorVehicleDTO.ActiveUsageDTO;
import vn.edu.bku.smart_emobility_backend.dto.operator.OperatorVehicleDTO.GeoLocationDTO;

@Service
public class MonitoringAggregator {
    private final InMemoryDataStore dataStore;
    private final OperatorStateRegistry stateRegistry;
    private final LatestDataStore latestDataStore;
    private final Clock clock;
    private final Duration freshnessLimit;
    private final double warningThresholdPct;
    private final double criticalThresholdPct;
    private final double minSelfDriveBatteryPct;

    public MonitoringAggregator(
            InMemoryDataStore dataStore,
            OperatorStateRegistry stateRegistry,
            LatestDataStore latestDataStore,
            Clock clock,
            @Value("${app.operator.freshness-limit:PT30S}") Duration freshnessLimit,
            @Value("${app.operator.warning-threshold-pct:80}") double warningThresholdPct,
            @Value("${app.operator.critical-threshold-pct:95}") double criticalThresholdPct,
            @Value("${app.search.min-battery-pct:20}") double minSelfDriveBatteryPct) {
        this.dataStore = dataStore;
        this.stateRegistry = stateRegistry;
        this.latestDataStore = latestDataStore;
        this.clock = clock;
        this.freshnessLimit = freshnessLimit;
        this.warningThresholdPct = warningThresholdPct;
        this.criticalThresholdPct = criticalThresholdPct;
        this.minSelfDriveBatteryPct = minSelfDriveBatteryPct;
    }

    public SnapshotListResponseDTO<HubSummaryDTO> listHubSummaries() {
        StoreSnapshot snapshot = dataStore.snapshot();
        Instant now = clock.instant();
        List<HubSummaryDTO> items = snapshot.hubs().stream()
                .sorted(Comparator.comparing(MobilityHub::hubId))
                .map(hub -> {
                    HubMetricsDTO metrics = computeHubMetrics(hub, snapshot, now);
                    return new HubSummaryDTO(
                            metrics.hubId(),
                            metrics.hubName(),
                            metrics.capacity(),
                            metrics.usableCapacity(),
                            metrics.blockedCount(),
                            metrics.freeSpaceCount(),
                            metrics.hubPowerLimitW(),
                            metrics.version());
                })
                .toList();
        return new SnapshotListResponseDTO<>(snapshot.version(), items);
    }

    public SnapshotListResponseDTO<OperatorParkingSpaceDTO> listHubSpaces(String hubId) {
        StoreSnapshot snapshot = dataStore.snapshot();
        MobilityHub hub = findHubOrThrow(snapshot, hubId);
        List<OperatorParkingSpaceDTO> spaces = buildSpacesForHub(hub, snapshot);
        return new SnapshotListResponseDTO<>(snapshot.version(), spaces);
    }

    public OperatorParkingSpaceDTO getParkingSpace(String spaceId) {
        StoreSnapshot snapshot = dataStore.snapshot();
        ParkingSpace space = snapshot.parkingSpaces().stream()
                .filter(s -> s.id().equals(spaceId))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                        "Không tìm thấy vị trí đỗ: " + spaceId));
        return toOperatorParkingSpaceDTO(space, snapshot);
    }

    public SnapshotListResponseDTO<OperatorVehicleDTO> listVehicles(String hubId, VehicleStatus status) {
        StoreSnapshot snapshot = dataStore.snapshot();
        if (hubId != null) {
            findHubOrThrow(snapshot, hubId);
        }
        Instant now = clock.instant();
        List<OperatorVehicleDTO> items = snapshot.vehicles().stream()
                .filter(v -> hubId == null || hubId.equals(v.hubId()))
                .filter(v -> status == null || status == v.status())
                .sorted(Comparator.comparing(Vehicle::vehicleId))
                .map(v -> toOperatorVehicleDTO(v, snapshot, now))
                .toList();
        return new SnapshotListResponseDTO<>(snapshot.version(), items);
    }

    public OperatorVehicleDTO getVehicle(String vehicleId) {
        StoreSnapshot snapshot = dataStore.snapshot();
        Instant now = clock.instant();
        Vehicle vehicle = snapshot.vehicles().stream()
                .filter(v -> v.vehicleId().equals(vehicleId))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                        "Không tìm thấy phương tiện: " + vehicleId));
        return toOperatorVehicleDTO(vehicle, snapshot, now);
    }

    public SnapshotListResponseDTO<OperatorChargingPortDTO> listChargingPorts(
            String hubId,
            OperatorPortStatus status) {
        StoreSnapshot snapshot = dataStore.snapshot();
        if (hubId != null) {
            findHubOrThrow(snapshot, hubId);
        }
        Instant now = clock.instant();
        List<OperatorChargingPortDTO> items = snapshot.chargingPorts().stream()
                .filter(p -> hubId == null || hubId.equals(p.hubId()))
                .map(p -> toOperatorChargingPortDTO(p, snapshot, now))
                .filter(dto -> status == null || status == dto.status())
                .sorted(Comparator.comparing(OperatorChargingPortDTO::portId))
                .toList();
        return new SnapshotListResponseDTO<>(snapshot.version(), items);
    }

    public OperatorChargingPortDTO getChargingPort(String portId) {
        StoreSnapshot snapshot = dataStore.snapshot();
        Instant now = clock.instant();
        ChargingPort port = snapshot.chargingPorts().stream()
                .filter(p -> p.id().equals(portId))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                        "Không tìm thấy cổng sạc: " + portId));
        return toOperatorChargingPortDTO(port, snapshot, now);
    }

    public SnapshotListResponseDTO<OperatorChargingRequestDTO> listChargingRequests(
            String hubId,
            String vehicleId,
            OperatorChargingRequestStatus status) {
        StoreSnapshot snapshot = dataStore.snapshot();
        if (hubId != null) {
            findHubOrThrow(snapshot, hubId);
        }
        if (vehicleId != null) {
            boolean vehicleExists = snapshot.vehicles().stream().anyMatch(v -> v.vehicleId().equals(vehicleId));
            if (!vehicleExists) {
                throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                        "Không tìm thấy phương tiện: " + vehicleId);
            }
        }
        List<OperatorChargingRequestDTO> items = snapshot.chargingRequests().stream()
                .filter(r -> hubId == null || hubId.equals(r.hubId()))
                .filter(r -> vehicleId == null || vehicleId.equals(r.vehicleId()))
                .map(r -> toOperatorChargingRequestDTO(r, snapshot))
                .filter(dto -> status == null || status == dto.status())
                .sorted(Comparator.comparing(OperatorChargingRequestDTO::chargingRequestId))
                .toList();
        return new SnapshotListResponseDTO<>(snapshot.version(), items);
    }

    public NetworkReportDTO getNetworkData() {
        StoreSnapshot snapshot = dataStore.snapshot();
        Instant now = clock.instant();
        List<HubMetricsDTO> hubs = snapshot.hubs().stream()
                .sorted(Comparator.comparing(MobilityHub::hubId))
                .map(hub -> computeHubMetrics(hub, snapshot, now))
                .toList();
        latestDataStore.save(hubs, snapshot.version());
        return new NetworkReportDTO(
                GlobalExceptionHandler.formatTimestamp(now),
                snapshot.version(),
                hubs);
    }

    public HubReportDTO getHubDetails(String hubId) {
        StoreSnapshot snapshot = dataStore.snapshot();
        Instant now = clock.instant();
        MobilityHub hub = findHubOrThrow(snapshot, hubId);
        HubMetricsDTO metrics = computeHubMetrics(hub, snapshot, now);
        latestDataStore.save(List.of(metrics), snapshot.version());
        return new HubReportDTO(
                GlobalExceptionHandler.formatTimestamp(now),
                snapshot.version(),
                metrics);
    }

    public HubMetricsDTO computeHubMetrics(MobilityHub hub, StoreSnapshot snapshot, Instant now) {
        int capacity = hub.parkingCapacity();
        List<ParkingSpace> hubSpaces = snapshot.parkingSpaces().stream()
                .filter(s -> hub.hubId().equals(s.hubId()))
                .sorted(Comparator.comparing(ParkingSpace::id))
                .toList();
        List<Vehicle> hubVehicles = snapshot.vehicles().stream()
                .filter(v -> hub.hubId().equals(v.hubId()))
                .toList();

        int blockedCount = 0;
        int occupiedCount = 0;
        int reservedCount = 0;
        int inboundCount = 0;

        if (!hubSpaces.isEmpty()) {
            for (ParkingSpace space : hubSpaces) {
                OperatorParkingSpaceDTO spaceDto = toOperatorParkingSpaceDTO(space, snapshot);
                if (spaceDto.blocked()) {
                    blockedCount++;
                } else if ("OCCUPIED".equals(spaceDto.status())) {
                    occupiedCount++;
                } else if ("RESERVED".equals(spaceDto.status())) {
                    if (spaceDto.reservationKind() == SpaceReservationKind.DISPATCH_INBOUND) {
                        inboundCount++;
                    } else {
                        reservedCount++;
                    }
                }
            }
        } else {
            // Fallback when explicit ParkingSpace entities are not seeded for this hub:
            // derive counts from vehicles currently at the hub, active parking reservations, and inbound dispatches
            for (Vehicle v : hubVehicles) {
                if (v.status() != VehicleStatus.IN_USE && v.status() != VehicleStatus.IN_TRANSIT) {
                    occupiedCount++;
                }
            }
            long activeInTransitToHub = snapshot.dispatchOrders().stream()
                    .filter(d -> hub.hubId().equals(d.destinationHubId())
                            && d.status() == DispatchOrderStatus.IN_TRANSIT)
                    .count();
            inboundCount = Math.max(hub.expectedIncoming(), (int) activeInTransitToHub);
        }

        int usableCapacity = capacity - blockedCount;
        int freeSpaceCount = usableCapacity - occupiedCount - reservedCount - inboundCount;

        if (capacity < 0 || blockedCount < 0 || occupiedCount < 0 || reservedCount < 0 || inboundCount < 0
                || usableCapacity < 0 || freeSpaceCount < 0) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STATE_INCONSISTENT",
                    "Dữ liệu sức chứa Hub không nhất quán (số lượng slot âm hoặc vượt sức chứa tại " + hub.hubId() + ")");
        }

        // Determine hub/parking dataQuality and observedAt
        Instant parkingObservedAt = determineHubParkingObservedAt(hub, hubSpaces, hubVehicles);
        boolean anyOfflineVehicle = hubVehicles.stream()
                .anyMatch(v -> resolveVehicleConnectionStatus(v) == ConnectionStatus.OFFLINE);
        DataQuality hubDataQuality = evaluateDataQuality(parkingObservedAt,
                anyOfflineVehicle ? ConnectionStatus.OFFLINE : ConnectionStatus.ONLINE, now);

        // If cached snapshot exists in LatestDataStore when current observation is missing, retain last observedAt as STALE
        if (hubDataQuality == DataQuality.MISSING && parkingObservedAt == null) {
            Optional<HubMetricsDTO> cached = latestDataStore.readLatestHub(hub.hubId());
            if (cached.isPresent() && cached.get().observedAt() != null) {
                hubDataQuality = DataQuality.STALE;
            }
        }

        Double rawOccupancyPct = usableCapacity > 0
                ? (100.0 * (occupiedCount + reservedCount + inboundCount)) / usableCapacity
                : null;
        Double occupancyPct = roundPct(rawOccupancyPct);
        AlertSeverity parkingSeverity = evaluateDimensionSeverity(rawOccupancyPct, usableCapacity > 0, hubDataQuality);

        // Vehicle counts by status (all 6 keys required)
        Map<String, Integer> vehicleCounts = new LinkedHashMap<>();
        vehicleCounts.put(VehicleStatus.AVAILABLE.name(), 0);
        vehicleCounts.put(VehicleStatus.RESERVED.name(), 0);
        vehicleCounts.put(VehicleStatus.IN_USE.name(), 0);
        vehicleCounts.put(VehicleStatus.IN_TRANSIT.name(), 0);
        vehicleCounts.put(VehicleStatus.LOW_BATTERY.name(), 0);
        vehicleCounts.put(VehicleStatus.MAINTENANCE.name(), 0);
        for (Vehicle v : hubVehicles) {
            vehicleCounts.computeIfPresent(v.status().name(), (k, count) -> count + 1);
        }

        // Charging ports & metrics
        List<ChargingPort> hubPorts = snapshot.chargingPorts().stream()
                .filter(p -> hub.hubId().equals(p.hubId()))
                .sorted(Comparator.comparing(ChargingPort::id))
                .toList();
        List<OperatorChargingPortDTO> portDtos = hubPorts.stream()
                .map(p -> toOperatorChargingPortDTO(p, snapshot, now))
                .toList();

        Map<String, Integer> portCounts = new LinkedHashMap<>();
        portCounts.put(OperatorPortStatus.AVAILABLE.name(), 0);
        portCounts.put(OperatorPortStatus.SCHEDULED.name(), 0);
        portCounts.put(OperatorPortStatus.CHARGING.name(), 0);
        portCounts.put(OperatorPortStatus.MAINTENANCE.name(), 0);
        for (OperatorChargingPortDTO p : portDtos) {
            portCounts.computeIfPresent(p.status().name(), (k, count) -> count + 1);
        }

        double powerLimitW = roundPct(hub.chargingCapacityKw() * 1000.0) != null
                ? roundPct(hub.chargingCapacityKw() * 1000.0)
                : 0.0;

        int waitingRequests = (int) snapshot.chargingRequests().stream()
                .filter(r -> hub.hubId().equals(r.hubId()))
                .filter(r -> OperatorChargingRequestStatus.fromDomain(r.status()) == OperatorChargingRequestStatus.WAITING)
                .count();

        ChargingMetricsDTO chargingMetrics = computeChargingMetrics(
                hub, hubPorts, portDtos, powerLimitW, waitingRequests, hubDataQuality, parkingObservedAt, now);

        AlertSeverity overallSeverity = AlertSeverity.combine(parkingSeverity, chargingMetrics.severity());
        String formattedObservedAt = GlobalExceptionHandler.formatTimestamp(parkingObservedAt);
        if (formattedObservedAt == null) {
            formattedObservedAt = latestDataStore.readLatestHub(hub.hubId())
                    .map(HubMetricsDTO::observedAt)
                    .orElse(null);
        }

        return new HubMetricsDTO(
                hub.hubId(),
                hub.name(),
                capacity,
                usableCapacity,
                blockedCount,
                freeSpaceCount,
                powerLimitW,
                stateRegistry.getHubVersion(hub.hubId()),
                occupiedCount,
                reservedCount,
                inboundCount,
                occupancyPct,
                parkingSeverity,
                vehicleCounts,
                portCounts,
                chargingMetrics,
                overallSeverity,
                hubDataQuality,
                formattedObservedAt);
    }

    private ChargingMetricsDTO computeChargingMetrics(
            MobilityHub hub,
            List<ChargingPort> rawPorts,
            List<OperatorChargingPortDTO> portDtos,
            double powerLimitW,
            int waitingRequests,
            DataQuality fallbackHubQuality,
            Instant fallbackObservedAt,
            Instant now) {
        int totalUsablePortCount = (int) portDtos.stream()
                .filter(p -> p.status() != OperatorPortStatus.MAINTENANCE)
                .count();
        int activePortCount = (int) portDtos.stream()
                .filter(p -> p.status() == OperatorPortStatus.CHARGING)
                .count();

        Double rawPortUtilPct = totalUsablePortCount > 0
                ? (100.0 * activePortCount) / totalUsablePortCount
                : null;
        Double portUtilizationPct = roundPct(rawPortUtilPct);

        DataQuality chargingQuality;
        Instant oldestPortObservedAt = null;
        Double totalMeasuredPowerW = null;

        if (portDtos.isEmpty()) {
            chargingQuality = DataQuality.MISSING;
        } else {
            boolean anyMissing = false;
            boolean anyStale = false;
            boolean allMeasuredKnown = true;
            double sumPower = 0.0;

            for (int i = 0; i < portDtos.size(); i++) {
                OperatorChargingPortDTO dto = portDtos.get(i);
                ChargingPort raw = rawPorts.get(i);
                Instant portObs = resolvePortObservedAt(raw, fallbackObservedAt);
                if (portObs != null) {
                    if (oldestPortObservedAt == null || portObs.isBefore(oldestPortObservedAt)) {
                        oldestPortObservedAt = portObs;
                    }
                }
                if (dto.dataQuality() == DataQuality.MISSING) {
                    anyMissing = true;
                } else if (dto.dataQuality() == DataQuality.STALE) {
                    anyStale = true;
                }
                if (dto.measuredPowerW() == null) {
                    allMeasuredKnown = false;
                } else {
                    sumPower += dto.measuredPowerW();
                }
            }

            if (anyMissing || !allMeasuredKnown) {
                chargingQuality = DataQuality.MISSING;
            } else if (anyStale) {
                chargingQuality = DataQuality.STALE;
            } else {
                chargingQuality = DataQuality.FRESH;
            }

            if (allMeasuredKnown) {
                totalMeasuredPowerW = roundPct(sumPower);
            }
        }

        Double rawPowerUtilPct = (totalMeasuredPowerW != null && powerLimitW > 0)
                ? (100.0 * totalMeasuredPowerW) / powerLimitW
                : null;
        Double powerUtilizationPct = roundPct(rawPowerUtilPct);

        AlertSeverity portSeverity = evaluateDimensionSeverity(
                rawPortUtilPct, totalUsablePortCount > 0, chargingQuality);
        AlertSeverity powerSeverity = evaluateDimensionSeverity(
                rawPowerUtilPct, powerLimitW > 0 && totalMeasuredPowerW != null, chargingQuality);
        AlertSeverity chargingSeverity = AlertSeverity.combine(portSeverity, powerSeverity);

        return new ChargingMetricsDTO(
                activePortCount,
                totalUsablePortCount,
                portUtilizationPct,
                totalMeasuredPowerW,
                powerLimitW,
                powerUtilizationPct,
                waitingRequests,
                chargingSeverity,
                chargingQuality,
                GlobalExceptionHandler.formatTimestamp(oldestPortObservedAt));
    }

    public AlertSeverity evaluateDimensionSeverity(Double rawPct, boolean validCapacity, DataQuality quality) {
        if (!validCapacity || rawPct == null || quality != DataQuality.FRESH) {
            return AlertSeverity.MISSING_DATA;
        }
        if (rawPct >= criticalThresholdPct) {
            return AlertSeverity.CRITICAL;
        }
        if (rawPct >= warningThresholdPct) {
            return AlertSeverity.WARNING;
        }
        return AlertSeverity.NORMAL;
    }

    public DataQuality evaluateDataQuality(Instant observedAt, ConnectionStatus connectionStatus, Instant now) {
        if (observedAt == null) {
            return DataQuality.MISSING;
        }
        if (connectionStatus == ConnectionStatus.OFFLINE) {
            return DataQuality.STALE;
        }
        Duration age = Duration.between(observedAt, now);
        if (age.isNegative() || age.compareTo(freshnessLimit) > 0) {
            return DataQuality.STALE;
        }
        return DataQuality.FRESH;
    }

    private Instant determineHubParkingObservedAt(
            MobilityHub hub,
            List<ParkingSpace> hubSpaces,
            List<Vehicle> hubVehicles) {
        Instant oldest = null;
        for (ParkingSpace space : hubSpaces) {
            SpaceMeta meta = stateRegistry.getSpaceMeta(space.id());
            if (meta != null && meta.observedAt() != null) {
                if (oldest == null || meta.observedAt().isBefore(oldest)) {
                    oldest = meta.observedAt();
                }
            }
        }
        for (Vehicle v : hubVehicles) {
            if (v.telemetryAt() != null) {
                if (oldest == null || v.telemetryAt().isBefore(oldest)) {
                    oldest = v.telemetryAt();
                }
            }
        }
        return oldest;
    }

    private List<OperatorParkingSpaceDTO> buildSpacesForHub(MobilityHub hub, StoreSnapshot snapshot) {
        return snapshot.parkingSpaces().stream()
                .filter(s -> hub.hubId().equals(s.hubId()))
                .sorted(Comparator.comparing(ParkingSpace::id))
                .map(s -> toOperatorParkingSpaceDTO(s, snapshot))
                .toList();
    }

    public OperatorParkingSpaceDTO toOperatorParkingSpaceDTO(ParkingSpace space, StoreSnapshot snapshot) {
        SpaceMeta meta = stateRegistry.getSpaceMeta(space.id());
        boolean blocked = (meta != null && meta.blocked() != null)
                ? meta.blocked()
                : (space.status() == ParkingSpaceStatus.OUT_OF_SERVICE);

        String wireStatus;
        if (space.status() == ParkingSpaceStatus.OUT_OF_SERVICE) {
            wireStatus = "AVAILABLE";
            blocked = true;
        } else {
            wireStatus = space.status().name();
        }

        String vehicleId = meta != null ? meta.vehicleId() : null;
        String parkingReservationId = meta != null ? meta.parkingReservationId() : null;
        String dispatchOrderId = meta != null ? meta.dispatchOrderId() : null;
        SpaceReservationKind reservationKind = meta != null ? meta.reservationKind() : null;

        if ("OCCUPIED".equals(wireStatus)) {
            if (vehicleId == null) {
                vehicleId = snapshot.vehicles().stream()
                        .filter(v -> space.id().equals(v.parkingSpaceId()))
                        .map(Vehicle::vehicleId)
                        .findFirst()
                        .orElse(null);
            }
            reservationKind = null;
            parkingReservationId = null;
            dispatchOrderId = null;
        } else if ("RESERVED".equals(wireStatus)) {
            vehicleId = null;
            if (reservationKind == null) {
                Optional<ParkingReservation> activeHold = snapshot.parkingReservations().stream()
                        .filter(pr -> space.id().equals(pr.parkingSpaceId())
                                && pr.status() == ParkingReservationStatus.ACTIVE)
                        .findFirst();
                if (activeHold.isPresent()) {
                    reservationKind = SpaceReservationKind.PARKING_HOLD;
                    parkingReservationId = activeHold.get().id();
                    dispatchOrderId = null;
                } else if (dispatchOrderId != null) {
                    reservationKind = SpaceReservationKind.DISPATCH_INBOUND;
                } else {
                    reservationKind = SpaceReservationKind.PARKING_HOLD;
                }
            }
        } else {
            // AVAILABLE
            vehicleId = null;
            reservationKind = null;
            parkingReservationId = null;
            dispatchOrderId = null;
        }

        long version = meta != null ? meta.version() : 0L;
        String pendingOperationId = meta != null ? meta.pendingOperationId() : null;

        List<InterventionAction> allowedActions = new ArrayList<>();
        if (pendingOperationId == null) {
            if ("AVAILABLE".equals(wireStatus)) {
                if (blocked) {
                    allowedActions.add(InterventionAction.UNBLOCK_SPACE);
                } else {
                    allowedActions.add(InterventionAction.BLOCK_SPACE);
                }
            } else if ("RESERVED".equals(wireStatus)
                    && reservationKind == SpaceReservationKind.PARKING_HOLD
                    && parkingReservationId != null) {
                allowedActions.add(InterventionAction.CANCEL_PARKING_RESERVATION);
            }
        }

        return new OperatorParkingSpaceDTO(
                space.id(),
                space.hubId(),
                wireStatus,
                blocked,
                reservationKind,
                vehicleId,
                parkingReservationId,
                dispatchOrderId,
                version,
                pendingOperationId,
                allowedActions);
    }

    public OperatorVehicleDTO toOperatorVehicleDTO(Vehicle vehicle, StoreSnapshot snapshot, Instant now) {
        MobilityHub hub = vehicle.hubId() == null ? null
                : snapshot.hubs().stream().filter(h -> h.hubId().equals(vehicle.hubId())).findFirst().orElse(null);
        GeoPoint defaultLoc = hub != null ? hub.location() : null;
        boolean defaultLocked = vehicle.status() != VehicleStatus.IN_USE;

        VehicleMeta meta = stateRegistry.getOrDefaultVehicleMeta(
                vehicle.vehicleId(), vehicle.telemetryAt(), defaultLoc, defaultLocked);

        ConnectionStatus connectionStatus = meta.connectionStatus() != null
                ? meta.connectionStatus()
                : (vehicle.telemetryAt() != null ? ConnectionStatus.ONLINE : ConnectionStatus.UNKNOWN);
        DataQuality dataQuality = evaluateDataQuality(vehicle.telemetryAt(), connectionStatus, now);

        BatterySource batterySource = meta.batterySource();
        if (batterySource == null && vehicle.batteryPct() != null) {
            batterySource = BatterySource.TELEMETRY;
        }
        Instant batteryUpdatedAt = meta.batteryUpdatedAt() != null ? meta.batteryUpdatedAt() : vehicle.telemetryAt();

        GeoLocationDTO locationDto = meta.location() != null
                ? new GeoLocationDTO(meta.location().latitude(), meta.location().longitude())
                : (defaultLoc != null ? new GeoLocationDTO(defaultLoc.latitude(), defaultLoc.longitude()) : null);

        String activeRideId = snapshot.rides().stream()
                .filter(r -> vehicle.vehicleId().equals(r.vehicleId()) && r.status() == RideStatus.ACTIVE)
                .map(Ride::id)
                .findFirst()
                .orElse(null);

        String activeReservationId = snapshot.reservations().stream()
                .filter(r -> vehicle.vehicleId().equals(r.vehicleId()) && r.status() == ReservationStatus.ACTIVE)
                .map(Reservation::id)
                .findFirst()
                .orElse(null);

        String activeParkingResId = snapshot.parkingReservations().stream()
                .filter(pr -> vehicle.vehicleId().equals(pr.vehicleId())
                        && pr.status() == ParkingReservationStatus.ACTIVE)
                .map(ParkingReservation::id)
                .findFirst()
                .orElse(null);

        Optional<ChargingRequest> activeChgReq = snapshot.chargingRequests().stream()
                .filter(cr -> vehicle.vehicleId().equals(cr.vehicleId())
                        && (cr.status() == ChargingRequestStatus.PENDING
                                || cr.status() == ChargingRequestStatus.SCHEDULED
                                || cr.status() == ChargingRequestStatus.CHARGING))
                .findFirst();

        String activeChgReqId = activeChgReq.map(ChargingRequest::id).orElse(null);
        String activeAllocId = activeChgReqId == null ? null
                : snapshot.chargingAllocations().stream()
                        .filter(ca -> activeChgReqId.equals(ca.chargingRequestId())
                                && (ca.status() == ChargingAllocationStatus.RESERVED
                                        || ca.status() == ChargingAllocationStatus.ACTIVE))
                        .map(ChargingAllocation::id)
                        .findFirst()
                        .orElse(null);

        ActiveUsageDTO activeUsage = new ActiveUsageDTO(
                activeRideId,
                activeReservationId,
                activeParkingResId,
                activeChgReqId,
                activeAllocId);

        String hubId = activeRideId != null || vehicle.status() == VehicleStatus.IN_TRANSIT ? null : vehicle.hubId();
        String spaceId = activeRideId != null || vehicle.status() == VehicleStatus.IN_TRANSIT
                ? null
                : vehicle.parkingSpaceId();

        boolean hasBlockingIncident = snapshot.incidents().stream()
                .anyMatch(inc -> vehicle.vehicleId().equals(inc.vehicleId())
                        && (inc.status() == IncidentStatus.OPEN || inc.status() == IncidentStatus.INVESTIGATING)
                        && inc.severity() != IncidentSeverity.LOW);

        boolean isBusy = activeRideId != null
                || vehicle.status() == VehicleStatus.IN_USE
                || vehicle.status() == VehicleStatus.IN_TRANSIT
                || activeReservationId != null
                || activeChgReqId != null
                || meta.pendingOperationId() != null;

        List<InterventionAction> allowedActions = new ArrayList<>();
        if (meta.pendingOperationId() == null) {
            if (!isBusy) {
                if (Boolean.TRUE.equals(meta.isLocked())) {
                    if (vehicle.status() != VehicleStatus.MAINTENANCE && !hasBlockingIncident) {
                        allowedActions.add(InterventionAction.UNLOCK);
                    }
                } else {
                    allowedActions.add(InterventionAction.LOCK);
                }
                if (vehicle.status() == VehicleStatus.MAINTENANCE) {
                    if (!hasBlockingIncident) {
                        allowedActions.add(InterventionAction.CLEAR_MAINTENANCE);
                    }
                } else {
                    allowedActions.add(InterventionAction.MARK_MAINTENANCE);
                }
                allowedActions.add(InterventionAction.SET_BATTERY);
            } else {
                if (activeReservationId != null && activeRideId == null) {
                    allowedActions.add(InterventionAction.CANCEL_RESERVATION);
                }
                if (activeChgReqId != null && activeAllocId == null) {
                    allowedActions.add(InterventionAction.CANCEL_CHARGING);
                }
            }
        }

        List<TransportMode> dispatchModes = new ArrayList<>();
        boolean eligibleBase = vehicle.ownership() == VehicleOwnership.SHARED
                && hubId != null
                && !isBusy
                && !hasBlockingIncident
                && meta.maintenanceCauses().isEmpty()
                && dataQuality == DataQuality.FRESH
                && vehicle.batteryPct() != null;
        if (eligibleBase) {
            if (vehicle.status() == VehicleStatus.AVAILABLE && vehicle.batteryPct() >= minSelfDriveBatteryPct) {
                dispatchModes.add(TransportMode.SELF_DRIVE);
                dispatchModes.add(TransportMode.TRUCK);
            } else if ((vehicle.status() == VehicleStatus.AVAILABLE || vehicle.status() == VehicleStatus.LOW_BATTERY)
                    && vehicle.batteryPct() >= 0 && vehicle.batteryPct() <= 100) {
                dispatchModes.add(TransportMode.TRUCK);
            }
        }

        String wireOwnership = vehicle.ownership() == VehicleOwnership.PRIVATE
                ? "PERSONAL"
                : vehicle.ownership().name();

        return new OperatorVehicleDTO(
                vehicle.vehicleId(),
                hubId,
                spaceId,
                wireOwnership,
                vehicle.status(),
                roundPct(vehicle.batteryPct()),
                batterySource,
                GlobalExceptionHandler.formatTimestamp(batteryUpdatedAt),
                GlobalExceptionHandler.formatTimestamp(vehicle.telemetryAt()),
                dataQuality,
                connectionStatus,
                locationDto,
                meta.isLocked(),
                vehicle.version(),
                meta.pendingOperationId(),
                allowedActions,
                dispatchModes,
                activeUsage);
    }

    public OperatorChargingPortDTO toOperatorChargingPortDTO(
            ChargingPort port,
            StoreSnapshot snapshot,
            Instant now) {
        PortMeta meta = stateRegistry.getPortMeta(port.id());
        OperatorPortStatus wireStatus = OperatorPortStatus.fromDomain(port.status());
        double ratedPowerW = roundPct(port.powerKw() * 1000.0) != null ? roundPct(port.powerKw() * 1000.0) : 250.0;

        Optional<ChargingAllocation> activeAllocation = snapshot.chargingAllocations().stream()
                .filter(ca -> port.id().equals(ca.chargingPortId())
                        && ca.status() == ChargingAllocationStatus.ACTIVE)
                .findFirst();

        String allocationId = null;
        String activeChargingRequestId = null;
        String vehicleId = null;

        if (wireStatus == OperatorPortStatus.CHARGING && activeAllocation.isPresent()) {
            allocationId = activeAllocation.get().id();
            activeChargingRequestId = activeAllocation.get().chargingRequestId();
            String reqId = activeChargingRequestId;
            vehicleId = snapshot.chargingRequests().stream()
                    .filter(cr -> cr.id().equals(reqId))
                    .map(ChargingRequest::vehicleId)
                    .findFirst()
                    .orElse(null);
        }

        Instant fallbackObs = snapshot.vehicles().stream()
                .filter(v -> port.hubId().equals(v.hubId()) && v.telemetryAt() != null)
                .map(Vehicle::telemetryAt)
                .min(Comparator.naturalOrder())
                .orElse(null);

        Instant observedAt = resolvePortObservedAt(port, fallbackObs);
        ConnectionStatus connectionStatus = (meta != null && meta.connectionStatus() != null)
                ? meta.connectionStatus()
                : (observedAt != null ? ConnectionStatus.ONLINE : ConnectionStatus.UNKNOWN);
        DataQuality dataQuality = evaluateDataQuality(observedAt, connectionStatus, now);

        Double measuredPowerW;
        if (meta != null) {
            measuredPowerW = roundPct(meta.measuredPowerW());
        } else if (dataQuality == DataQuality.MISSING) {
            measuredPowerW = null;
        } else {
            measuredPowerW = wireStatus == OperatorPortStatus.CHARGING ? ratedPowerW : 0.0;
        }

        Boolean powerEnabled = (meta != null && meta.powerEnabled() != null)
                ? meta.powerEnabled()
                : (wireStatus != OperatorPortStatus.MAINTENANCE);

        long version = meta != null ? meta.version() : 0L;
        String pendingOperationId = meta != null ? meta.pendingOperationId() : null;

        boolean hasBlockingIncident = snapshot.incidents().stream()
                .anyMatch(inc -> port.id().equals(inc.vehicleId())
                        && (inc.status() == IncidentStatus.OPEN || inc.status() == IncidentStatus.INVESTIGATING)
                        && inc.severity() != IncidentSeverity.LOW);

        List<InterventionAction> allowedActions = new ArrayList<>();
        if (pendingOperationId == null) {
            if (wireStatus == OperatorPortStatus.AVAILABLE) {
                allowedActions.add(InterventionAction.MARK_MAINTENANCE);
            } else if (wireStatus == OperatorPortStatus.MAINTENANCE) {
                if (!hasBlockingIncident) {
                    allowedActions.add(InterventionAction.CLEAR_MAINTENANCE);
                }
            } else if (wireStatus == OperatorPortStatus.CHARGING) {
                allowedActions.add(InterventionAction.STOP_CHARGING);
                allowedActions.add(InterventionAction.CANCEL_CHARGING);
            } else if (wireStatus == OperatorPortStatus.SCHEDULED) {
                allowedActions.add(InterventionAction.CANCEL_CHARGING);
            }
        }

        return new OperatorChargingPortDTO(
                port.id(),
                port.hubId(),
                wireStatus,
                ratedPowerW,
                measuredPowerW,
                powerEnabled,
                activeChargingRequestId,
                allocationId,
                vehicleId,
                GlobalExceptionHandler.formatTimestamp(observedAt),
                dataQuality,
                connectionStatus,
                version,
                pendingOperationId,
                allowedActions);
    }

    public OperatorChargingRequestDTO toOperatorChargingRequestDTO(
            ChargingRequest request,
            StoreSnapshot snapshot) {
        Optional<ChargingAllocation> alloc = snapshot.chargingAllocations().stream()
                .filter(ca -> request.id().equals(ca.chargingRequestId())
                        && ca.status() != ChargingAllocationStatus.CANCELLED)
                .findFirst();
        String portId = alloc.map(ChargingAllocation::chargingPortId).orElse(null);
        String allocationId = alloc.map(ChargingAllocation::id).orElse(null);

        return new OperatorChargingRequestDTO(
                request.id(),
                request.vehicleId(),
                request.hubId(),
                portId,
                allocationId,
                OperatorChargingRequestStatus.fromDomain(request.status()),
                request.targetBatteryPct(),
                GlobalExceptionHandler.formatTimestamp(request.requestedAt()),
                stateRegistry.getRequestVersion(request.id()));
    }

    private Instant resolvePortObservedAt(ChargingPort port, Instant fallbackHubObservedAt) {
        PortMeta meta = stateRegistry.getPortMeta(port.id());
        if (meta != null) {
            return meta.observedAt();
        }
        return fallbackHubObservedAt;
    }

    private ConnectionStatus resolveVehicleConnectionStatus(Vehicle vehicle) {
        VehicleMeta meta = stateRegistry.getVehicleMeta(vehicle.vehicleId());
        if (meta != null && meta.connectionStatus() != null) {
            return meta.connectionStatus();
        }
        return vehicle.telemetryAt() != null ? ConnectionStatus.ONLINE : ConnectionStatus.UNKNOWN;
    }

    private MobilityHub findHubOrThrow(StoreSnapshot snapshot, String hubId) {
        return snapshot.hubs().stream()
                .filter(h -> Objects.equals(h.hubId(), hubId))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                        "Không tìm thấy Mobility Hub: " + hubId));
    }

    public static Double roundPct(Double value) {
        if (value == null || !Double.isFinite(value)) {
            return null;
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
