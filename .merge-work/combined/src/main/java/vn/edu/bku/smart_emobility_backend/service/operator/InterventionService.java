package vn.edu.bku.smart_emobility_backend.service.operator;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
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
import vn.edu.bku.smart_emobility_backend.common.store.OperatorStateRegistry.PortMeta;
import vn.edu.bku.smart_emobility_backend.common.store.OperatorStateRegistry.SpaceMeta;
import vn.edu.bku.smart_emobility_backend.common.store.OperatorStateRegistry.VehicleMeta;
import vn.edu.bku.smart_emobility_backend.common.store.StateCoordinator;
import vn.edu.bku.smart_emobility_backend.common.store.StoreSnapshot;
import vn.edu.bku.smart_emobility_backend.dto.operator.*;
import vn.edu.bku.smart_emobility_backend.dto.operator.InterventionResultDTO.AffectedResourceDTO;
import vn.edu.bku.smart_emobility_backend.service.operator.AuditLogService.AuditEntry;
import vn.edu.bku.smart_emobility_backend.service.operator.DeviceGateway.CommandOutcome;
import vn.edu.bku.smart_emobility_backend.service.operator.DeviceGateway.CommandType;

@Service
public class InterventionService {
    private static final String PATH_INTERVENTIONS = "/api/operator/interventions";

    private final InMemoryDataStore dataStore;
    private final StateCoordinator stateCoordinator;
    private final OperatorStateRegistry stateRegistry;
    private final IdempotencyStore idempotencyStore;
    private final AuditLogService auditLogService;
    private final OperationOrchestrator operationOrchestrator;
    private final MonitoringAggregator monitoringAggregator;
    private final Clock clock;
    private final double minBatteryPct;
    private final AtomicLong interventionSeq = new AtomicLong(1);

    public InterventionService(
            InMemoryDataStore dataStore,
            StateCoordinator stateCoordinator,
            OperatorStateRegistry stateRegistry,
            IdempotencyStore idempotencyStore,
            AuditLogService auditLogService,
            OperationOrchestrator operationOrchestrator,
            MonitoringAggregator monitoringAggregator,
            Clock clock,
            @Value("${app.search.min-battery-pct:20}") double minBatteryPct) {
        this.dataStore = dataStore;
        this.stateCoordinator = stateCoordinator;
        this.stateRegistry = stateRegistry;
        this.idempotencyStore = idempotencyStore;
        this.auditLogService = auditLogService;
        this.operationOrchestrator = operationOrchestrator;
        this.monitoringAggregator = monitoringAggregator;
        this.clock = clock;
        this.minBatteryPct = minBatteryPct;
    }

    public synchronized PendingEnvelopeDTO executeIntervention(
            String actorId,
            String rawIdempotencyKey,
            InterventionRequestDTO request) {
        String idempotencyKey = IdempotencyStore.requireValidUuidHeader(rawIdempotencyKey);
        validateRequestShape(request);

        ResourceType resourceType = parseResourceType(request.getResourceType());
        InterventionAction action = parseAction(request.getAction());
        String resourceId = request.getResourceId().trim();
        String reason = request.getReason().trim();
        long expectedVersion = request.getExpectedVersion();
        Map<String, Object> parameters = request.getParameters();

        validateParametersShape(action, parameters);

        String canonicalPayload = idempotencyStore.canonicalizePayload(request);
        Optional<CachedResponse> replay = idempotencyStore.checkReplay(
                actorId, "POST", PATH_INTERVENTIONS, idempotencyKey, canonicalPayload);
        if (replay.isPresent()) {
            return (PendingEnvelopeDTO) replay.get().body();
        }

        // Validate resource-action pairing (422 BUSINESS_RULE_VIOLATION if incompatible)
        if (!isResourceActionCompatible(resourceType, action)) {
            auditLogService.record(actorId, resourceType, resourceId, action.name(), reason,
                    null, AuditOutcome.REJECTED, "BUSINESS_RULE_VIOLATION");
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION",
                    "Hành động " + action + " không áp dụng cho loại tài nguyên " + resourceType);
        }

        StoreSnapshot snapshot = dataStore.snapshot();

