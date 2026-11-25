package vn.edu.bku.smart_emobility_backend.service.operator;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.edu.bku.smart_emobility_backend.common.domain.*;
import vn.edu.bku.smart_emobility_backend.common.error.ApiException;
import vn.edu.bku.smart_emobility_backend.common.error.ApiException.FieldErrorDetail;
import vn.edu.bku.smart_emobility_backend.common.error.GlobalExceptionHandler;
import vn.edu.bku.smart_emobility_backend.common.store.IdempotencyStore;
import vn.edu.bku.smart_emobility_backend.common.store.IdempotencyStore.CachedResponse;
import vn.edu.bku.smart_emobility_backend.common.store.InMemoryDataStore;
import vn.edu.bku.smart_emobility_backend.common.store.OperatorStateRegistry;
import vn.edu.bku.smart_emobility_backend.common.store.OperatorStateRegistry.DispatchOrderMeta;
import vn.edu.bku.smart_emobility_backend.common.store.OperatorStateRegistry.SpaceMeta;
import vn.edu.bku.smart_emobility_backend.common.store.OperatorStateRegistry.VehicleMeta;
import vn.edu.bku.smart_emobility_backend.common.store.StateCoordinator;
import vn.edu.bku.smart_emobility_backend.common.store.StoreSnapshot;
import vn.edu.bku.smart_emobility_backend.dto.operator.*;
import vn.edu.bku.smart_emobility_backend.dto.operator.DispatchCandidatesResponseDTO.DestinationOptionDTO;
import vn.edu.bku.smart_emobility_backend.dto.operator.DispatchOrderDTO.DispatchOrderListResponseDTO;

@Service
public class DispatchService {
    private static final String PATH_DISPATCH_ORDERS = "/api/operator/dispatch-orders";

    private final InMemoryDataStore dataStore;
    private final StateCoordinator stateCoordinator;
    private final OperatorStateRegistry stateRegistry;
    private final IdempotencyStore idempotencyStore;
    private final MonitoringAggregator monitoringAggregator;
    private final InterventionService interventionService;
    private final Clock clock;
    private final AtomicLong orderSeq = new AtomicLong(1);

    public DispatchService(
            InMemoryDataStore dataStore,
            StateCoordinator stateCoordinator,
            OperatorStateRegistry stateRegistry,
            IdempotencyStore idempotencyStore,
            MonitoringAggregator monitoringAggregator,
            InterventionService interventionService,
            Clock clock) {
        this.dataStore = dataStore;
        this.stateCoordinator = stateCoordinator;
        this.stateRegistry = stateRegistry;
        this.idempotencyStore = idempotencyStore;
        this.monitoringAggregator = monitoringAggregator;
        this.interventionService = interventionService;
        this.clock = clock;
    }

