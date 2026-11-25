package vn.edu.bku.smart_emobility_backend.service.operator;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
import vn.edu.bku.smart_emobility_backend.common.store.OperatorStateRegistry.IncidentMeta;
import vn.edu.bku.smart_emobility_backend.common.store.OperatorStateRegistry.PortMeta;
import vn.edu.bku.smart_emobility_backend.common.store.OperatorStateRegistry.VehicleMeta;
import vn.edu.bku.smart_emobility_backend.common.store.StateCoordinator;
import vn.edu.bku.smart_emobility_backend.common.store.StoreSnapshot;
import vn.edu.bku.smart_emobility_backend.dto.operator.*;
import vn.edu.bku.smart_emobility_backend.dto.operator.OperatorIncidentDTO.IncidentListResponseDTO;
import vn.edu.bku.smart_emobility_backend.service.operator.AuditLogService.AuditEntry;
import vn.edu.bku.smart_emobility_backend.service.operator.DeviceGateway.CommandOutcome;
import vn.edu.bku.smart_emobility_backend.service.operator.DeviceGateway.CommandType;

@Service
public class IncidentService {
    private static final String PATH_INCIDENTS = "/api/operator/incidents";

    private final InMemoryDataStore dataStore;
    private final StateCoordinator stateCoordinator;
    private final OperatorStateRegistry stateRegistry;
    private final IdempotencyStore idempotencyStore;
    private final AuditLogService auditLogService;
    private final OperationOrchestrator operationOrchestrator;
    private final MonitoringAggregator monitoringAggregator;
    private final InterventionService interventionService;
    private final Clock clock;
    private final AtomicLong incidentSeq = new AtomicLong(1);
    private final Map<String, String> eventIdToIncidentId = new ConcurrentHashMap<>();

    public IncidentService(
            InMemoryDataStore dataStore,
            StateCoordinator stateCoordinator,
            OperatorStateRegistry stateRegistry,
            IdempotencyStore idempotencyStore,
            AuditLogService auditLogService,
            OperationOrchestrator operationOrchestrator,
            MonitoringAggregator monitoringAggregator,
            InterventionService interventionService,
            Clock clock) {
        this.dataStore = dataStore;
        this.stateCoordinator = stateCoordinator;
        this.stateRegistry = stateRegistry;
        this.idempotencyStore = idempotencyStore;
        this.auditLogService = auditLogService;
        this.operationOrchestrator = operationOrchestrator;
        this.monitoringAggregator = monitoringAggregator;
        this.interventionService = interventionService;
        this.clock = clock;
    }

    public IncidentListResponseDTO listIncidents(
            OperatorIncidentStatus statusFilter,
            OperatorIncidentSeverity severityFilter,
            String hubIdFilter,
            ResourceType resourceTypeFilter,
            String resourceIdFilter) {
        if (resourceIdFilter != null && resourceTypeFilter == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Khi lọc theo resourceId phải cung cấp cả resourceType",
                    List.of(new FieldErrorDetail("resourceType", "Bắt buộc khi có resourceId")));
        }