        return switch (resourceType) {
            case VEHICLE -> handleVehicleIntervention(
                    actorId, idempotencyKey, canonicalPayload, resourceId, action, reason, expectedVersion, parameters, snapshot);
            case CHARGING_PORT -> handlePortIntervention(
                    actorId, idempotencyKey, canonicalPayload, resourceId, action, reason, expectedVersion, parameters, snapshot);
            case PARKING_SPACE -> handleSpaceIntervention(
                    actorId, idempotencyKey, canonicalPayload, resourceId, action, reason, expectedVersion, parameters, snapshot);
        };
    }

    private PendingEnvelopeDTO handleVehicleIntervention(
            String actorId,
            String idempotencyKey,
            String canonicalPayload,
            String vehicleId,
            InterventionAction action,
            String reason,
            long expectedVersion,
            Map<String, Object> parameters,
            StoreSnapshot snapshot) {
        Vehicle vehicle = snapshot.vehicles().stream()
                .filter(v -> v.vehicleId().equals(vehicleId))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                        "Không tìm thấy phương tiện: " + vehicleId));

        MobilityHub hub = vehicle.hubId() == null ? null
                : snapshot.hubs().stream().filter(h -> h.hubId().equals(vehicle.hubId())).findFirst().orElse(null);
        GeoPoint defaultLoc = hub != null ? hub.location() : null;
        boolean defaultLocked = vehicle.status() != VehicleStatus.IN_USE;
        VehicleMeta meta = stateRegistry.getOrDefaultVehicleMeta(
                vehicleId, vehicle.telemetryAt(), defaultLoc, defaultLocked);

        if (meta.pendingOperationId() != null) {
            rejectWithAudit(actorId, ResourceType.VEHICLE, vehicleId, action, reason,
                    HttpStatus.CONFLICT, "RESOURCE_BUSY", "Phương tiện đang có lệnh chờ xử lý");
        }
        if (vehicle.version() != expectedVersion) {
            rejectWithAudit(actorId, ResourceType.VEHICLE, vehicleId, action, reason,
                    HttpStatus.CONFLICT, "VERSION_CONFLICT",
                    "Phiên bản phương tiện đã thay đổi, vui lòng tải lại trước khi thao tác");
        }

        boolean hasActiveRide = vehicle.status() == VehicleStatus.IN_USE
                || snapshot.rides().stream().anyMatch(r -> vehicleId.equals(r.vehicleId()) && r.status() == RideStatus.ACTIVE);
        boolean isInTransit = vehicle.status() == VehicleStatus.IN_TRANSIT;
        Optional<Reservation> activeReservation = snapshot.reservations().stream()
                .filter(r -> vehicleId.equals(r.vehicleId()) && r.status() == ReservationStatus.ACTIVE)
                .findFirst();
        Optional<ChargingRequest> activeChargingReq = snapshot.chargingRequests().stream()
                .filter(cr -> vehicleId.equals(cr.vehicleId())
                        && (cr.status() == ChargingRequestStatus.PENDING
                                || cr.status() == ChargingRequestStatus.SCHEDULED
                                || cr.status() == ChargingRequestStatus.CHARGING))
                .findFirst();
        boolean hasBlockingIncident = snapshot.incidents().stream()
                .anyMatch(inc -> vehicleId.equals(inc.vehicleId())
                        && (inc.status() == IncidentStatus.OPEN || inc.status() == IncidentStatus.INVESTIGATING)
                        && inc.severity() != IncidentSeverity.LOW);

        CommandType commandType;
        Map<String, Object> commandPayload = new LinkedHashMap<>();

        switch (action) {
            case LOCK -> {
                if (hasActiveRide || isInTransit || activeReservation.isPresent() || activeChargingReq.isPresent()) {
                    rejectWithAudit(actorId, ResourceType.VEHICLE, vehicleId, action, reason,
                            HttpStatus.CONFLICT, "RESOURCE_BUSY",
                            "Không thể khóa phương tiện đang trong chuyến đi hoặc đang có phiên hoạt động");
                }
                commandType = Boolean.TRUE.equals(meta.isLocked()) ? CommandType.VERIFY_STATE : CommandType.LOCK;
                if (commandType == CommandType.VERIFY_STATE) {
                    commandPayload = buildVerifyStatePayload(action.name(), null, null, null);
                }
            }
            case UNLOCK -> {
                if (hasActiveRide || isInTransit || activeReservation.isPresent() || activeChargingReq.isPresent()) {
                    rejectWithAudit(actorId, ResourceType.VEHICLE, vehicleId, action, reason,
                            HttpStatus.CONFLICT, "RESOURCE_BUSY",
                            "Không thể mở khóa phương tiện đang bận");
                }
                if (vehicle.status() == VehicleStatus.MAINTENANCE || hasBlockingIncident) {
                    rejectWithAudit(actorId, ResourceType.VEHICLE, vehicleId, action, reason,
                            HttpStatus.CONFLICT, hasBlockingIncident ? "ACTIVE_INCIDENTS" : "INVALID_STATE",
                            "Không thể mở khóa phương tiện đang bảo trì hoặc có sự cố chưa giải quyết");
                }
                commandType = Boolean.FALSE.equals(meta.isLocked()) ? CommandType.VERIFY_STATE : CommandType.UNLOCK;
                if (commandType == CommandType.VERIFY_STATE) {
                    commandPayload = buildVerifyStatePayload(action.name(), null, null, null);
                }
            }
            case MARK_MAINTENANCE -> {
                if (hasActiveRide || isInTransit || activeReservation.isPresent() || activeChargingReq.isPresent()) {
                    rejectWithAudit(actorId, ResourceType.VEHICLE, vehicleId, action, reason,
                            HttpStatus.CONFLICT, "RESOURCE_BUSY",
                            "Không thể đưa phương tiện đang bận vào bảo trì");
                }
                commandType = CommandType.LOCK;
            }
            case CLEAR_MAINTENANCE -> {
                if (vehicle.status() != VehicleStatus.MAINTENANCE) {
                    rejectWithAudit(actorId, ResourceType.VEHICLE, vehicleId, action, reason,
                            HttpStatus.CONFLICT, "INVALID_STATE",
                            "Phương tiện không ở trạng thái MAINTENANCE");
                }
                boolean hasIncidentCause = meta.maintenanceCauses().stream().anyMatch(c -> c.startsWith("INCIDENT:"));
                if (hasBlockingIncident || hasIncidentCause) {
                    rejectWithAudit(actorId, ResourceType.VEHICLE, vehicleId, action, reason,
                            HttpStatus.CONFLICT, "ACTIVE_INCIDENTS",
                            "Phương tiện còn sự cố chưa được giải quyết");
                }
                commandType = CommandType.VERIFY_STATE;
                commandPayload = buildVerifyStatePayload(action.name(), null, null, null);
            }
            case CANCEL_RESERVATION -> {
                String resId = (String) parameters.get("reservationId");
                Reservation res = snapshot.reservations().stream()
                        .filter(r -> r.id().equals(resId))
                        .findFirst()
                        .orElseThrow(() -> {
                            auditLogService.record(actorId, ResourceType.VEHICLE, vehicleId, action.name(), reason,
                                    null, AuditOutcome.REJECTED, "BUSINESS_RULE_VIOLATION");
                            return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION",
                                    "Không tìm thấy lượt đặt xe: " + resId);
                        });
                if (!vehicleId.equals(res.vehicleId())) {
                    rejectWithAudit(actorId, ResourceType.VEHICLE, vehicleId, action, reason,
                            HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION",
                            "Lượt đặt xe không thuộc về phương tiện này");
                }
                if (res.status() != ReservationStatus.ACTIVE || hasActiveRide) {
                    rejectWithAudit(actorId, ResourceType.VEHICLE, vehicleId, action, reason,
                            HttpStatus.CONFLICT, "INVALID_STATE",
                            "Lượt đặt xe không còn ở trạng thái ACTIVE");
                }
                commandType = CommandType.VERIFY_STATE;
                commandPayload = buildVerifyStatePayload(action.name(), resId, null, null);
            }
            case CANCEL_CHARGING -> {
                String reqId = (String) parameters.get("chargingRequestId");
                ChargingRequest chgReq = snapshot.chargingRequests().stream()
                        .filter(cr -> cr.id().equals(reqId))
                        .findFirst()
                        .orElseThrow(() -> {
                            auditLogService.record(actorId, ResourceType.VEHICLE, vehicleId, action.name(), reason,
                                    null, AuditOutcome.REJECTED, "BUSINESS_RULE_VIOLATION");
                            return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION",
                                    "Không tìm thấy yêu cầu sạc: " + reqId);
                        });
                if (!vehicleId.equals(chgReq.vehicleId())) {
                    rejectWithAudit(actorId, ResourceType.VEHICLE, vehicleId, action, reason,
                            HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION",
                            "Yêu cầu sạc không thuộc về phương tiện này");
                }
                boolean hasAssignedPort = snapshot.chargingAllocations().stream()
                        .anyMatch(ca -> reqId.equals(ca.chargingRequestId())
                                && (ca.status() == ChargingAllocationStatus.RESERVED
                                        || ca.status() == ChargingAllocationStatus.ACTIVE));
                if (hasAssignedPort) {
                    rejectWithAudit(actorId, ResourceType.VEHICLE, vehicleId, action, reason,
                            HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION",
                            "Yêu cầu sạc đã được gán cổng sạc, vui lòng can thiệp trên CHARGING_PORT tương ứng");
                }
                if (chgReq.status() == ChargingRequestStatus.COMPLETED
                        || chgReq.status() == ChargingRequestStatus.CANCELLED) {
                    rejectWithAudit(actorId, ResourceType.VEHICLE, vehicleId, action, reason,
                            HttpStatus.CONFLICT, "INVALID_STATE",
                            "Yêu cầu sạc đã kết thúc hoặc đã bị hủy");
                }
                commandType = CommandType.VERIFY_STATE;
                commandPayload = buildVerifyStatePayload(action.name(), null, null, reqId);
            }
            case SET_BATTERY -> {
                if (hasActiveRide || isInTransit || activeChargingReq.isPresent()) {
                    rejectWithAudit(actorId, ResourceType.VEHICLE, vehicleId, action, reason,
                            HttpStatus.CONFLICT, "RESOURCE_BUSY",
                            "Không thể cập nhật pin khi xe đang di chuyển hoặc đang có phiên sạc");
                }
                double newBatteryPct = ((Number) parameters.get("batteryPct")).doubleValue();
                commandType = CommandType.SET_BATTERY;
                commandPayload = Map.of("batteryPct", newBatteryPct);
            }
            default -> throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION",
                    "Hành động không hỗ trợ cho VEHICLE: " + action);
        }

        String operationId = operationOrchestrator.nextOperationId();
        AuditEntry audit = auditLogService.record(
                actorId, ResourceType.VEHICLE, vehicleId, action.name(), reason,
                operationId, AuditOutcome.ACCEPTED, null);

        PendingEnvelopeDTO envelope = operationOrchestrator.startOperation(
                operationId,
                OperationKind.INTERVENTION,
                OperationResultType.INTERVENTION,
                ResourceType.VEHICLE,
                vehicleId,
                expectedVersion,
                audit.auditId(),
                commandType,
                commandPayload,
                outcome -> commitVehicleIntervention(actorId, audit.auditId(), vehicleId, action, parameters, outcome),
                null);

        idempotencyStore.recordAccepted(
                actorId, "POST", PATH_INTERVENTIONS, idempotencyKey, canonicalPayload, HttpStatus.ACCEPTED, envelope);
        return envelope;
    }

    private InterventionResultDTO commitVehicleIntervention(
            String actorId,
            String auditId,
            String vehicleId,
            InterventionAction action,
            Map<String, Object> parameters,
            CommandOutcome outcome) {
        Instant now = clock.instant();
        List<AffectedResourceDTO> affected = new ArrayList<>();
        final long[] committedVehicleVersion = new long[1];

        synchronized (dataStore) {
            long baseStateVer = dataStore.version();
            boolean committed = stateCoordinator.commit(baseStateVer, store -> {
                Vehicle current = store.findById(Vehicle.class, vehicleId)
                        .orElseThrow(() -> new IllegalStateException("Vehicle missing during commit"));
                VehicleMeta meta = stateRegistry.getOrDefaultVehicleMeta(
                        vehicleId, current.telemetryAt(), null, current.status() != VehicleStatus.IN_USE);

                long newVer = current.version() + 1;
                committedVehicleVersion[0] = newVer;
                VehicleStatus newStatus = current.status();
                Double newBatteryPct = current.batteryPct();
                BatterySource newBatterySource = meta.batterySource();
                Instant newBatteryUpdatedAt = meta.batteryUpdatedAt();
                Boolean newLocked = meta.isLocked();
                Set<String> newCauses = new LinkedHashSet<>(meta.maintenanceCauses());

                switch (action) {
                    case LOCK -> {
                        newLocked = true;
                        affected.add(new AffectedResourceDTO("VEHICLE", vehicleId, newVer));
                    }
                    case UNLOCK -> {
                        newLocked = false;
                        affected.add(new AffectedResourceDTO("VEHICLE", vehicleId, newVer));
                    }
                    case MARK_MAINTENANCE -> {
                        newLocked = true;
                        newCauses.add("MANUAL");
                        newStatus = VehicleStatus.MAINTENANCE;
                        affected.add(new AffectedResourceDTO("VEHICLE", vehicleId, newVer));
                    }
                    case CLEAR_MAINTENANCE -> {
                        newCauses.remove("MANUAL");
                        newCauses.remove("DATA_UNVERIFIED");
                        newCauses.remove("EMERGENCY_STOP");
                        newStatus = evaluateRestoredVehicleStatus(
                                store, current, newCauses, current.batteryPct(), meta.connectionStatus(), now);
                        affected.add(new AffectedResourceDTO("VEHICLE", vehicleId, newVer));
                    }
                    case CANCEL_RESERVATION -> {
                        String resId = (String) parameters.get("reservationId");
                        Reservation res = store.findById(Reservation.class, resId)
                                .orElseThrow(() -> new IllegalStateException("Reservation missing"));
                        Reservation cancelled = new Reservation(
                                res.id(), res.accountId(), res.vehicleId(), res.pickupCodeHash(),
                                res.createdAt(), res.expiresAt(), ReservationStatus.CANCELLED);
                        store.put(cancelled);
                        newStatus = evaluateRestoredVehicleStatus(
                                store, current, newCauses, current.batteryPct(), meta.connectionStatus(), now);
                        affected.add(new AffectedResourceDTO("VEHICLE", vehicleId, newVer));
                        affected.add(new AffectedResourceDTO("RESERVATION", resId, 1L));
                    }
                    case CANCEL_CHARGING -> {
                        String reqId = (String) parameters.get("chargingRequestId");
                        ChargingRequest req = store.findById(ChargingRequest.class, reqId)
                                .orElseThrow(() -> new IllegalStateException("ChargingRequest missing"));
                        ChargingRequest cancelled = new ChargingRequest(
                                req.id(), req.accountId(), req.vehicleId(), req.hubId(),
                                req.targetBatteryPct(), req.requestedAt(), ChargingRequestStatus.CANCELLED);
                        store.put(cancelled);
                        long reqVer = stateRegistry.getRequestVersion(reqId) + 1;
                        stateRegistry.setRequestVersion(reqId, reqVer);
                        affected.add(new AffectedResourceDTO("VEHICLE", vehicleId, newVer));
                        affected.add(new AffectedResourceDTO("CHARGING_REQUEST", reqId, reqVer));
                    }
                    case SET_BATTERY -> {
                        double pct = ((Number) parameters.get("batteryPct")).doubleValue();
                        newBatteryPct = pct;
                        newBatterySource = BatterySource.MANUAL;
                        newBatteryUpdatedAt = now;
                        if (newStatus == VehicleStatus.AVAILABLE || newStatus == VehicleStatus.LOW_BATTERY) {
                            newStatus = pct < minBatteryPct ? VehicleStatus.LOW_BATTERY : VehicleStatus.AVAILABLE;
                        }
                        affected.add(new AffectedResourceDTO("VEHICLE", vehicleId, newVer));

                        // Revalidate scheduled/waiting requests for this vehicle per Section 7.1 SET_BATTERY
                        List<ChargingRequest> vehicleRequests = store.findAll(ChargingRequest.class).stream()
                                .filter(cr -> vehicleId.equals(cr.vehicleId())
                                        && (cr.status() == ChargingRequestStatus.PENDING
                                                || cr.status() == ChargingRequestStatus.SCHEDULED))
                                .toList();
                        for (ChargingRequest cr : vehicleRequests) {
                            List<ChargingAllocation> allocs = store.findAll(ChargingAllocation.class).stream()
                                    .filter(ca -> cr.id().equals(ca.chargingRequestId())
                                            && ca.status() == ChargingAllocationStatus.RESERVED)
                                    .toList();
                            for (ChargingAllocation ca : allocs) {
                                store.put(new ChargingAllocation(
                                        ca.id(), ca.chargingRequestId(), ca.chargingPortId(),
                                        ca.startsAt(), ca.endsAt(), ca.allocatedPowerKw(),
                                        ChargingAllocationStatus.CANCELLED));
                                affected.add(new AffectedResourceDTO("ALLOCATION", ca.id(), 1L));
                            }
                            ChargingRequestStatus targetReqStatus = pct >= cr.targetBatteryPct()
                                    ? ChargingRequestStatus.COMPLETED
                                    : ChargingRequestStatus.PENDING;
                            store.put(new ChargingRequest(
                                    cr.id(), cr.accountId(), cr.vehicleId(), cr.hubId(),
                                    cr.targetBatteryPct(), cr.requestedAt(), targetReqStatus));
                            long reqVer = stateRegistry.getRequestVersion(cr.id()) + 1;
                            stateRegistry.setRequestVersion(cr.id(), reqVer);
                            affected.add(new AffectedResourceDTO("CHARGING_REQUEST", cr.id(), reqVer));
                        }
                    }
                    default -> {
                    }
                }

                Vehicle updatedVehicle = new Vehicle(
                        current.vehicleId(),
                        current.hubId(),
                        current.parkingSpaceId(),
                        current.vehicleType(),
                        newBatteryPct,
                        current.batteryCapacityKwh(),
                        current.maxChargingPowerKw(),
                        newStatus,
                        current.telemetryAt(),
                        newVer,
                        current.ownership(),
                        current.ownerAccountId());
                store.put(updatedVehicle);

                stateRegistry.putVehicleMeta(vehicleId, new VehicleMeta(
                        newBatterySource,
                        newBatteryUpdatedAt,
                        meta.connectionStatus(),
                        meta.location(),
                        newLocked,
                        null,
                        newCauses));
            });

            if (!committed) {
                throw new IllegalStateException("Concurrent state modification during commit");
            }
        }

        String interventionId = String.format("int-%04d", interventionSeq.getAndIncrement());
        return new InterventionResultDTO(
                interventionId,
                auditId,
                ResourceType.VEHICLE,
                vehicleId,
                action,
                "SUCCEEDED",
                committedVehicleVersion[0],
                actorId,
                GlobalExceptionHandler.formatTimestamp(now),
                affected);
    }

    private PendingEnvelopeDTO handlePortIntervention(
            String actorId,
            String idempotencyKey,
            String canonicalPayload,
            String portId,
            InterventionAction action,
            String reason,
            long expectedVersion,
            Map<String, Object> parameters,
            StoreSnapshot snapshot) {
        ChargingPort port = snapshot.chargingPorts().stream()
                .filter(p -> p.id().equals(portId))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                        "Không tìm thấy cổng sạc: " + portId));

        PortMeta meta = stateRegistry.getPortMeta(portId);
        long currentVersion = meta != null ? meta.version() : 0L;
        String pendingOpId = meta != null ? meta.pendingOperationId() : null;
        Set<String> causes = meta != null ? meta.maintenanceCauses() : Set.of();

        if (pendingOpId != null) {
            rejectWithAudit(actorId, ResourceType.CHARGING_PORT, portId, action, reason,
                    HttpStatus.CONFLICT, "RESOURCE_BUSY", "Cổng sạc đang có lệnh chờ xử lý");
        }
        if (currentVersion != expectedVersion) {
            rejectWithAudit(actorId, ResourceType.CHARGING_PORT, portId, action, reason,
                    HttpStatus.CONFLICT, "VERSION_CONFLICT",
                    "Phiên bản cổng sạc đã thay đổi, vui lòng tải lại trước khi thao tác");
        }

        OperatorPortStatus wireStatus = OperatorPortStatus.fromDomain(port.status());
        List<ChargingAllocation> portAllocations = snapshot.chargingAllocations().stream()
                .filter(ca -> portId.equals(ca.chargingPortId())
                        && (ca.status() == ChargingAllocationStatus.ACTIVE
                                || ca.status() == ChargingAllocationStatus.RESERVED))
                .toList();
        boolean hasBlockingIncident = snapshot.incidents().stream()
                .anyMatch(inc -> portId.equals(inc.vehicleId())
                        && (inc.status() == IncidentStatus.OPEN || inc.status() == IncidentStatus.INVESTIGATING)
                        && inc.severity() != IncidentSeverity.LOW);

        CommandType commandType;
        Map<String, Object> commandPayload = new LinkedHashMap<>();

        switch (action) {
            case MARK_MAINTENANCE -> {
                if (wireStatus == OperatorPortStatus.CHARGING || !portAllocations.isEmpty()) {
                    rejectWithAudit(actorId, ResourceType.CHARGING_PORT, portId, action, reason,
                            HttpStatus.CONFLICT, "RESOURCE_BUSY",
                            "Cổng sạc đang có phiên sạc hoặc lịch phân bổ, hãy dừng/hủy phiên trước khi bảo trì");
                }
                commandType = CommandType.DISABLE_POWER;
                commandPayload.put("chargingRequestId", null);
                commandPayload.put("allocationId", null);
            }
            case CLEAR_MAINTENANCE -> {
                if (wireStatus != OperatorPortStatus.MAINTENANCE) {
                    rejectWithAudit(actorId, ResourceType.CHARGING_PORT, portId, action, reason,
                            HttpStatus.CONFLICT, "INVALID_STATE",
                            "Cổng sạc không ở trạng thái MAINTENANCE");
                }
                boolean hasIncidentCause = causes.stream().anyMatch(c -> c.startsWith("INCIDENT:"));
                if (hasBlockingIncident || hasIncidentCause) {
                    rejectWithAudit(actorId, ResourceType.CHARGING_PORT, portId, action, reason,
                            HttpStatus.CONFLICT, "ACTIVE_INCIDENTS",
                            "Cổng sạc còn sự cố chưa được giải quyết");
                }
                commandType = CommandType.ENABLE_POWER;
                commandPayload.put("chargingRequestId", null);
                commandPayload.put("allocationId", null);
            }
            case STOP_CHARGING -> {
                String reqId = (String) parameters.get("chargingRequestId");
                String allocId = (String) parameters.get("allocationId");
                ChargingAllocation alloc = snapshot.chargingAllocations().stream()
                        .filter(ca -> ca.id().equals(allocId))
                        .findFirst()
                        .orElseThrow(() -> {
                            auditLogService.record(actorId, ResourceType.CHARGING_PORT, portId, action.name(), reason,
                                    null, AuditOutcome.REJECTED, "BUSINESS_RULE_VIOLATION");
                            return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION",
                                    "Không tìm thấy phân bổ sạc: " + allocId);
                        });
                if (!portId.equals(alloc.chargingPortId()) || !reqId.equals(alloc.chargingRequestId())) {
                    rejectWithAudit(actorId, ResourceType.CHARGING_PORT, portId, action, reason,
                            HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION",
                            "Thông tin chargingRequestId/allocationId không thuộc cổng sạc này");
                }
                if (wireStatus != OperatorPortStatus.CHARGING || alloc.status() != ChargingAllocationStatus.ACTIVE) {
                    rejectWithAudit(actorId, ResourceType.CHARGING_PORT, portId, action, reason,
                            HttpStatus.CONFLICT, "INVALID_STATE",
                            "Phiên sạc không ở trạng thái CHARGING hoạt động");
                }
                commandType = CommandType.DISABLE_POWER;
                commandPayload.put("chargingRequestId", reqId);
                commandPayload.put("allocationId", allocId);
            }
            case CANCEL_CHARGING -> {
                String reqId = (String) parameters.get("chargingRequestId");
                ChargingRequest chgReq = snapshot.chargingRequests().stream()
                        .filter(cr -> cr.id().equals(reqId))
                        .findFirst()
                        .orElseThrow(() -> {
                            auditLogService.record(actorId, ResourceType.CHARGING_PORT, portId, action.name(), reason,
                                    null, AuditOutcome.REJECTED, "BUSINESS_RULE_VIOLATION");
                            return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION",
                                    "Không tìm thấy yêu cầu sạc: " + reqId);
                        });
                ChargingAllocation alloc = snapshot.chargingAllocations().stream()
                        .filter(ca -> reqId.equals(ca.chargingRequestId())
                                && (ca.status() == ChargingAllocationStatus.ACTIVE
                                        || ca.status() == ChargingAllocationStatus.RESERVED))
                        .findFirst()
                        .orElseThrow(() -> {
                            auditLogService.record(actorId, ResourceType.CHARGING_PORT, portId, action.name(), reason,
                                    null, AuditOutcome.REJECTED, "BUSINESS_RULE_VIOLATION");
                            return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION",
                                    "Yêu cầu sạc không có phân bổ đang hoạt động trên cổng này");
                        });
                if (!portId.equals(alloc.chargingPortId())) {
                    rejectWithAudit(actorId, ResourceType.CHARGING_PORT, portId, action, reason,
                            HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION",
                            "Yêu cầu sạc không thuộc cổng sạc này");
                }
                if (chgReq.status() == ChargingRequestStatus.CHARGING || alloc.status() == ChargingAllocationStatus.ACTIVE) {
                    commandType = CommandType.STOP_CHARGING;
                    commandPayload.put("chargingRequestId", reqId);
                    commandPayload.put("allocationId", alloc.id());
                } else {
                    commandType = CommandType.VERIFY_STATE;
                    commandPayload = buildVerifyStatePayload(action.name(), null, null, reqId);
                }
            }
            default -> throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION",
                    "Hành động không hỗ trợ cho CHARGING_PORT: " + action);
        }

        String operationId = operationOrchestrator.nextOperationId();
        AuditEntry audit = auditLogService.record(
                actorId, ResourceType.CHARGING_PORT, portId, action.name(), reason,
                operationId, AuditOutcome.ACCEPTED, null);

        PendingEnvelopeDTO envelope = operationOrchestrator.startOperation(
                operationId,
                OperationKind.INTERVENTION,
                OperationResultType.INTERVENTION,
                ResourceType.CHARGING_PORT,
                portId,
                expectedVersion,
                audit.auditId(),
                commandType,
                commandPayload,
                outcome -> commitPortIntervention(actorId, audit.auditId(), portId, action, parameters, outcome),
                null);

        idempotencyStore.recordAccepted(
                actorId, "POST", PATH_INTERVENTIONS, idempotencyKey, canonicalPayload, HttpStatus.ACCEPTED, envelope);
        return envelope;
    }

    private InterventionResultDTO commitPortIntervention(
            String actorId,
            String auditId,
            String portId,
            InterventionAction action,
            Map<String, Object> parameters,
            CommandOutcome outcome) {
        Instant now = clock.instant();
        List<AffectedResourceDTO> affected = new ArrayList<>();
        final long[] committedPortVersion = new long[1];

        synchronized (dataStore) {
            long baseStateVer = dataStore.version();
            boolean committed = stateCoordinator.commit(baseStateVer, store -> {
                ChargingPort current = store.findById(ChargingPort.class, portId)
                        .orElseThrow(() -> new IllegalStateException("ChargingPort missing during commit"));
                PortMeta meta = stateRegistry.getPortMeta(portId);
                long newVer = (meta != null ? meta.version() : 0L) + 1;
                committedPortVersion[0] = newVer;

                Set<String> newCauses = new LinkedHashSet<>();
                if (meta != null && meta.maintenanceCauses() != null) {
                    newCauses.addAll(meta.maintenanceCauses());
                }
                Boolean newPowerEnabled = (meta != null && meta.powerEnabled() != null)
                        ? meta.powerEnabled()
                        : Boolean.TRUE;
                Double newMeasuredPowerW = meta != null ? meta.measuredPowerW() : null;
                Instant newObservedAt = outcome.observedAt() != null
                        ? outcome.observedAt()
                        : (meta != null ? meta.observedAt() : now);
                ConnectionStatus connStatus = meta != null && meta.connectionStatus() != null
                        ? meta.connectionStatus()
                        : ConnectionStatus.ONLINE;
                ChargingPortStatus newDomainStatus = current.status();

                switch (action) {
                    case MARK_MAINTENANCE -> {
                        newCauses.add("MANUAL");
                        newPowerEnabled = false;
                        newMeasuredPowerW = 0.0;
                        newDomainStatus = ChargingPortStatus.OUT_OF_SERVICE;
                        affected.add(new AffectedResourceDTO("CHARGING_PORT", portId, newVer));
                    }
                    case CLEAR_MAINTENANCE -> {
                        newCauses.remove("MANUAL");
                        newCauses.remove("DATA_UNVERIFIED");
                        newCauses.remove("EMERGENCY_STOP");
                        newPowerEnabled = true;
                        newDomainStatus = evaluateRestoredPortDomainStatus(store, portId, newCauses);
                        affected.add(new AffectedResourceDTO("CHARGING_PORT", portId, newVer));
                    }
                    case STOP_CHARGING -> {
                        String reqId = (String) parameters.get("chargingRequestId");
                        String allocId = (String) parameters.get("allocationId");

                        newCauses.add("EMERGENCY_STOP");
                        newPowerEnabled = false;
                        newMeasuredPowerW = 0.0;
                        newDomainStatus = ChargingPortStatus.OUT_OF_SERVICE;
                        affected.add(new AffectedResourceDTO("CHARGING_PORT", portId, newVer));

                        // Cancel current allocation & charging request
                        ChargingAllocation activeAlloc = store.findById(ChargingAllocation.class, allocId).orElse(null);
                        if (activeAlloc != null) {
                            store.put(new ChargingAllocation(
                                    activeAlloc.id(), activeAlloc.chargingRequestId(), activeAlloc.chargingPortId(),
                                    activeAlloc.startsAt(), now, activeAlloc.allocatedPowerKw(),
                                    ChargingAllocationStatus.CANCELLED));
                            affected.add(new AffectedResourceDTO("ALLOCATION", allocId, 1L));
                        }
                        ChargingRequest activeReq = store.findById(ChargingRequest.class, reqId).orElse(null);
                        if (activeReq != null) {
                            store.put(new ChargingRequest(
                                    activeReq.id(), activeReq.accountId(), activeReq.vehicleId(), activeReq.hubId(),
                                    activeReq.targetBatteryPct(), activeReq.requestedAt(),
                                    ChargingRequestStatus.CANCELLED));
                            long reqVer = stateRegistry.getRequestVersion(reqId) + 1;
                            stateRegistry.setRequestVersion(reqId, reqVer);
                            affected.add(new AffectedResourceDTO("CHARGING_REQUEST", reqId, reqVer));

                            // Update vehicle battery if ACK provided final batteryPct
                            if (outcome.deviceState() != null && outcome.deviceState().batteryPct() != null) {
                                Vehicle v = store.findById(Vehicle.class, activeReq.vehicleId()).orElse(null);
                                if (v != null) {
                                    long vVer = v.version() + 1;
                                    store.put(new Vehicle(
                                            v.vehicleId(), v.hubId(), v.parkingSpaceId(), v.vehicleType(),
                                            outcome.deviceState().batteryPct(), v.batteryCapacityKwh(),
                                            v.maxChargingPowerKw(), v.status(), now, vVer,
                                            v.ownership(), v.ownerAccountId()));
                                    affected.add(new AffectedResourceDTO("VEHICLE", v.vehicleId(), vVer));
                                }
                            }
                        }

                        // Cancel future allocations on this port and move their requests back to WAITING (PENDING)
                        List<ChargingAllocation> futureAllocs = store.findAll(ChargingAllocation.class).stream()
                                .filter(ca -> portId.equals(ca.chargingPortId())
                                        && !ca.id().equals(allocId)
                                        && ca.status() == ChargingAllocationStatus.RESERVED)
                                .toList();
                        for (ChargingAllocation fa : futureAllocs) {
                            store.put(new ChargingAllocation(
                                    fa.id(), fa.chargingRequestId(), fa.chargingPortId(),
                                    fa.startsAt(), fa.endsAt(), fa.allocatedPowerKw(),
                                    ChargingAllocationStatus.CANCELLED));
                            affected.add(new AffectedResourceDTO("ALLOCATION", fa.id(), 1L));

                            ChargingRequest freq = store.findById(ChargingRequest.class, fa.chargingRequestId()).orElse(null);
                            if (freq != null && freq.status() == ChargingRequestStatus.SCHEDULED) {
                                store.put(new ChargingRequest(
                                        freq.id(), freq.accountId(), freq.vehicleId(), freq.hubId(),
                                        freq.targetBatteryPct(), freq.requestedAt(),
                                        ChargingRequestStatus.PENDING));
                                long freqVer = stateRegistry.getRequestVersion(freq.id()) + 1;
                                stateRegistry.setRequestVersion(freq.id(), freqVer);
                                affected.add(new AffectedResourceDTO("CHARGING_REQUEST", freq.id(), freqVer));
                            }
                        }
                    }
                    case CANCEL_CHARGING -> {
                        String reqId = (String) parameters.get("chargingRequestId");
                        ChargingRequest req = store.findById(ChargingRequest.class, reqId)
                                .orElseThrow(() -> new IllegalStateException("ChargingRequest missing"));
                        store.put(new ChargingRequest(
                                req.id(), req.accountId(), req.vehicleId(), req.hubId(),
                                req.targetBatteryPct(), req.requestedAt(), ChargingRequestStatus.CANCELLED));
                        long reqVer = stateRegistry.getRequestVersion(reqId) + 1;
                        stateRegistry.setRequestVersion(reqId, reqVer);

                        List<ChargingAllocation> reqAllocs = store.findAll(ChargingAllocation.class).stream()
                                .filter(ca -> reqId.equals(ca.chargingRequestId())
                                        && (ca.status() == ChargingAllocationStatus.ACTIVE
                                                || ca.status() == ChargingAllocationStatus.RESERVED))
                                .toList();
                        for (ChargingAllocation ca : reqAllocs) {
                            store.put(new ChargingAllocation(
                                    ca.id(), ca.chargingRequestId(), ca.chargingPortId(),
                                    ca.startsAt(), ca.endsAt(), ca.allocatedPowerKw(),
                                    ChargingAllocationStatus.CANCELLED));
                            affected.add(new AffectedResourceDTO("ALLOCATION", ca.id(), 1L));
                        }

                        newMeasuredPowerW = 0.0;
                        newDomainStatus = evaluateRestoredPortDomainStatus(store, portId, newCauses);
                        affected.add(new AffectedResourceDTO("CHARGING_PORT", portId, newVer));
                        affected.add(new AffectedResourceDTO("CHARGING_REQUEST", reqId, reqVer));
                    }
                    default -> {
                    }
                }

                store.put(new ChargingPort(
                        current.id(), current.hubId(), current.name(), current.powerKw(), newDomainStatus));
                stateRegistry.putPortMeta(portId, new PortMeta(
                        newMeasuredPowerW,
                        newPowerEnabled,
                        newObservedAt,
                        connStatus,
                        newVer,
                        null,
                        newCauses));
            });

            if (!committed) {
                throw new IllegalStateException("Concurrent state modification during port commit");
            }
        }

        String interventionId = String.format("int-%04d", interventionSeq.getAndIncrement());
        return new InterventionResultDTO(
                interventionId,
                auditId,
                ResourceType.CHARGING_PORT,
                portId,
                action,
                "SUCCEEDED",
                committedPortVersion[0],
                actorId,
                GlobalExceptionHandler.formatTimestamp(now),
                affected);
    }

    private PendingEnvelopeDTO handleSpaceIntervention(
            String actorId,
            String idempotencyKey,
            String canonicalPayload,
            String spaceId,
            InterventionAction action,
            String reason,
            long expectedVersion,
            Map<String, Object> parameters,
            StoreSnapshot snapshot) {
        ParkingSpace space = snapshot.parkingSpaces().stream()
                .filter(s -> s.id().equals(spaceId))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                        "Không tìm thấy vị trí đỗ: " + spaceId));

        OperatorParkingSpaceDTO spaceDto = monitoringAggregator.toOperatorParkingSpaceDTO(space, snapshot);

        if (spaceDto.pendingOperationId() != null) {
            rejectWithAudit(actorId, ResourceType.PARKING_SPACE, spaceId, action, reason,
                    HttpStatus.CONFLICT, "RESOURCE_BUSY", "Vị trí đỗ đang có lệnh chờ xử lý");
        }
        if (spaceDto.version() != expectedVersion) {
            rejectWithAudit(actorId, ResourceType.PARKING_SPACE, spaceId, action, reason,
                    HttpStatus.CONFLICT, "VERSION_CONFLICT",
                    "Phiên bản vị trí đỗ đã thay đổi, vui lòng tải lại trước khi thao tác");
        }

        CommandType commandType;
        Map<String, Object> commandPayload = new LinkedHashMap<>();

        switch (action) {
            case BLOCK_SPACE -> {
                if (!"AVAILABLE".equals(spaceDto.status()) || spaceDto.blocked() || spaceDto.vehicleId() != null) {
                    rejectWithAudit(actorId, ResourceType.PARKING_SPACE, spaceId, action, reason,
                            HttpStatus.CONFLICT, "INVALID_STATE",
                            "Chỉ có thể khóa chặn vị trí đỗ đang AVAILABLE và chưa bị khóa");
                }
                commandType = CommandType.BLOCK_SPACE;
            }
            case UNBLOCK_SPACE -> {
                if (!"AVAILABLE".equals(spaceDto.status()) || !spaceDto.blocked() || spaceDto.vehicleId() != null) {
                    rejectWithAudit(actorId, ResourceType.PARKING_SPACE, spaceId, action, reason,
                            HttpStatus.CONFLICT, "INVALID_STATE",
                            "Vị trí đỗ không ở trạng thái bị chặn hoặc đang có xe/lượt đặt");
                }
                commandType = CommandType.UNBLOCK_SPACE;
            }
            case CANCEL_PARKING_RESERVATION -> {
                String parkingResId = (String) parameters.get("parkingReservationId");
                ParkingReservation pr = snapshot.parkingReservations().stream()
                        .filter(p -> p.id().equals(parkingResId))
                        .findFirst()
                        .orElseThrow(() -> {
                            auditLogService.record(actorId, ResourceType.PARKING_SPACE, spaceId, action.name(), reason,
                                    null, AuditOutcome.REJECTED, "BUSINESS_RULE_VIOLATION");
                            return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION",
                                    "Không tìm thấy lượt đặt chỗ đỗ: " + parkingResId);
                        });
                if (!spaceId.equals(pr.parkingSpaceId())) {
                    rejectWithAudit(actorId, ResourceType.PARKING_SPACE, spaceId, action, reason,
                            HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION",
                            "Lượt đặt chỗ đỗ không thuộc vị trí đỗ này");
                }
                if (!"RESERVED".equals(spaceDto.status())
                        || spaceDto.reservationKind() != SpaceReservationKind.PARKING_HOLD
                        || pr.status() != ParkingReservationStatus.ACTIVE) {
                    rejectWithAudit(actorId, ResourceType.PARKING_SPACE, spaceId, action, reason,
                            HttpStatus.CONFLICT, "INVALID_STATE",
                            "Vị trí đỗ không ở trạng thái RESERVED bởi PARKING_HOLD đang hoạt động");
                }
                commandType = CommandType.VERIFY_STATE;
                commandPayload = buildVerifyStatePayload(action.name(), null, parkingResId, null);
            }
            default -> throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION",
                    "Hành động không hỗ trợ cho PARKING_SPACE: " + action);
        }

        String operationId = operationOrchestrator.nextOperationId();
        AuditEntry audit = auditLogService.record(
                actorId, ResourceType.PARKING_SPACE, spaceId, action.name(), reason,
                operationId, AuditOutcome.ACCEPTED, null);

        PendingEnvelopeDTO envelope = operationOrchestrator.startOperation(
                operationId,
                OperationKind.INTERVENTION,
                OperationResultType.INTERVENTION,
                ResourceType.PARKING_SPACE,
                spaceId,
                expectedVersion,
                audit.auditId(),
                commandType,
                commandPayload,
                outcome -> commitSpaceIntervention(actorId, audit.auditId(), spaceId, action, parameters, outcome),
                null);

        idempotencyStore.recordAccepted(
                actorId, "POST", PATH_INTERVENTIONS, idempotencyKey, canonicalPayload, HttpStatus.ACCEPTED, envelope);
        return envelope;
    }

    private InterventionResultDTO commitSpaceIntervention(
            String actorId,
            String auditId,
            String spaceId,
            InterventionAction action,
            Map<String, Object> parameters,
            CommandOutcome outcome) {
        Instant now = clock.instant();
        List<AffectedResourceDTO> affected = new ArrayList<>();
        final long[] committedSpaceVersion = new long[1];

        synchronized (dataStore) {
            long baseStateVer = dataStore.version();
            boolean committed = stateCoordinator.commit(baseStateVer, store -> {
                ParkingSpace current = store.findById(ParkingSpace.class, spaceId)
                        .orElseThrow(() -> new IllegalStateException("ParkingSpace missing during commit"));
                SpaceMeta meta = stateRegistry.getSpaceMeta(spaceId);
                long newVer = (meta != null ? meta.version() : 0L) + 1;
                committedSpaceVersion[0] = newVer;

                switch (action) {
                    case BLOCK_SPACE -> {
                        store.put(new ParkingSpace(current.id(), current.hubId(), ParkingSpaceStatus.AVAILABLE));
                        stateRegistry.putSpaceMeta(spaceId, new SpaceMeta(
                                true, null, null, null, null, newVer, null, now));
                        affected.add(new AffectedResourceDTO("PARKING_SPACE", spaceId, newVer));
                    }
                    case UNBLOCK_SPACE -> {
                        store.put(new ParkingSpace(current.id(), current.hubId(), ParkingSpaceStatus.AVAILABLE));
                        stateRegistry.putSpaceMeta(spaceId, new SpaceMeta(
                                false, null, null, null, null, newVer, null, now));
                        affected.add(new AffectedResourceDTO("PARKING_SPACE", spaceId, newVer));
                    }
                    case CANCEL_PARKING_RESERVATION -> {
                        String parkingResId = (String) parameters.get("parkingReservationId");
                        ParkingReservation pr = store.findById(ParkingReservation.class, parkingResId)
                                .orElseThrow(() -> new IllegalStateException("ParkingReservation missing"));
                        store.put(new ParkingReservation(
                                pr.id(), pr.accountId(), pr.vehicleId(), pr.parkingSpaceId(),
                                pr.startsAt(), pr.endsAt(), pr.createdAt(), pr.holdExpiresAt(),
                                ParkingReservationStatus.CANCELLED));
                        store.put(new ParkingSpace(current.id(), current.hubId(), ParkingSpaceStatus.AVAILABLE));
                        stateRegistry.putSpaceMeta(spaceId, new SpaceMeta(
                                false, null, null, null, null, newVer, null, now));
                        affected.add(new AffectedResourceDTO("PARKING_SPACE", spaceId, newVer));
                        affected.add(new AffectedResourceDTO("PARKING_RESERVATION", parkingResId, 1L));
                    }
                    default -> {
                    }
                }
            });

            if (!committed) {
                throw new IllegalStateException("Concurrent state modification during space commit");
            }
        }

        String interventionId = String.format("int-%04d", interventionSeq.getAndIncrement());
        return new InterventionResultDTO(
                interventionId,
                auditId,
                ResourceType.PARKING_SPACE,
                spaceId,
                action,
                "SUCCEEDED",
                committedSpaceVersion[0],
                actorId,
                GlobalExceptionHandler.formatTimestamp(now),
                affected);
    }

    public VehicleStatus evaluateRestoredVehicleStatus(
            InMemoryDataStore store,
            Vehicle vehicle,
            Set<String> remainingCauses,
            Double batteryPct,
            ConnectionStatus connectionStatus,
            Instant now) {
        if (!remainingCauses.isEmpty()) {
            return VehicleStatus.MAINTENANCE;
        }
        boolean hasOpenBlockingIncident = store.findAll(Incident.class).stream()
                .anyMatch(inc -> vehicle.vehicleId().equals(inc.vehicleId())
                        && (inc.status() == IncidentStatus.OPEN || inc.status() == IncidentStatus.INVESTIGATING)
                        && inc.severity() != IncidentSeverity.LOW);
        if (hasOpenBlockingIncident) {
            return VehicleStatus.MAINTENANCE;
        }
        boolean hasActiveRide = store.findAll(Ride.class).stream()
                .anyMatch(r -> vehicle.vehicleId().equals(r.vehicleId()) && r.status() == RideStatus.ACTIVE);
        if (hasActiveRide) {
            return VehicleStatus.IN_USE;
        }
        if (vehicle.status() == VehicleStatus.IN_TRANSIT) {
            return VehicleStatus.IN_TRANSIT;
        }
        boolean hasActiveRes = store.findAll(Reservation.class).stream()
                .anyMatch(r -> vehicle.vehicleId().equals(r.vehicleId()) && r.status() == ReservationStatus.ACTIVE);
        if (hasActiveRes) {
            return VehicleStatus.RESERVED;
        }
        if (batteryPct == null || !Double.isFinite(batteryPct)) {
            remainingCauses.add("DATA_UNVERIFIED");
            return VehicleStatus.MAINTENANCE;
        }
        if (batteryPct < minBatteryPct) {
            return VehicleStatus.LOW_BATTERY;
        }
        DataQuality quality = monitoringAggregator.evaluateDataQuality(vehicle.telemetryAt(), connectionStatus, now);
        if (quality != DataQuality.FRESH) {
            remainingCauses.add("DATA_UNVERIFIED");
            return VehicleStatus.MAINTENANCE;
        }
        return VehicleStatus.AVAILABLE;
    }

    public ChargingPortStatus evaluateRestoredPortDomainStatus(
            InMemoryDataStore store,
            String portId,
            Set<String> remainingCauses) {
        if (!remainingCauses.isEmpty()) {
            return ChargingPortStatus.OUT_OF_SERVICE;
        }
        boolean hasActiveAlloc = store.findAll(ChargingAllocation.class).stream()
                .anyMatch(ca -> portId.equals(ca.chargingPortId()) && ca.status() == ChargingAllocationStatus.ACTIVE);
        if (hasActiveAlloc) {
            return ChargingPortStatus.CHARGING;
        }
        boolean hasScheduledAlloc = store.findAll(ChargingAllocation.class).stream()
                .anyMatch(ca -> portId.equals(ca.chargingPortId()) && ca.status() == ChargingAllocationStatus.RESERVED);
        if (hasScheduledAlloc) {
            return ChargingPortStatus.RESERVED;
        }
        return ChargingPortStatus.AVAILABLE;
    }

    private void rejectWithAudit(
            String actorId,
            ResourceType resourceType,
            String resourceId,
            InterventionAction action,
            String reason,
            HttpStatus status,
            String errorCode,
            String message) {
        auditLogService.record(actorId, resourceType, resourceId, action.name(), reason,
                null, AuditOutcome.REJECTED, errorCode);
        throw new ApiException(status, errorCode, message);
    }

    private Map<String, Object> buildVerifyStatePayload(
            String purpose,
            String reservationId,
            String parkingReservationId,
            String chargingRequestId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("purpose", purpose);
        payload.put("reservationId", reservationId);
        payload.put("parkingReservationId", parkingReservationId);
        payload.put("chargingRequestId", chargingRequestId);
        return payload;
    }

    private boolean isResourceActionCompatible(ResourceType resourceType, InterventionAction action) {
        return switch (action) {
            case LOCK, UNLOCK, CANCEL_RESERVATION, SET_BATTERY -> resourceType == ResourceType.VEHICLE;
            case MARK_MAINTENANCE, CLEAR_MAINTENANCE, CANCEL_CHARGING ->
                    resourceType == ResourceType.VEHICLE || resourceType == ResourceType.CHARGING_PORT;
            case STOP_CHARGING -> resourceType == ResourceType.CHARGING_PORT;
            case CANCEL_PARKING_RESERVATION, BLOCK_SPACE, UNBLOCK_SPACE ->
                    resourceType == ResourceType.PARKING_SPACE;
        };
    }

    private void validateRequestShape(InterventionRequestDTO request) {
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
        if (request.getResourceType() == null || request.getResourceType().isBlank()) {
            errors.add(new FieldErrorDetail("resourceType", "Không được để trống"));
        }
        if (request.getResourceId() == null || request.getResourceId().isBlank()
                || request.getResourceId().trim().length() > 128) {
            errors.add(new FieldErrorDetail("resourceId", "Phải từ 1 đến 128 ký tự"));
        }
        if (request.getAction() == null || request.getAction().isBlank()) {
            errors.add(new FieldErrorDetail("action", "Không được để trống"));
        }
        if (request.getReason() == null || request.getReason().trim().isEmpty()
                || request.getReason().trim().length() > 500) {
            errors.add(new FieldErrorDetail("reason", "Lý do sau khi trim phải từ 1 đến 500 ký tự"));
        }
        if (request.getExpectedVersion() == null || request.getExpectedVersion() < 0) {
            errors.add(new FieldErrorDetail("expectedVersion", "Phải là số nguyên >= 0"));
        }
        if (request.getParameters() == null) {
            errors.add(new FieldErrorDetail("parameters", "Object parameters là bắt buộc"));
        }
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Dữ liệu không hợp lệ", errors);
        }
    }

    private ResourceType parseResourceType(String raw) {
        try {
            return ResourceType.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "resourceType không hợp lệ: " + raw,
                    List.of(new FieldErrorDetail("resourceType", "Giá trị enum không hợp lệ")));
        }
    }

    private InterventionAction parseAction(String raw) {
        try {
            return InterventionAction.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "action không hợp lệ: " + raw,
                    List.of(new FieldErrorDetail("action", "Giá trị enum không hợp lệ")));
        }
    }

    private void validateParametersShape(InterventionAction action, Map<String, Object> params) {
        Set<String> requiredKeys = switch (action) {
            case LOCK, UNLOCK, MARK_MAINTENANCE, CLEAR_MAINTENANCE, BLOCK_SPACE, UNBLOCK_SPACE -> Set.of();
            case STOP_CHARGING -> Set.of("chargingRequestId", "allocationId");
            case CANCEL_CHARGING -> Set.of("chargingRequestId");
            case CANCEL_RESERVATION -> Set.of("reservationId");
            case CANCEL_PARKING_RESERVATION -> Set.of("parkingReservationId");
            case SET_BATTERY -> Set.of("batteryPct");
        };

        for (String key : params.keySet()) {
            if (!requiredKeys.contains(key)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "Tham số không hợp lệ trong parameters: " + key,
                        List.of(new FieldErrorDetail("parameters." + key, "Tham số không được phép cho action " + action)));
            }
        }
        for (String reqKey : requiredKeys) {
            Object val = params.get(reqKey);
            if (val == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "Thiếu tham số bắt buộc: parameters." + reqKey,
                        List.of(new FieldErrorDetail("parameters." + reqKey, "Bắt buộc cho action " + action)));
            }
            if ("batteryPct".equals(reqKey)) {
                if (!(val instanceof Number num) || !Double.isFinite(num.doubleValue())
                        || num.doubleValue() < 0.0 || num.doubleValue() > 100.0) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                            "batteryPct phải là số từ 0 đến 100",
                            List.of(new FieldErrorDetail("parameters.batteryPct", "Phải trong khoảng 0..100")));
                }
            } else {
                if (!(val instanceof String str) || str.isBlank() || str.trim().length() > 128) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                            "Tham số " + reqKey + " phải là chuỗi định danh hợp lệ",
                            List.of(new FieldErrorDetail("parameters." + reqKey, "Chuỗi không hợp lệ")));
                }
            }
        }
    }
}