    public DispatchCandidatesResponseDTO getDispatchCandidates(
            String sourceHubId,
            TransportMode transportMode) {
        if (sourceHubId == null || sourceHubId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Tham số sourceHubId là bắt buộc",
                    List.of(new FieldErrorDetail("sourceHubId", "Không được để trống")));
        }
        if (transportMode == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Tham số transportMode là bắt buộc",
                    List.of(new FieldErrorDetail("transportMode", "Không được để trống")));
        }

        StoreSnapshot snapshot = dataStore.snapshot();
        findHubOrThrow(snapshot, sourceHubId, "sourceHubId");
        Instant now = clock.instant();

        List<OperatorVehicleDTO> eligibleVehicles = snapshot.vehicles().stream()
                .filter(v -> sourceHubId.equals(v.hubId()))
                .filter(v -> isVehicleAtOccupiedSourceSpace(v, sourceHubId, snapshot))
                .sorted(Comparator.comparing(Vehicle::vehicleId))
                .map(v -> monitoringAggregator.toOperatorVehicleDTO(v, snapshot, now))
                .filter(dto -> dto.dispatchModes().contains(transportMode))
                .toList();

        List<DestinationOptionDTO> destinationOptions = snapshot.hubs().stream()
                .filter(h -> !sourceHubId.equals(h.hubId()))
                .map(h -> {
                    HubMetricsDTO metrics = monitoringAggregator.computeHubMetrics(h, snapshot, now);
                    return new DestinationOptionDTO(h.hubId(), h.name(), metrics.freeSpaceCount());
                })
                .filter(opt -> opt.freeSpaceCount() > 0)
                .sorted(Comparator.comparingInt(DestinationOptionDTO::freeSpaceCount).reversed()
                        .thenComparing(DestinationOptionDTO::hubId))
                .toList();

        return new DispatchCandidatesResponseDTO(
                snapshot.version(),
                sourceHubId,
                transportMode,
                eligibleVehicles,
                destinationOptions);
    }

    public synchronized DispatchOrderDTO createDispatchOrder(
            String actorId,
            String rawIdempotencyKey,
            CreateDispatchOrderRequestDTO request) {
        String idempotencyKey = IdempotencyStore.requireValidUuidHeader(rawIdempotencyKey);
        validateCreateRequestShape(request);

        String sourceHubId = request.getSourceHubId().trim();
        String destinationHubId = request.getDestinationHubId().trim();
        List<String> vehicleIds = request.getVehicleIds().stream().map(String::trim).toList();
        TransportMode transportMode = parseTransportMode(request.getTransportMode());
        String reason = request.getReason().trim();

        String canonicalPayload = idempotencyStore.canonicalizePayload(request);
        Optional<CachedResponse> replay = idempotencyStore.checkReplay(
                actorId, "POST", PATH_DISPATCH_ORDERS, idempotencyKey, canonicalPayload);
        if (replay.isPresent()) {
            return (DispatchOrderDTO) replay.get().body();
        }

        StoreSnapshot snapshot = dataStore.snapshot();
        findHubOrThrow(snapshot, sourceHubId, "sourceHubId");
        findHubOrThrow(snapshot, destinationHubId, "destinationHubId");

        if (sourceHubId.equals(destinationHubId)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION",
                    "Hub nguồn và Hub đích phải khác nhau",
                    List.of(new FieldErrorDetail("destinationHubId", "Không được trùng với sourceHubId")));
        }

        Instant now = clock.instant();
        List<Vehicle> targetVehicles = new ArrayList<>();
        List<String> sourceSpaceIdsToRelease = new ArrayList<>();

        for (int i = 0; i < vehicleIds.size(); i++) {
            final int idx = i;
            String vId = vehicleIds.get(i);
            Vehicle v = snapshot.vehicles().stream()
                    .filter(x -> x.vehicleId().equals(vId))
                    .findFirst()
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                            "Không tìm thấy phương tiện: " + vId,
                            List.of(new FieldErrorDetail("vehicleIds[" + idx + "]", "Không tìm thấy phương tiện"))));

            if (!sourceHubId.equals(v.hubId())) {
                throw new ApiException(HttpStatus.CONFLICT, "RESOURCE_BUSY",
                        "Phương tiện " + vId + " hiện không nằm tại Hub nguồn " + sourceHubId,
                        List.of(new FieldErrorDetail("vehicleIds[" + i + "]", "Không thuộc Hub nguồn")));
            }

            Optional<ParkingSpace> occupiedSpace = findOccupiedSpaceForVehicle(v, sourceHubId, snapshot);
            if (occupiedSpace.isEmpty()) {
                throw new ApiException(HttpStatus.CONFLICT, "RESOURCE_BUSY",
                        "Phương tiện " + vId + " không nằm trong vị trí đỗ OCCUPIED hợp lệ tại Hub nguồn",
                        List.of(new FieldErrorDetail("vehicleIds[" + i + "]", "Không nằm tại ô đỗ OCCUPIED nguồn")));
            }

            OperatorVehicleDTO vDto = monitoringAggregator.toOperatorVehicleDTO(v, snapshot, now);
            if (vDto.pendingOperationId() != null
                    || v.status() == VehicleStatus.IN_USE
                    || v.status() == VehicleStatus.IN_TRANSIT
                    || v.status() == VehicleStatus.RESERVED
                    || vDto.activeUsage().rideId() != null
                    || vDto.activeUsage().reservationId() != null
                    || vDto.activeUsage().chargingRequestId() != null) {
                throw new ApiException(HttpStatus.CONFLICT, "RESOURCE_BUSY",
                        "Phương tiện " + vId + " đang bận hoặc có phiên đang hoạt động",
                        List.of(new FieldErrorDetail("vehicleIds[" + i + "]", "Phương tiện đang bận")));
            }

            if (!vDto.dispatchModes().contains(transportMode)) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION",
                        "Phương tiện " + vId + " không đủ điều kiện điều phối theo chế độ " + transportMode,
                        List.of(new FieldErrorDetail("vehicleIds[" + i + "]",
                                "Không đáp ứng điều kiện pin hoặc trạng thái cho " + transportMode)));
            }

            targetVehicles.add(v);
            sourceSpaceIdsToRelease.add(occupiedSpace.get().id());
        }

        // Find available unblocked parking spaces at destinationHubId sorted by spaceId
        List<ParkingSpace> availableDestSpaces = findAvailableUnblockedSpaces(destinationHubId, snapshot);
        if (availableDestSpaces.size() < vehicleIds.size()) {
            throw new ApiException(HttpStatus.CONFLICT, "DESTINATION_HUB_FULL",
                    "Hub đích " + destinationHubId + " không đủ chỗ đỗ trống (cần "
                            + vehicleIds.size() + ", còn " + availableDestSpaces.size() + ")");
        }

        List<ParkingSpace> selectedDestSpaces = availableDestSpaces.subList(0, vehicleIds.size());
        List<String> reservedDestSpaceIds = selectedDestSpaces.stream().map(ParkingSpace::id).toList();

        long seq = orderSeq.getAndIncrement();
        String orderId = String.format("dispatch-%04d", seq);

        synchronized (dataStore) {
            long baseVer = dataStore.version();
            boolean committed = stateCoordinator.commit(baseVer, store -> {
                // 1. Release source parking spaces
                for (String srcSpaceId : sourceSpaceIdsToRelease) {
                    ParkingSpace srcSpace = store.findById(ParkingSpace.class, srcSpaceId)
                            .orElseThrow(() -> new IllegalStateException("Source space missing"));
                    SpaceMeta srcMeta = stateRegistry.getSpaceMeta(srcSpaceId);
                    long newSrcSpaceVer = (srcMeta != null ? srcMeta.version() : 0L) + 1;
                    store.put(new ParkingSpace(srcSpace.id(), srcSpace.hubId(), ParkingSpaceStatus.AVAILABLE));
                    stateRegistry.putSpaceMeta(srcSpaceId, new SpaceMeta(
                            false, null, null, null, null, newSrcSpaceVer, null, now));
                }

                // 2. Reserve destination parking spaces as DISPATCH_INBOUND
                for (ParkingSpace destSpace : selectedDestSpaces) {
                    SpaceMeta destMeta = stateRegistry.getSpaceMeta(destSpace.id());
                    long newDestSpaceVer = (destMeta != null ? destMeta.version() : 0L) + 1;
                    store.put(new ParkingSpace(destSpace.id(), destSpace.hubId(), ParkingSpaceStatus.RESERVED));
                    stateRegistry.putSpaceMeta(destSpace.id(), new SpaceMeta(
                            false,
                            SpaceReservationKind.DISPATCH_INBOUND,
                            null,
                            null,
                            orderId,
                            newDestSpaceVer,
                            null,
                            now));
                }

                // 3. Move vehicles to IN_TRANSIT with hubId=null and parkingSpaceId=null
                for (Vehicle v : targetVehicles) {
                    long newVehicleVer = v.version() + 1;
                    store.put(new Vehicle(
                            v.vehicleId(),
                            null,
                            null,
                            v.vehicleType(),
                            v.batteryPct(),
                            v.batteryCapacityKwh(),
                            v.maxChargingPowerKw(),
                            VehicleStatus.IN_TRANSIT,
                            v.telemetryAt(),
                            newVehicleVer,
                            v.ownership(),
                            v.ownerAccountId()));
                }

                // 4. Save DispatchOrder and its metadata
                DispatchOrder domainOrder = new DispatchOrder(
                        orderId,
                        vehicleIds.get(0),
                        sourceHubId,
                        destinationHubId,
                        now,
                        DispatchOrderStatus.IN_TRANSIT);
                store.put(domainOrder);

                stateRegistry.putDispatchMeta(orderId, new DispatchOrderMeta(
                        vehicleIds,
                        reservedDestSpaceIds,
                        transportMode,
                        reason,
                        actorId,
                        now,
                        null,
                        null,
                        0L,
                        seq));
            });

            if (!committed) {
                throw new IllegalStateException("Concurrent state modification while creating dispatch order");
            }
        }

        DispatchOrderDTO resultDto = getDispatchOrder(orderId);
        idempotencyStore.recordAccepted(
                actorId, "POST", PATH_DISPATCH_ORDERS, idempotencyKey, canonicalPayload, HttpStatus.CREATED, resultDto);
        return resultDto;
    }

    public DispatchOrderListResponseDTO listDispatchOrders(
            OperatorDispatchStatus statusFilter,
            String hubIdFilter) {
        StoreSnapshot snapshot = dataStore.snapshot();
        if (hubIdFilter != null) {
            findHubOrThrow(snapshot, hubIdFilter, "hubId");
        }

        List<DispatchOrderDTO> items = snapshot.dispatchOrders().stream()
                .filter(d -> hubIdFilter == null
                        || hubIdFilter.equals(d.sourceHubId())
                        || hubIdFilter.equals(d.destinationHubId()))
                .map(this::toDtoWithSequence)
                .filter(pair -> statusFilter == null || statusFilter == pair.dto().status())
                .sorted(Comparator.comparing((OrderWithSeq p) -> p.createdAt()).reversed()
                        .thenComparing(Comparator.comparingLong(OrderWithSeq::seq).reversed())
                        .thenComparing(Comparator.comparing((OrderWithSeq p) -> p.dto().dispatchOrderId()).reversed()))
                .map(OrderWithSeq::dto)
                .toList();

        return new DispatchOrderListResponseDTO(items);
    }

    public DispatchOrderDTO getDispatchOrder(String orderId) {
        StoreSnapshot snapshot = dataStore.snapshot();
        DispatchOrder order = snapshot.dispatchOrders().stream()
                .filter(d -> d.id().equals(orderId))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                        "Không tìm thấy lệnh điều phối: " + orderId));
        return toDtoWithSequence(order).dto();
    }

    public synchronized DispatchOrderDTO confirmArrival(
            String actorId,
            String rawIdempotencyKey,
            String orderId,
            DispatchVersionRequestDTO request) {
        String idempotencyKey = IdempotencyStore.requireValidUuidHeader(rawIdempotencyKey);
        validateVersionRequestShape(request);
        String path = PATH_DISPATCH_ORDERS + "/" + orderId + "/arrival";
        String canonicalPayload = idempotencyStore.canonicalizePayload(request);

        Optional<CachedResponse> replay = idempotencyStore.checkReplay(
                actorId, "POST", path, idempotencyKey, canonicalPayload);
        if (replay.isPresent()) {
            return (DispatchOrderDTO) replay.get().body();
        }

        StoreSnapshot snapshot = dataStore.snapshot();
        DispatchOrder order = snapshot.dispatchOrders().stream()
                .filter(d -> d.id().equals(orderId))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                        "Không tìm thấy lệnh điều phối: " + orderId));

        OrderWithSeq currentPair = toDtoWithSequence(order);
        DispatchOrderDTO currentDto = currentPair.dto();

        // Terminal retry per Section 2: if already COMPLETED, return 200 current object without repeating side effects
        if (currentDto.status() == OperatorDispatchStatus.COMPLETED) {
            idempotencyStore.recordAccepted(actorId, "POST", path, idempotencyKey, canonicalPayload, HttpStatus.OK, currentDto);
            return currentDto;
        }
        if (currentDto.status() == OperatorDispatchStatus.CANCELLED) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE",
                    "Lệnh điều phối đã bị hủy, không thể xác nhận đến đích");
        }

        if (currentDto.version() != request.getExpectedVersion()) {
            throw new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT",
                    "Phiên bản lệnh điều phối đã thay đổi, vui lòng tải lại trước khi xác nhận");
        }

        DispatchOrderMeta meta = stateRegistry.getDispatchMeta(orderId);
        List<String> vehicleIds = currentDto.vehicleIds();
        List<String> inboundSpaceIds = (meta != null && !meta.reservedDestinationSpaceIds().isEmpty())
                ? meta.reservedDestinationSpaceIds()
                : findInboundSpacesForOrder(orderId, order.destinationHubId(), snapshot);

        if (inboundSpaceIds.size() < vehicleIds.size()) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE",
                    "Các vị trí đỗ đã giữ cho lệnh điều phối không còn hợp lệ tại Hub đích");
        }

        // Verify vehicles are still IN_TRANSIT and have no pending operation
        for (String vId : vehicleIds) {
            Vehicle v = snapshot.vehicles().stream()
                    .filter(x -> x.vehicleId().equals(vId))
                    .findFirst()
                    .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "INVALID_STATE",
                            "Phương tiện trong lệnh điều phối không tồn tại: " + vId));
            if (v.status() != VehicleStatus.IN_TRANSIT) {
                throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE",
                        "Phương tiện " + vId + " không còn ở trạng thái IN_TRANSIT");
            }
            VehicleMeta vMeta = stateRegistry.getVehicleMeta(vId);
            if (vMeta != null && vMeta.pendingOperationId() != null) {
                throw new ApiException(HttpStatus.CONFLICT, "RESOURCE_BUSY",
                        "Phương tiện " + vId + " đang có lệnh can thiệp chờ xử lý");
            }
        }

        // Verify reserved inbound spaces are still RESERVED for this order and not blocked
        for (String spaceId : inboundSpaceIds) {
            ParkingSpace sp = snapshot.parkingSpaces().stream()
                    .filter(s -> s.id().equals(spaceId))
                    .findFirst()
                    .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "INVALID_STATE",
                            "Không tìm thấy vị trí đỗ đích: " + spaceId));
            OperatorParkingSpaceDTO spDto = monitoringAggregator.toOperatorParkingSpaceDTO(sp, snapshot);
            if (!"RESERVED".equals(spDto.status())
                    || spDto.blocked()
                    || spDto.reservationKind() != SpaceReservationKind.DISPATCH_INBOUND
                    || !orderId.equals(spDto.dispatchOrderId())) {
                throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE",
                        "Vị trí đỗ đích " + spaceId + " đã bị thay đổi trạng thái trái quy tắc");
            }
        }

        Instant now = clock.instant();
        long newOrderVersion = currentDto.version() + 1;

        synchronized (dataStore) {
            long baseVer = dataStore.version();
            boolean committed = stateCoordinator.commit(baseVer, store -> {
                for (int i = 0; i < vehicleIds.size(); i++) {
                    String vId = vehicleIds.get(i);
                    String destSpaceId = inboundSpaceIds.get(i);

                    Vehicle v = store.findById(Vehicle.class, vId)
                            .orElseThrow(() -> new IllegalStateException("Vehicle missing during arrival commit"));
                    VehicleMeta vMeta = stateRegistry.getOrDefaultVehicleMeta(
                            vId, v.telemetryAt(), null, true);
                    Set<String> causes = new LinkedHashSet<>(vMeta.maintenanceCauses());

                    Vehicle forEval = new Vehicle(
                            v.vehicleId(), order.destinationHubId(), destSpaceId, v.vehicleType(),
                            v.batteryPct(), v.batteryCapacityKwh(), v.maxChargingPowerKw(),
                            VehicleStatus.AVAILABLE, v.telemetryAt(), v.version(),
                            v.ownership(), v.ownerAccountId());
                    VehicleStatus restoredStatus = interventionService.evaluateRestoredVehicleStatus(
                            store, forEval, causes, v.batteryPct(), vMeta.connectionStatus(), now);

                    long newVehicleVer = v.version() + 1;
                    store.put(new Vehicle(
                            v.vehicleId(),
                            order.destinationHubId(),
                            destSpaceId,
                            v.vehicleType(),
                            v.batteryPct(),
                            v.batteryCapacityKwh(),
                            v.maxChargingPowerKw(),
                            restoredStatus,
                            v.telemetryAt(),
                            newVehicleVer,
                            v.ownership(),
                            v.ownerAccountId()));

                    stateRegistry.putVehicleMeta(vId, new VehicleMeta(
                            vMeta.batterySource(),
                            vMeta.batteryUpdatedAt(),
                            vMeta.connectionStatus(),
                            vMeta.location(),
                            true,
                            null,
                            causes));

                    ParkingSpace destSpace = store.findById(ParkingSpace.class, destSpaceId)
                            .orElseThrow(() -> new IllegalStateException("Dest space missing"));
                    SpaceMeta sMeta = stateRegistry.getSpaceMeta(destSpaceId);
                    long newSpaceVer = (sMeta != null ? sMeta.version() : 0L) + 1;
                    store.put(new ParkingSpace(destSpace.id(), destSpace.hubId(), ParkingSpaceStatus.OCCUPIED));
                    stateRegistry.putSpaceMeta(destSpaceId, new SpaceMeta(
                            false, null, vId, null, null, newSpaceVer, null, now));
                }

                store.put(new DispatchOrder(
                        order.id(),
                        order.vehicleId(),
                        order.sourceHubId(),
                        order.destinationHubId(),
                        order.createdAt(),
                        DispatchOrderStatus.COMPLETED));

                stateRegistry.putDispatchMeta(orderId, new DispatchOrderMeta(
                        vehicleIds,
                        inboundSpaceIds,
                        currentDto.transportMode(),
                        currentDto.reason(),
                        currentDto.actorId(),
                        now,
                        now,
                        null,
                        newOrderVersion,
                        currentPair.seq()));
            });

            if (!committed) {
                throw new IllegalStateException("Concurrent state modification during dispatch arrival commit");
            }
        }

        DispatchOrderDTO updatedDto = getDispatchOrder(orderId);
        idempotencyStore.recordAccepted(actorId, "POST", path, idempotencyKey, canonicalPayload, HttpStatus.OK, updatedDto);
        return updatedDto;
    }

    public synchronized DispatchOrderDTO cancelDispatchOrder(
            String actorId,
            String rawIdempotencyKey,
            String orderId,
            DispatchVersionRequestDTO request) {
        String idempotencyKey = IdempotencyStore.requireValidUuidHeader(rawIdempotencyKey);
        validateVersionRequestShape(request);
        String path = PATH_DISPATCH_ORDERS + "/" + orderId + "/cancel";
        String canonicalPayload = idempotencyStore.canonicalizePayload(request);

        Optional<CachedResponse> replay = idempotencyStore.checkReplay(
                actorId, "POST", path, idempotencyKey, canonicalPayload);
        if (replay.isPresent()) {
            return (DispatchOrderDTO) replay.get().body();
        }

        StoreSnapshot snapshot = dataStore.snapshot();
        DispatchOrder order = snapshot.dispatchOrders().stream()
                .filter(d -> d.id().equals(orderId))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                        "Không tìm thấy lệnh điều phối: " + orderId));

        OrderWithSeq currentPair = toDtoWithSequence(order);
        DispatchOrderDTO currentDto = currentPair.dto();

        // Terminal retry per Section 2: if already CANCELLED, return 200 current object
        if (currentDto.status() == OperatorDispatchStatus.CANCELLED) {
            idempotencyStore.recordAccepted(actorId, "POST", path, idempotencyKey, canonicalPayload, HttpStatus.OK, currentDto);
            return currentDto;
        }
        if (currentDto.status() == OperatorDispatchStatus.COMPLETED) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE",
                    "Lệnh điều phối đã hoàn tất, không thể hủy");
        }

        if (currentDto.version() != request.getExpectedVersion()) {
            throw new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT",
                    "Phiên bản lệnh điều phối đã thay đổi, vui lòng tải lại trước khi hủy");
        }

        DispatchOrderMeta meta = stateRegistry.getDispatchMeta(orderId);
        List<String> vehicleIds = currentDto.vehicleIds();
        List<String> inboundSpaceIds = (meta != null && !meta.reservedDestinationSpaceIds().isEmpty())
                ? meta.reservedDestinationSpaceIds()
                : findInboundSpacesForOrder(orderId, order.destinationHubId(), snapshot);

        for (String vId : vehicleIds) {
            Vehicle v = snapshot.vehicles().stream()
                    .filter(x -> x.vehicleId().equals(vId))
                    .findFirst()
                    .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "INVALID_STATE",
                            "Phương tiện trong lệnh điều phối không tồn tại: " + vId));
            if (v.status() != VehicleStatus.IN_TRANSIT) {
                throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE",
                        "Phương tiện " + vId + " không còn ở trạng thái IN_TRANSIT");
            }
            VehicleMeta vMeta = stateRegistry.getVehicleMeta(vId);
            if (vMeta != null && vMeta.pendingOperationId() != null) {
                throw new ApiException(HttpStatus.CONFLICT, "RESOURCE_BUSY",
                        "Phương tiện " + vId + " đang có lệnh can thiệp chờ xử lý");
            }
        }

        // Check source hub has enough available unblocked spaces
        List<ParkingSpace> availableSourceSpaces = findAvailableUnblockedSpaces(order.sourceHubId(), snapshot);
        if (availableSourceSpaces.size() < vehicleIds.size()) {
            throw new ApiException(HttpStatus.CONFLICT, "SOURCE_HUB_FULL",
                    "Hub nguồn " + order.sourceHubId() + " không còn đủ chỗ đỗ trống để nhận lại xe (cần "
                            + vehicleIds.size() + ", còn " + availableSourceSpaces.size() + ")");
        }

        List<ParkingSpace> selectedSourceSpaces = availableSourceSpaces.subList(0, vehicleIds.size());
        Instant now = clock.instant();
        long newOrderVersion = currentDto.version() + 1;

        synchronized (dataStore) {
            long baseVer = dataStore.version();
            boolean committed = stateCoordinator.commit(baseVer, store -> {
                // 1. Release reserved destination inbound spaces back to AVAILABLE
                for (String destSpaceId : inboundSpaceIds) {
                    ParkingSpace destSpace = store.findById(ParkingSpace.class, destSpaceId).orElse(null);
                    if (destSpace != null) {
                        SpaceMeta sMeta = stateRegistry.getSpaceMeta(destSpaceId);
                        long newSpaceVer = (sMeta != null ? sMeta.version() : 0L) + 1;
                        store.put(new ParkingSpace(destSpace.id(), destSpace.hubId(), ParkingSpaceStatus.AVAILABLE));
                        stateRegistry.putSpaceMeta(destSpaceId, new SpaceMeta(
                                false, null, null, null, null, newSpaceVer, null, now));
                    }
                }

                // 2. Return vehicles to source hub slots and restore vehicle status
                for (int i = 0; i < vehicleIds.size(); i++) {
                    String vId = vehicleIds.get(i);
                    ParkingSpace srcSpace = selectedSourceSpaces.get(i);

                    Vehicle v = store.findById(Vehicle.class, vId)
                            .orElseThrow(() -> new IllegalStateException("Vehicle missing during cancel commit"));
                    VehicleMeta vMeta = stateRegistry.getOrDefaultVehicleMeta(
                            vId, v.telemetryAt(), null, true);
                    Set<String> causes = new LinkedHashSet<>(vMeta.maintenanceCauses());

                    Vehicle forEval = new Vehicle(
                            v.vehicleId(), order.sourceHubId(), srcSpace.id(), v.vehicleType(),
                            v.batteryPct(), v.batteryCapacityKwh(), v.maxChargingPowerKw(),
                            VehicleStatus.AVAILABLE, v.telemetryAt(), v.version(),
                            v.ownership(), v.ownerAccountId());
                    VehicleStatus restoredStatus = interventionService.evaluateRestoredVehicleStatus(
                            store, forEval, causes, v.batteryPct(), vMeta.connectionStatus(), now);

                    long newVehicleVer = v.version() + 1;
                    store.put(new Vehicle(
                            v.vehicleId(),
                            order.sourceHubId(),
                            srcSpace.id(),
                            v.vehicleType(),
                            v.batteryPct(),
                            v.batteryCapacityKwh(),
                            v.maxChargingPowerKw(),
                            restoredStatus,
                            v.telemetryAt(),
                            newVehicleVer,
                            v.ownership(),
                            v.ownerAccountId()));

                    stateRegistry.putVehicleMeta(vId, new VehicleMeta(
                            vMeta.batterySource(),
                            vMeta.batteryUpdatedAt(),
                            vMeta.connectionStatus(),
                            vMeta.location(),
                            true,
                            null,
                            causes));

                    SpaceMeta srcMeta = stateRegistry.getSpaceMeta(srcSpace.id());
                    long newSrcSpaceVer = (srcMeta != null ? srcMeta.version() : 0L) + 1;
                    store.put(new ParkingSpace(srcSpace.id(), srcSpace.hubId(), ParkingSpaceStatus.OCCUPIED));
                    stateRegistry.putSpaceMeta(srcSpace.id(), new SpaceMeta(
                            false, null, vId, null, null, newSrcSpaceVer, null, now));
                }

                // 3. Mark order as CANCELLED
                store.put(new DispatchOrder(
                        order.id(),
                        order.vehicleId(),
                        order.sourceHubId(),
                        order.destinationHubId(),
                        order.createdAt(),
                        DispatchOrderStatus.CANCELLED));

                stateRegistry.putDispatchMeta(orderId, new DispatchOrderMeta(
                        vehicleIds,
                        List.of(),
                        currentDto.transportMode(),
                        currentDto.reason(),
                        currentDto.actorId(),
                        now,
                        null,
                        now,
                        newOrderVersion,
                        currentPair.seq()));
            });

            if (!committed) {
                throw new IllegalStateException("Concurrent state modification during dispatch cancel commit");
            }
        }

        DispatchOrderDTO updatedDto = getDispatchOrder(orderId);
        idempotencyStore.recordAccepted(actorId, "POST", path, idempotencyKey, canonicalPayload, HttpStatus.OK, updatedDto);
        return updatedDto;
    }

    private boolean isVehicleAtOccupiedSourceSpace(Vehicle v, String sourceHubId, StoreSnapshot snapshot) {
        return findOccupiedSpaceForVehicle(v, sourceHubId, snapshot).isPresent();
    }

    private Optional<ParkingSpace> findOccupiedSpaceForVehicle(Vehicle v, String hubId, StoreSnapshot snapshot) {
        if (v.parkingSpaceId() != null) {
            Optional<ParkingSpace> explicit = snapshot.parkingSpaces().stream()
                    .filter(s -> s.id().equals(v.parkingSpaceId()) && hubId.equals(s.hubId()))
                    .findFirst();
            if (explicit.isPresent()) {
                OperatorParkingSpaceDTO dto = monitoringAggregator.toOperatorParkingSpaceDTO(explicit.get(), snapshot);
                if ("OCCUPIED".equals(dto.status()) && v.vehicleId().equals(dto.vehicleId())) {
                    return explicit;
                }
            }
        }
        return snapshot.parkingSpaces().stream()
                .filter(s -> hubId.equals(s.hubId()))
                .filter(s -> {
                    OperatorParkingSpaceDTO dto = monitoringAggregator.toOperatorParkingSpaceDTO(s, snapshot);
                    return "OCCUPIED".equals(dto.status()) && v.vehicleId().equals(dto.vehicleId());
                })
                .findFirst();
    }

    private List<ParkingSpace> findAvailableUnblockedSpaces(String hubId, StoreSnapshot snapshot) {
        return snapshot.parkingSpaces().stream()
                .filter(s -> hubId.equals(s.hubId()))
                .sorted(Comparator.comparing(ParkingSpace::id))
                .filter(s -> {
                    OperatorParkingSpaceDTO dto = monitoringAggregator.toOperatorParkingSpaceDTO(s, snapshot);
                    return "AVAILABLE".equals(dto.status()) && !dto.blocked() && dto.pendingOperationId() == null;
                })
                .toList();
    }

    private List<String> findInboundSpacesForOrder(String orderId, String destHubId, StoreSnapshot snapshot) {
        return snapshot.parkingSpaces().stream()
                .filter(s -> destHubId.equals(s.hubId()))
                .sorted(Comparator.comparing(ParkingSpace::id))
                .filter(s -> {
                    OperatorParkingSpaceDTO dto = monitoringAggregator.toOperatorParkingSpaceDTO(s, snapshot);
                    return "RESERVED".equals(dto.status())
                            && dto.reservationKind() == SpaceReservationKind.DISPATCH_INBOUND
                            && orderId.equals(dto.dispatchOrderId());
                })
                .map(ParkingSpace::id)
                .toList();
    }

    private OrderWithSeq toDtoWithSequence(DispatchOrder order) {
        DispatchOrderMeta meta = stateRegistry.getDispatchMeta(order.id());
        List<String> vehicleIds = (meta != null && !meta.vehicleIds().isEmpty())
                ? meta.vehicleIds()
                : (order.vehicleId() != null ? List.of(order.vehicleId()) : List.of());
        TransportMode mode = (meta != null && meta.transportMode() != null)
                ? meta.transportMode()
                : TransportMode.SELF_DRIVE;
        String reason = (meta != null && meta.reason() != null) ? meta.reason() : "Điều phối phương tiện";
        String actorId = (meta != null && meta.actorId() != null) ? meta.actorId() : "system";
        Instant updatedAt = (meta != null && meta.updatedAt() != null) ? meta.updatedAt() : order.createdAt();
        Instant arrivedAt = meta != null ? meta.arrivedAt() : null;
        Instant cancelledAt = meta != null ? meta.cancelledAt() : null;
        long version = meta != null ? meta.version() : 0L;
        long seq = meta != null ? meta.sequenceOrder() : 0L;

        DispatchOrderDTO dto = new DispatchOrderDTO(
                order.id(),
                order.sourceHubId(),
                order.destinationHubId(),
                vehicleIds,
                mode,
                reason,
                OperatorDispatchStatus.fromDomain(order.status()),
                actorId,
                GlobalExceptionHandler.formatTimestamp(order.createdAt()),
                GlobalExceptionHandler.formatTimestamp(updatedAt),
                GlobalExceptionHandler.formatTimestamp(arrivedAt),
                GlobalExceptionHandler.formatTimestamp(cancelledAt),
                version);

        return new OrderWithSeq(dto, order.createdAt() != null ? order.createdAt() : Instant.EPOCH, seq);
    }

    private MobilityHub findHubOrThrow(StoreSnapshot snapshot, String hubId, String fieldName) {
        return snapshot.hubs().stream()
                .filter(h -> Objects.equals(h.hubId(), hubId))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                        "Không tìm thấy Mobility Hub: " + hubId,
                        List.of(new FieldErrorDetail(fieldName, "Hub không tồn tại"))));
    }

    private TransportMode parseTransportMode(String raw) {
        try {
            return TransportMode.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "transportMode không hợp lệ: " + raw,
                    List.of(new FieldErrorDetail("transportMode", "Giá trị enum không hợp lệ")));
        }
    }

    private void validateCreateRequestShape(CreateDispatchOrderRequestDTO request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request body là bắt buộc");
        }
        if (!request.unknownFields().isEmpty()) {
            String firstUnknown = request.unknownFields().keySet().iterator().next();
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Trường không được hỗ trợ trong request: " + firstUnknown,
                    List.of(new FieldErrorDetail(firstUnknown, "Trường không được phép")));
        }
        List<FieldErrorDetail> errors = new ArrayList<>();
        if (request.getSourceHubId() == null || request.getSourceHubId().isBlank()
                || request.getSourceHubId().trim().length() > 128) {
            errors.add(new FieldErrorDetail("sourceHubId", "Không được để trống (1..128 ký tự)"));
        }
        if (request.getDestinationHubId() == null || request.getDestinationHubId().isBlank()
                || request.getDestinationHubId().trim().length() > 128) {
            errors.add(new FieldErrorDetail("destinationHubId", "Không được để trống (1..128 ký tự)"));
        }
        if (request.getTransportMode() == null || request.getTransportMode().isBlank()) {
            errors.add(new FieldErrorDetail("transportMode", "Không được để trống"));
        }
        if (request.getReason() == null || request.getReason().trim().isEmpty()
                || request.getReason().trim().length() > 500) {
            errors.add(new FieldErrorDetail("reason", "Lý do sau khi trim phải từ 1 đến 500 ký tự"));
        }
        if (request.getVehicleIds() == null || request.getVehicleIds().isEmpty()
                || request.getVehicleIds().size() > 100) {
            errors.add(new FieldErrorDetail("vehicleIds", "Danh sách xe phải có từ 1 đến 100 phần tử"));
        } else {
            Set<String> seen = new LinkedHashSet<>();
            for (int i = 0; i < request.getVehicleIds().size(); i++) {
                String vId = request.getVehicleIds().get(i);
                if (vId == null || vId.isBlank() || vId.trim().length() > 128) {
                    errors.add(new FieldErrorDetail("vehicleIds[" + i + "]", "Mã xe không hợp lệ"));
                } else if (!seen.add(vId.trim())) {
                    errors.add(new FieldErrorDetail("vehicleIds[" + i + "]", "Mã xe bị trùng lặp trong danh sách"));
                }
            }
        }
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Dữ liệu không hợp lệ", errors);
        }
    }

    private void validateVersionRequestShape(DispatchVersionRequestDTO request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request body là bắt buộc");
        }
        if (!request.unknownFields().isEmpty()) {
            String firstUnknown = request.unknownFields().keySet().iterator().next();
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Trường không được hỗ trợ trong request: " + firstUnknown,
                    List.of(new FieldErrorDetail(firstUnknown, "Trường không được phép")));
        }
        if (request.getExpectedVersion() == null || request.getExpectedVersion() < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "expectedVersion phải là số nguyên >= 0",
                    List.of(new FieldErrorDetail("expectedVersion", "Bắt buộc và >= 0")));
        }
    }

    private record OrderWithSeq(DispatchOrderDTO dto, Instant createdAt, long seq) {
    }
}