        StoreSnapshot snapshot = dataStore.snapshot();
        if (hubIdFilter != null) {
            boolean hubExists = snapshot.hubs().stream().anyMatch(h -> Objects.equals(h.hubId(), hubIdFilter));
            if (!hubExists) {
                throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                        "Không tìm thấy Mobility Hub: " + hubIdFilter);
            }
        }

        List<OperatorIncidentDTO> items = snapshot.incidents().stream()
                .map(inc -> toDtoWithSequence(inc, snapshot))
                .filter(p -> statusFilter == null || statusFilter == p.dto().status())
                .filter(p -> severityFilter == null || severityFilter == p.dto().severity())
                .filter(p -> hubIdFilter == null || hubIdFilter.equals(p.dto().hubId()))
                .filter(p -> resourceTypeFilter == null || resourceTypeFilter == p.dto().resourceType())
                .filter(p -> resourceIdFilter == null || resourceIdFilter.equals(p.dto().resourceId()))
                .sorted(Comparator.comparing(IncidentWithSeq::createdAt).reversed()
                        .thenComparing(Comparator.comparingLong(IncidentWithSeq::seq).reversed())
                        .thenComparing(Comparator.comparing((IncidentWithSeq p) -> p.dto().incidentId()).reversed()))
                .map(IncidentWithSeq::dto)
                .toList();

        return new IncidentListResponseDTO(items);
    }

    public OperatorIncidentDTO getIncident(String incidentId) {
        StoreSnapshot snapshot = dataStore.snapshot();
        Incident inc = snapshot.incidents().stream()
                .filter(i -> i.id().equals(incidentId))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                        "Không tìm thấy sự cố: " + incidentId));
        return toDtoWithSequence(inc, snapshot).dto();
    }

    public synchronized OperatorIncidentDTO createIncident(
            String actorId,
            String rawIdempotencyKey,
            CreateIncidentRequestDTO request) {
        String idempotencyKey = IdempotencyStore.requireValidUuidHeader(rawIdempotencyKey);
        validateCreateRequestShape(request);

        ResourceType resourceType = parseIncidentResourceType(request.getResourceType());
        String resourceId = request.getResourceId().trim();
        OperatorIncidentSeverity severity = parseSeverity(request.getSeverity());
        String description = request.getDescription().trim();

        String canonicalPayload = idempotencyStore.canonicalizePayload(request);
        Optional<CachedResponse> replay = idempotencyStore.checkReplay(
                actorId, "POST", PATH_INCIDENTS, idempotencyKey, canonicalPayload);
        if (replay.isPresent()) {
            return (OperatorIncidentDTO) replay.get().body();
        }

        OperatorIncidentDTO created = recordIncidentInternal(
                null, IncidentSource.OPERATOR, actorId, resourceType, resourceId, severity, description, clock.instant());

        idempotencyStore.recordAccepted(
                actorId, "POST", PATH_INCIDENTS, idempotencyKey, canonicalPayload, HttpStatus.CREATED, created);
        return created;
    }

    /**
     * Internal interface for M1 (Student) and IoT Simulator to report incidents per Section 11.
     * Supports eventId deduplication.
     */
    public synchronized OperatorIncidentDTO reportIncident(
            String eventId,
            IncidentSource source,
            String actorId,
            ResourceType resourceType,
            String resourceId,
            OperatorIncidentSeverity severity,
            String description,
            Instant occurredAt) {
        if (eventId != null && !eventId.isBlank()) {
            String existingId = eventIdToIncidentId.get(eventId);
            if (existingId != null) {
                return getIncident(existingId);
            }
        }
        return recordIncidentInternal(
                eventId,
                source != null ? source : IncidentSource.IOT,
                actorId,
                resourceType,
                resourceId,
                severity,
                description,
                occurredAt != null ? occurredAt : clock.instant());
    }

    private OperatorIncidentDTO recordIncidentInternal(
            String eventId,
            IncidentSource source,
            String actorId,
            ResourceType resourceType,
            String resourceId,
            OperatorIncidentSeverity severity,
            String description,
            Instant reportedAt) {
        StoreSnapshot snapshot = dataStore.snapshot();
        String hubId = resolveResourceHubIdOrThrow(snapshot, resourceType, resourceId);

        long seq = incidentSeq.getAndIncrement();
        String incidentId = String.format("incident-%04d", seq);

        synchronized (dataStore) {
            long baseVer = dataStore.version();
            boolean committed = stateCoordinator.commit(baseVer, store -> {
                Incident domainIncident = new Incident(
                        incidentId,
                        resourceId,
                        hubId,
                        description,
                        severity.toDomain(),
                        reportedAt,
                        null,
                        IncidentStatus.OPEN);
                store.put(domainIncident);

                stateRegistry.putIncidentMeta(incidentId, new IncidentMeta(
                        resourceType,
                        source,
                        actorId,
                        reportedAt,
                        null,
                        null,
                        null,
                        0L,
                        null,
                        seq));
            });

            if (!committed) {
                throw new IllegalStateException("Concurrent state modification while creating incident");
            }
        }

        if (eventId != null && !eventId.isBlank()) {
            eventIdToIncidentId.put(eventId, incidentId);
        }

        return getIncident(incidentId);
    }

    public record IncidentActionResponse(HttpStatus status, Object body) {
    }

    public synchronized IncidentActionResponse sendToMaintenance(
            String actorId,
            String rawIdempotencyKey,
            String incidentId,
            IncidentMaintenanceRequestDTO request) {
        String idempotencyKey = IdempotencyStore.requireValidUuidHeader(rawIdempotencyKey);
        validateMaintenanceRequestShape(request);
        String path = PATH_INCIDENTS + "/" + incidentId + "/maintenance";
        String canonicalPayload = idempotencyStore.canonicalizePayload(request);

        Optional<CachedResponse> replay = idempotencyStore.checkReplay(
                actorId, "POST", path, idempotencyKey, canonicalPayload);
        if (replay.isPresent()) {
            return new IncidentActionResponse(replay.get().status(), replay.get().body());
        }

        StoreSnapshot snapshot = dataStore.snapshot();
        Incident inc = snapshot.incidents().stream()
                .filter(i -> i.id().equals(incidentId))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                        "Không tìm thấy sự cố: " + incidentId));

        IncidentWithSeq currentPair = toDtoWithSequence(inc, snapshot);
        OperatorIncidentDTO currentDto = currentPair.dto();

        // Terminal retry per Section 2 & 10: if already MAINTENANCE, return 200 current Incident
        if (currentDto.status() == OperatorIncidentStatus.MAINTENANCE) {
            idempotencyStore.recordAccepted(actorId, "POST", path, idempotencyKey, canonicalPayload, HttpStatus.OK, currentDto);
            return new IncidentActionResponse(HttpStatus.OK, currentDto);
        }
        if (currentDto.status() != OperatorIncidentStatus.OPEN) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE",
                    "Chỉ có thể đưa vào bảo trì sự cố đang ở trạng thái OPEN");
        }

        if (currentDto.pendingOperationId() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "RESOURCE_BUSY",
                    "Sự cố hoặc thiết bị đang có lệnh chờ xử lý");
        }

        if (currentDto.version() != request.getExpectedVersion()
                || currentDto.resourceVersion() != request.getExpectedResourceVersion()) {
            throw new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT",
                    "Phiên bản sự cố hoặc thiết bị đã thay đổi, vui lòng tải lại trước khi thao tác");
        }

        ResourceType resType = currentDto.resourceType();
        String resId = currentDto.resourceId();
        String reason = request.getReason().trim();

        if (isResourceBusyForMaintenance(snapshot, resType, resId)) {
            auditLogService.record(actorId, resType, resId, "INCIDENT_MAINTENANCE", reason,
                    null, AuditOutcome.REJECTED, "RESOURCE_BUSY");
            throw new ApiException(HttpStatus.CONFLICT, "RESOURCE_BUSY",
                    "Thiết bị đang có chuyến đi, lượt đặt hoặc phiên sạc đang hoạt động");
        }

        CommandType commandType = resType == ResourceType.VEHICLE
                ? CommandType.LOCK
                : CommandType.DISABLE_POWER;
        Map<String, Object> cmdPayload = new LinkedHashMap<>();
        if (commandType == CommandType.DISABLE_POWER) {
            cmdPayload.put("chargingRequestId", null);
            cmdPayload.put("allocationId", null);
        }

        String operationId = operationOrchestrator.nextOperationId();
        setIncidentPendingOperation(incidentId, operationId);

        AuditEntry audit = auditLogService.record(
                actorId, resType, resId, "INCIDENT_MAINTENANCE", reason,
                operationId, AuditOutcome.ACCEPTED, null);

        PendingEnvelopeDTO envelope = operationOrchestrator.startOperation(
                operationId,
                OperationKind.INCIDENT_MAINTENANCE,
                OperationResultType.INCIDENT,
                resType,
                resId,
                currentDto.resourceVersion(),
                audit.auditId(),
                commandType,
                cmdPayload,
                outcome -> commitIncidentMaintenance(incidentId, resType, resId, outcome),
                () -> clearIncidentPendingOperation(incidentId, operationId));

        idempotencyStore.recordAccepted(
                actorId, "POST", path, idempotencyKey, canonicalPayload, HttpStatus.ACCEPTED, envelope);
        return new IncidentActionResponse(HttpStatus.ACCEPTED, envelope);
    }

    private OperatorIncidentDTO commitIncidentMaintenance(
            String incidentId,
            ResourceType resType,
            String resId,
            CommandOutcome outcome) {
        Instant now = clock.instant();
        String causeKey = "INCIDENT:" + incidentId;

        synchronized (dataStore) {
            long baseVer = dataStore.version();
            boolean committed = stateCoordinator.commit(baseVer, store -> {
                Incident inc = store.findById(Incident.class, incidentId)
                        .orElseThrow(() -> new IllegalStateException("Incident missing during maintenance commit"));
                IncidentMeta incMeta = stateRegistry.getIncidentMeta(incidentId);
                long newIncVer = (incMeta != null ? incMeta.version() : 0L) + 1;

                if (resType == ResourceType.VEHICLE) {
                    Vehicle v = store.findById(Vehicle.class, resId)
                            .orElseThrow(() -> new IllegalStateException("Vehicle missing"));
                    VehicleMeta vMeta = stateRegistry.getOrDefaultVehicleMeta(
                            resId, v.telemetryAt(), null, true);
                    Set<String> causes = new LinkedHashSet<>(vMeta.maintenanceCauses());
                    causes.add(causeKey);
                    long newVehicleVer = v.version() + 1;

                    store.put(new Vehicle(
                            v.vehicleId(), v.hubId(), v.parkingSpaceId(), v.vehicleType(),
                            v.batteryPct(), v.batteryCapacityKwh(), v.maxChargingPowerKw(),
                            VehicleStatus.MAINTENANCE, v.telemetryAt(), newVehicleVer,
                            v.ownership(), v.ownerAccountId()));

                    stateRegistry.putVehicleMeta(resId, new VehicleMeta(
                            vMeta.batterySource(),
                            vMeta.batteryUpdatedAt(),
                            vMeta.connectionStatus(),
                            vMeta.location(),
                            true,
                            null,
                            causes));
                } else {
                    ChargingPort p = store.findById(ChargingPort.class, resId)
                            .orElseThrow(() -> new IllegalStateException("ChargingPort missing"));
                    PortMeta pMeta = stateRegistry.getPortMeta(resId);
                    Set<String> causes = new LinkedHashSet<>();
                    if (pMeta != null && pMeta.maintenanceCauses() != null) {
                        causes.addAll(pMeta.maintenanceCauses());
                    }
                    causes.add(causeKey);
                    long newPortVer = (pMeta != null ? pMeta.version() : 0L) + 1;
                    Instant obsAt = outcome.observedAt() != null
                            ? outcome.observedAt()
                            : (pMeta != null ? pMeta.observedAt() : now);
                    ConnectionStatus conn = (pMeta != null && pMeta.connectionStatus() != null)
                            ? pMeta.connectionStatus()
                            : ConnectionStatus.ONLINE;

                    store.put(new ChargingPort(
                            p.id(), p.hubId(), p.name(), p.powerKw(), ChargingPortStatus.OUT_OF_SERVICE));
                    stateRegistry.putPortMeta(resId, new PortMeta(
                            0.0, false, obsAt, conn, newPortVer, null, causes));
                }

                store.put(new Incident(
                        inc.id(), inc.vehicleId(), inc.hubId(), inc.description(),
                        inc.severity(), inc.reportedAt(), null, IncidentStatus.INVESTIGATING));

                stateRegistry.putIncidentMeta(incidentId, new IncidentMeta(
                        resType,
                        incMeta != null ? incMeta.source() : IncidentSource.OPERATOR,
                        incMeta != null ? incMeta.actorId() : null,
                        now,
                        null,
                        null,
                        null,
                        newIncVer,
                        null,
                        incMeta != null ? incMeta.sequenceOrder() : 0L));
            });

            if (!committed) {
                throw new IllegalStateException("Concurrent state modification during incident maintenance commit");
            }
        }

        return getIncident(incidentId);
    }

    public synchronized IncidentActionResponse resolveIncident(
            String actorId,
            String rawIdempotencyKey,
            String incidentId,
            IncidentResolveRequestDTO request) {
        String idempotencyKey = IdempotencyStore.requireValidUuidHeader(rawIdempotencyKey);
        validateResolveRequestShape(request);
        String path = PATH_INCIDENTS + "/" + incidentId + "/resolve";
        String canonicalPayload = idempotencyStore.canonicalizePayload(request);

        Optional<CachedResponse> replay = idempotencyStore.checkReplay(
                actorId, "POST", path, idempotencyKey, canonicalPayload);
        if (replay.isPresent()) {
            return new IncidentActionResponse(replay.get().status(), replay.get().body());
        }

        StoreSnapshot snapshot = dataStore.snapshot();
        Incident inc = snapshot.incidents().stream()
                .filter(i -> i.id().equals(incidentId))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                        "Không tìm thấy sự cố: " + incidentId));

        IncidentWithSeq currentPair = toDtoWithSequence(inc, snapshot);
        OperatorIncidentDTO currentDto = currentPair.dto();

        // Terminal retry: if already RESOLVED, return 200 current Incident
        if (currentDto.status() == OperatorIncidentStatus.RESOLVED) {
            idempotencyStore.recordAccepted(actorId, "POST", path, idempotencyKey, canonicalPayload, HttpStatus.OK, currentDto);
            return new IncidentActionResponse(HttpStatus.OK, currentDto);
        }
        if (currentDto.status() == OperatorIncidentStatus.DISMISSED) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE",
                    "Sự cố đã bị DISMISSED, không thể chuyển sang RESOLVED");
        }

        if (currentDto.pendingOperationId() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "RESOURCE_BUSY",
                    "Sự cố hoặc thiết bị đang có lệnh chờ xử lý");
        }

        if (currentDto.version() != request.getExpectedVersion()
                || currentDto.resourceVersion() != request.getExpectedResourceVersion()) {
            throw new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT",
                    "Phiên bản sự cố hoặc thiết bị đã thay đổi, vui lòng tải lại trước khi thao tác");
        }

        ResourceType resType = currentDto.resourceType();
        String resId = currentDto.resourceId();
        String resolutionNote = request.getResolutionNote().trim();

        CommandType commandType;
        Map<String, Object> cmdPayload = new LinkedHashMap<>();

        if (resType == ResourceType.VEHICLE) {
            commandType = CommandType.VERIFY_STATE;
            cmdPayload.put("purpose", "INCIDENT_RESOLVE");
            cmdPayload.put("reservationId", null);
            cmdPayload.put("parkingReservationId", null);
            cmdPayload.put("chargingRequestId", null);
        } else {
            boolean willEnablePortPower = willPortRecoverOnResolve(snapshot, incidentId, resId);
            if (willEnablePortPower) {
                commandType = CommandType.ENABLE_POWER;
                cmdPayload.put("chargingRequestId", null);
                cmdPayload.put("allocationId", null);
            } else {
                commandType = CommandType.VERIFY_STATE;
                cmdPayload.put("purpose", "INCIDENT_RESOLVE");
                cmdPayload.put("reservationId", null);
                cmdPayload.put("parkingReservationId", null);
                cmdPayload.put("chargingRequestId", null);
            }
        }

        String operationId = operationOrchestrator.nextOperationId();
        setIncidentPendingOperation(incidentId, operationId);

        AuditEntry audit = auditLogService.record(
                actorId, resType, resId, "INCIDENT_RESOLVE", resolutionNote,
                operationId, AuditOutcome.ACCEPTED, null);

        PendingEnvelopeDTO envelope = operationOrchestrator.startOperation(
                operationId,
                OperationKind.INCIDENT_RESOLVE,
                OperationResultType.INCIDENT,
                resType,
                resId,
                currentDto.resourceVersion(),
                audit.auditId(),
                commandType,
                cmdPayload,
                outcome -> commitIncidentResolve(incidentId, resType, resId, resolutionNote, outcome),
                () -> clearIncidentPendingOperation(incidentId, operationId));

        idempotencyStore.recordAccepted(
                actorId, "POST", path, idempotencyKey, canonicalPayload, HttpStatus.ACCEPTED, envelope);
        return new IncidentActionResponse(HttpStatus.ACCEPTED, envelope);
    }

    private OperatorIncidentDTO commitIncidentResolve(
            String incidentId,
            ResourceType resType,
            String resId,
            String resolutionNote,
            CommandOutcome outcome) {
        Instant now = clock.instant();
        String causeKey = "INCIDENT:" + incidentId;

        synchronized (dataStore) {
            long baseVer = dataStore.version();
            boolean committed = stateCoordinator.commit(baseVer, store -> {
                Incident inc = store.findById(Incident.class, incidentId)
                        .orElseThrow(() -> new IllegalStateException("Incident missing during resolve commit"));
                IncidentMeta incMeta = stateRegistry.getIncidentMeta(incidentId);
                long newIncVer = (incMeta != null ? incMeta.version() : 0L) + 1;

                // 1. Update Incident to RESOLVED first so evaluateRestoredStatus sees it resolved
                store.put(new Incident(
                        inc.id(), inc.vehicleId(), inc.hubId(), inc.description(),
                        inc.severity(), inc.reportedAt(), now, IncidentStatus.RESOLVED));

                stateRegistry.putIncidentMeta(incidentId, new IncidentMeta(
                        resType,
                        incMeta != null ? incMeta.source() : IncidentSource.OPERATOR,
                        incMeta != null ? incMeta.actorId() : null,
                        now,
                        null,
                        resolutionNote,
                        null,
                        newIncVer,
                        null,
                        incMeta != null ? incMeta.sequenceOrder() : 0L));

                // 2. Remove only this incident's cause and evaluate cause-aware device recovery
                if (resType == ResourceType.VEHICLE) {
                    Vehicle v = store.findById(Vehicle.class, resId)
                            .orElseThrow(() -> new IllegalStateException("Vehicle missing"));
                    VehicleMeta vMeta = stateRegistry.getOrDefaultVehicleMeta(
                            resId, v.telemetryAt(), null, v.status() != VehicleStatus.IN_USE);
                    Set<String> causes = new LinkedHashSet<>(vMeta.maintenanceCauses());
                    boolean hadCause = causes.remove(causeKey);

                    VehicleStatus evaluatedStatus = interventionService.evaluateRestoredVehicleStatus(
                            store, v, causes, v.batteryPct(), vMeta.connectionStatus(), now);

                    long newVehicleVer = (hadCause || evaluatedStatus != v.status())
                            ? v.version() + 1
                            : v.version();

                    store.put(new Vehicle(
                            v.vehicleId(), v.hubId(), v.parkingSpaceId(), v.vehicleType(),
                            v.batteryPct(), v.batteryCapacityKwh(), v.maxChargingPowerKw(),
                            evaluatedStatus, v.telemetryAt(), newVehicleVer,
                            v.ownership(), v.ownerAccountId()));

                    stateRegistry.putVehicleMeta(resId, new VehicleMeta(
                            vMeta.batterySource(),
                            vMeta.batteryUpdatedAt(),
                            vMeta.connectionStatus(),
                            vMeta.location(),
                            vMeta.isLocked(),
                            null,
                            causes));
                } else {
                    ChargingPort p = store.findById(ChargingPort.class, resId)
                            .orElseThrow(() -> new IllegalStateException("ChargingPort missing"));
                    PortMeta pMeta = stateRegistry.getPortMeta(resId);
                    Set<String> causes = new LinkedHashSet<>();
                    if (pMeta != null && pMeta.maintenanceCauses() != null) {
                        causes.addAll(pMeta.maintenanceCauses());
                    }
                    boolean hadCause = causes.remove(causeKey);

                    boolean hasOtherBlockingIncident = hasOtherOpenBlockingIncident(store, incidentId, resId);
                    ChargingPortStatus evaluatedStatus;
                    Boolean powerEnabled = pMeta != null && pMeta.powerEnabled() != null
                            ? pMeta.powerEnabled()
                            : (p.status() != ChargingPortStatus.OUT_OF_SERVICE);

                    if (!causes.isEmpty() || hasOtherBlockingIncident) {
                        evaluatedStatus = ChargingPortStatus.OUT_OF_SERVICE;
                    } else {
                        evaluatedStatus = interventionService.evaluateRestoredPortDomainStatus(store, resId, causes);
                        powerEnabled = true;
                    }

                    long currentPortVer = pMeta != null ? pMeta.version() : 0L;
                    long newPortVer = (hadCause || evaluatedStatus != p.status())
                            ? currentPortVer + 1
                            : currentPortVer;

                    Instant obsAt = outcome.observedAt() != null
                            ? outcome.observedAt()
                            : (pMeta != null ? pMeta.observedAt() : now);
                    ConnectionStatus conn = (pMeta != null && pMeta.connectionStatus() != null)
                            ? pMeta.connectionStatus()
                            : ConnectionStatus.ONLINE;

                    store.put(new ChargingPort(
                            p.id(), p.hubId(), p.name(), p.powerKw(), evaluatedStatus));
                    stateRegistry.putPortMeta(resId, new PortMeta(
                            pMeta != null ? pMeta.measuredPowerW() : 0.0,
                            powerEnabled,
                            obsAt,
                            conn,
                            newPortVer,
                            null,
                            causes));
                }
            });

            if (!committed) {
                throw new IllegalStateException("Concurrent state modification during incident resolve commit");
            }
        }

        return getIncident(incidentId);
    }

    public synchronized OperatorIncidentDTO dismissIncident(
            String actorId,
            String rawIdempotencyKey,
            String incidentId,
            IncidentDismissRequestDTO request) {
        String idempotencyKey = IdempotencyStore.requireValidUuidHeader(rawIdempotencyKey);
        validateDismissRequestShape(request);
        String path = PATH_INCIDENTS + "/" + incidentId + "/dismiss";
        String canonicalPayload = idempotencyStore.canonicalizePayload(request);

        Optional<CachedResponse> replay = idempotencyStore.checkReplay(
                actorId, "POST", path, idempotencyKey, canonicalPayload);
        if (replay.isPresent()) {
            return (OperatorIncidentDTO) replay.get().body();
        }

        StoreSnapshot snapshot = dataStore.snapshot();
        Incident inc = snapshot.incidents().stream()
                .filter(i -> i.id().equals(incidentId))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                        "Không tìm thấy sự cố: " + incidentId));

        IncidentWithSeq currentPair = toDtoWithSequence(inc, snapshot);
        OperatorIncidentDTO currentDto = currentPair.dto();

        // Terminal retry: if already DISMISSED, return 200 current Incident
        if (currentDto.status() == OperatorIncidentStatus.DISMISSED) {
            idempotencyStore.recordAccepted(actorId, "POST", path, idempotencyKey, canonicalPayload, HttpStatus.OK, currentDto);
            return currentDto;
        }
        if (currentDto.status() != OperatorIncidentStatus.OPEN) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE",
                    "Chỉ có thể bỏ qua sự cố đang ở trạng thái OPEN");
        }
        if (currentDto.severity() != OperatorIncidentSeverity.LOW) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION",
                    "Chỉ được phép bỏ qua (dismiss) cảnh báo sự cố mức độ nhẹ (LOW)");
        }
        if (currentDto.pendingOperationId() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "RESOURCE_BUSY",
                    "Sự cố hoặc thiết bị đang có lệnh chờ xử lý");
        }
        if (currentDto.version() != request.getExpectedVersion()) {
            throw new ApiException(HttpStatus.CONFLICT, "VERSION_CONFLICT",
                    "Phiên bản sự cố đã thay đổi, vui lòng tải lại trước khi thao tác");
        }

        String dismissNote = request.getDismissNote().trim();
        Instant now = clock.instant();
        long newIncVer = currentDto.version() + 1;

        synchronized (dataStore) {
            long baseVer = dataStore.version();
            boolean committed = stateCoordinator.commit(baseVer, store -> {
                store.put(new Incident(
                        inc.id(), inc.vehicleId(), inc.hubId(), inc.description(),
                        inc.severity(), inc.reportedAt(), null, IncidentStatus.DISMISSED));

                stateRegistry.putIncidentMeta(incidentId, new IncidentMeta(
                        currentDto.resourceType(),
                        currentDto.source(),
                        currentDto.actorId(),
                        now,
                        now,
                        null,
                        dismissNote,
                        newIncVer,
                        null,
                        currentPair.seq()));
            });

            if (!committed) {
                throw new IllegalStateException("Concurrent state modification during incident dismiss commit");
            }
        }

        auditLogService.record(
                actorId,
                currentDto.resourceType(),
                currentDto.resourceId(),
                "INCIDENT_DISMISS",
                dismissNote,
                null,
                AuditOutcome.SUCCEEDED,
                null);

        OperatorIncidentDTO updatedDto = getIncident(incidentId);
        idempotencyStore.recordAccepted(actorId, "POST", path, idempotencyKey, canonicalPayload, HttpStatus.OK, updatedDto);
        return updatedDto;
    }

    public synchronized void clear() {
        incidentSeq.set(1);
        eventIdToIncidentId.clear();
    }

    private boolean willPortRecoverOnResolve(StoreSnapshot snapshot, String incidentId, String portId) {
        PortMeta pMeta = stateRegistry.getPortMeta(portId);
        Set<String> causes = new LinkedHashSet<>();
        if (pMeta != null && pMeta.maintenanceCauses() != null) {
            causes.addAll(pMeta.maintenanceCauses());
        }
        causes.remove("INCIDENT:" + incidentId);
        if (!causes.isEmpty()) {
            return false;
        }
        boolean hasOtherBlocking = snapshot.incidents().stream()
                .anyMatch(inc -> !inc.id().equals(incidentId)
                        && portId.equals(inc.vehicleId())
                        && (inc.status() == IncidentStatus.OPEN || inc.status() == IncidentStatus.INVESTIGATING)
                        && inc.severity() != IncidentSeverity.LOW);
        if (hasOtherBlocking) {
            return false;
        }
        boolean hasActiveCharging = snapshot.chargingAllocations().stream()
                .anyMatch(ca -> portId.equals(ca.chargingPortId())
                        && ca.status() == ChargingAllocationStatus.ACTIVE);
        return !hasActiveCharging;
    }

    private boolean hasOtherOpenBlockingIncident(InMemoryDataStore store, String excludedIncidentId, String resourceId) {
        return store.findAll(Incident.class).stream()
                .anyMatch(inc -> !inc.id().equals(excludedIncidentId)
                        && resourceId.equals(inc.vehicleId())
                        && (inc.status() == IncidentStatus.OPEN || inc.status() == IncidentStatus.INVESTIGATING)
                        && inc.severity() != IncidentSeverity.LOW);
    }

    private boolean isResourceBusyForMaintenance(StoreSnapshot snapshot, ResourceType resType, String resId) {
        if (resType == ResourceType.VEHICLE) {
            Vehicle v = snapshot.vehicles().stream()
                    .filter(x -> x.vehicleId().equals(resId))
                    .findFirst().orElse(null);
            if (v == null) {
                return false;
            }
            if (v.status() == VehicleStatus.IN_USE || v.status() == VehicleStatus.IN_TRANSIT) {
                return true;
            }
            boolean hasRide = snapshot.rides().stream()
                    .anyMatch(r -> resId.equals(r.vehicleId()) && r.status() == RideStatus.ACTIVE);
            boolean hasRes = snapshot.reservations().stream()
                    .anyMatch(r -> resId.equals(r.vehicleId()) && r.status() == ReservationStatus.ACTIVE);
            boolean hasChg = snapshot.chargingRequests().stream()
                    .anyMatch(cr -> resId.equals(cr.vehicleId())
                            && (cr.status() == ChargingRequestStatus.PENDING
                                    || cr.status() == ChargingRequestStatus.SCHEDULED
                                    || cr.status() == ChargingRequestStatus.CHARGING));
            return hasRide || hasRes || hasChg;
        } else {
            ChargingPort p = snapshot.chargingPorts().stream()
                    .filter(x -> x.id().equals(resId))
                    .findFirst().orElse(null);
            if (p != null && p.status() == ChargingPortStatus.CHARGING) {
                return true;
            }
            return snapshot.chargingAllocations().stream()
                    .anyMatch(ca -> resId.equals(ca.chargingPortId())
                            && (ca.status() == ChargingAllocationStatus.ACTIVE
                                    || ca.status() == ChargingAllocationStatus.RESERVED));
        }
    }

    private void setIncidentPendingOperation(String incidentId, String operationId) {
        IncidentMeta meta = stateRegistry.getIncidentMeta(incidentId);
        if (meta != null) {
            stateRegistry.putIncidentMeta(incidentId, new IncidentMeta(
                    meta.resourceType(),
                    meta.source(),
                    meta.actorId(),
                    meta.updatedAt(),
                    meta.dismissedAt(),
                    meta.resolutionNote(),
                    meta.dismissNote(),
                    meta.version(),
                    operationId,
                    meta.sequenceOrder()));
        }
    }

    private void clearIncidentPendingOperation(String incidentId, String operationId) {
        IncidentMeta meta = stateRegistry.getIncidentMeta(incidentId);
        if (meta != null && (operationId == null || operationId.equals(meta.pendingOperationId()))) {
            stateRegistry.putIncidentMeta(incidentId, new IncidentMeta(
                    meta.resourceType(),
                    meta.source(),
                    meta.actorId(),
                    meta.updatedAt(),
                    meta.dismissedAt(),
                    meta.resolutionNote(),
                    meta.dismissNote(),
                    meta.version(),
                    null,
                    meta.sequenceOrder()));
        }
    }

    private IncidentWithSeq toDtoWithSequence(Incident inc, StoreSnapshot snapshot) {
        IncidentMeta meta = stateRegistry.getIncidentMeta(inc.id());
        String resourceId = inc.vehicleId();

        ResourceType resourceType;
        if (meta != null && meta.resourceType() != null) {
            resourceType = meta.resourceType();
        } else {
            boolean isPort = snapshot.chargingPorts().stream().anyMatch(p -> p.id().equals(resourceId));
            resourceType = isPort ? ResourceType.CHARGING_PORT : ResourceType.VEHICLE;
        }

        IncidentSource source = (meta != null && meta.source() != null) ? meta.source() : IncidentSource.OPERATOR;
        String actorId = meta != null ? meta.actorId() : "acc-01";
        Instant updatedAt = (meta != null && meta.updatedAt() != null) ? meta.updatedAt() : inc.reportedAt();
        Instant dismissedAt = meta != null ? meta.dismissedAt() : null;
        String resolutionNote = meta != null ? meta.resolutionNote() : null;
        String dismissNote = meta != null ? meta.dismissNote() : null;
        long version = meta != null ? meta.version() : 0L;
        long seq = meta != null ? meta.sequenceOrder() : 0L;

        long resourceVersion = 0L;
        String resourcePendingOp = null;
        if (resourceType == ResourceType.VEHICLE) {
            Vehicle v = snapshot.vehicles().stream().filter(x -> x.vehicleId().equals(resourceId)).findFirst().orElse(null);
            if (v != null) {
                resourceVersion = v.version();
            }
            VehicleMeta vMeta = stateRegistry.getVehicleMeta(resourceId);
            if (vMeta != null) {
                resourcePendingOp = vMeta.pendingOperationId();
            }
        } else {
            PortMeta pMeta = stateRegistry.getPortMeta(resourceId);
            if (pMeta != null) {
                resourceVersion = pMeta.version();
                resourcePendingOp = pMeta.pendingOperationId();
            }
        }

        String pendingOpId = (meta != null && meta.pendingOperationId() != null)
                ? meta.pendingOperationId()
                : resourcePendingOp;

        OperatorIncidentStatus wireStatus = OperatorIncidentStatus.fromDomain(inc.status());
        OperatorIncidentSeverity wireSeverity = OperatorIncidentSeverity.fromDomain(inc.severity());

        List<IncidentAction> allowedActions = new ArrayList<>();
        if (pendingOpId == null) {
            if (wireStatus == OperatorIncidentStatus.OPEN) {
                if (!isResourceBusyForMaintenance(snapshot, resourceType, resourceId)) {
                    allowedActions.add(IncidentAction.MAINTENANCE);
                }
                allowedActions.add(IncidentAction.RESOLVE);
                if (wireSeverity == OperatorIncidentSeverity.LOW) {
                    allowedActions.add(IncidentAction.DISMISS);
                }
            } else if (wireStatus == OperatorIncidentStatus.MAINTENANCE) {
                allowedActions.add(IncidentAction.RESOLVE);
            }
        }

        OperatorIncidentDTO dto = new OperatorIncidentDTO(
                inc.id(),
                resourceType,
                resourceId,
                inc.hubId(),
                wireSeverity,
                wireStatus,
                inc.description(),
                source,
                actorId,
                GlobalExceptionHandler.formatTimestamp(inc.reportedAt()),
                GlobalExceptionHandler.formatTimestamp(updatedAt),
                GlobalExceptionHandler.formatTimestamp(inc.resolvedAt()),
                GlobalExceptionHandler.formatTimestamp(dismissedAt),
                resolutionNote,
                dismissNote,
                version,
                resourceVersion,
                pendingOpId,
                allowedActions);

        return new IncidentWithSeq(dto, inc.reportedAt() != null ? inc.reportedAt() : Instant.EPOCH, seq);
    }

    private String resolveResourceHubIdOrThrow(StoreSnapshot snapshot, ResourceType resourceType, String resourceId) {
        if (resourceType == ResourceType.VEHICLE) {
            Vehicle v = snapshot.vehicles().stream()
                    .filter(x -> x.vehicleId().equals(resourceId))
                    .findFirst()
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                            "Không tìm thấy phương tiện: " + resourceId));
            boolean hasActiveRide = v.status() == VehicleStatus.IN_USE
                    || snapshot.rides().stream().anyMatch(r -> resourceId.equals(r.vehicleId()) && r.status() == RideStatus.ACTIVE);
            return (hasActiveRide || v.status() == VehicleStatus.IN_TRANSIT) ? null : v.hubId();
        } else {
            ChargingPort p = snapshot.chargingPorts().stream()
                    .filter(x -> x.id().equals(resourceId))
                    .findFirst()
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND",
                            "Không tìm thấy cổng sạc: " + resourceId));
            return p.hubId();
        }
    }

    private ResourceType parseIncidentResourceType(String raw) {
        try {
            ResourceType type = ResourceType.valueOf(raw);
            if (type != ResourceType.VEHICLE && type != ResourceType.CHARGING_PORT) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                        "Sự cố chỉ áp dụng cho VEHICLE hoặc CHARGING_PORT",
                        List.of(new FieldErrorDetail("resourceType", "Chỉ cho phép VEHICLE hoặc CHARGING_PORT")));
            }
            return type;
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "resourceType không hợp lệ: " + raw,
                    List.of(new FieldErrorDetail("resourceType", "Giá trị enum không hợp lệ")));
        }
    }

    private OperatorIncidentSeverity parseSeverity(String raw) {
        try {
            return OperatorIncidentSeverity.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "severity không hợp lệ: " + raw,
                    List.of(new FieldErrorDetail("severity", "Chỉ cho phép LOW, MEDIUM, HIGH")));
        }
    }

    private void validateCreateRequestShape(CreateIncidentRequestDTO request) {
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
        if (request.getSeverity() == null || request.getSeverity().isBlank()) {
            errors.add(new FieldErrorDetail("severity", "Không được để trống"));
        }
        if (request.getDescription() == null || request.getDescription().trim().isEmpty()
                || request.getDescription().trim().length() > 2000) {
            errors.add(new FieldErrorDetail("description", "Mô tả sau khi trim phải từ 1 đến 2000 ký tự"));
        }
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Dữ liệu không hợp lệ", errors);
        }
    }

    private void validateMaintenanceRequestShape(IncidentMaintenanceRequestDTO request) {
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
        if (request.getExpectedVersion() == null || request.getExpectedVersion() < 0) {
            errors.add(new FieldErrorDetail("expectedVersion", "Bắt buộc và >= 0"));
        }
        if (request.getExpectedResourceVersion() == null || request.getExpectedResourceVersion() < 0) {
            errors.add(new FieldErrorDetail("expectedResourceVersion", "Bắt buộc và >= 0"));
        }
        if (request.getReason() == null || request.getReason().trim().isEmpty()
                || request.getReason().trim().length() > 500) {
            errors.add(new FieldErrorDetail("reason", "Lý do sau khi trim phải từ 1 đến 500 ký tự"));
        }
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Dữ liệu không hợp lệ", errors);
        }
    }

    private void validateResolveRequestShape(IncidentResolveRequestDTO request) {
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
        if (request.getExpectedVersion() == null || request.getExpectedVersion() < 0) {
            errors.add(new FieldErrorDetail("expectedVersion", "Bắt buộc và >= 0"));
        }
        if (request.getExpectedResourceVersion() == null || request.getExpectedResourceVersion() < 0) {
            errors.add(new FieldErrorDetail("expectedResourceVersion", "Bắt buộc và >= 0"));
        }
        if (request.getResolutionNote() == null || request.getResolutionNote().trim().isEmpty()
                || request.getResolutionNote().trim().length() > 500) {
            errors.add(new FieldErrorDetail("resolutionNote", "Ghi chú sau khi trim phải từ 1 đến 500 ký tự"));
        }
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Dữ liệu không hợp lệ", errors);
        }
    }

    private void validateDismissRequestShape(IncidentDismissRequestDTO request) {
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
        if (request.getExpectedVersion() == null || request.getExpectedVersion() < 0) {
            errors.add(new FieldErrorDetail("expectedVersion", "Bắt buộc và >= 0"));
        }
        if (request.getDismissNote() == null || request.getDismissNote().trim().isEmpty()
                || request.getDismissNote().trim().length() > 500) {
            errors.add(new FieldErrorDetail("dismissNote", "Ghi chú sau khi trim phải từ 1 đến 500 ký tự"));
        }
        if (!errors.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Dữ liệu không hợp lệ", errors);
        }
    }

    private record IncidentWithSeq(OperatorIncidentDTO dto, Instant createdAt, long seq) {
    }
}
