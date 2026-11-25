package vn.edu.bku.smart_emobility_backend.common.store;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import vn.edu.bku.smart_emobility_backend.common.domain.GeoPoint;
import vn.edu.bku.smart_emobility_backend.dto.operator.BatterySource;
import vn.edu.bku.smart_emobility_backend.dto.operator.ConnectionStatus;
import vn.edu.bku.smart_emobility_backend.dto.operator.IncidentSource;
import vn.edu.bku.smart_emobility_backend.dto.operator.ResourceType;
import vn.edu.bku.smart_emobility_backend.dto.operator.SpaceReservationKind;
import vn.edu.bku.smart_emobility_backend.dto.operator.TransportMode;

/**
 * Holds supplemental operator/telemetry metadata in RAM for domain entities
 * whose serialized StoreSnapshot records have fixed fields.
 */
@Component
public class OperatorStateRegistry {
    private final Map<String, VehicleMeta> vehicleMeta = new ConcurrentHashMap<>();
    private final Map<String, SpaceMeta> spaceMeta = new ConcurrentHashMap<>();
    private final Map<String, PortMeta> portMeta = new ConcurrentHashMap<>();
    private final Map<String, DispatchOrderMeta> dispatchMeta = new ConcurrentHashMap<>();
    private final Map<String, IncidentMeta> incidentMeta = new ConcurrentHashMap<>();
    private final Map<String, Long> hubVersions = new ConcurrentHashMap<>();
    private final Map<String, Long> requestVersions = new ConcurrentHashMap<>();

    public synchronized VehicleMeta getOrDefaultVehicleMeta(
            String vehicleId,
            Instant defaultObservedAt,
            GeoPoint defaultLocation,
            boolean defaultLocked) {
        return vehicleMeta.computeIfAbsent(vehicleId, id -> new VehicleMeta(
                defaultObservedAt != null ? BatterySource.TELEMETRY : null,
                defaultObservedAt,
                defaultObservedAt != null ? ConnectionStatus.ONLINE : ConnectionStatus.UNKNOWN,
                defaultLocation,
                defaultLocked,
                null,
                new LinkedHashSet<>()));
    }

    public synchronized VehicleMeta getVehicleMeta(String vehicleId) {
        return vehicleMeta.get(vehicleId);
    }

    public synchronized void putVehicleMeta(String vehicleId, VehicleMeta meta) {
        vehicleMeta.put(vehicleId, meta);
    }

    public synchronized SpaceMeta getSpaceMeta(String spaceId) {
        return spaceMeta.get(spaceId);
    }

    public synchronized void putSpaceMeta(String spaceId, SpaceMeta meta) {
        spaceMeta.put(spaceId, meta);
    }

    public synchronized PortMeta getPortMeta(String portId) {
        return portMeta.get(portId);
    }

    public synchronized void putPortMeta(String portId, PortMeta meta) {
        portMeta.put(portId, meta);
    }

    public synchronized DispatchOrderMeta getDispatchMeta(String orderId) {
        return dispatchMeta.get(orderId);
    }

    public synchronized void putDispatchMeta(String orderId, DispatchOrderMeta meta) {
        dispatchMeta.put(orderId, meta);
    }

    public synchronized IncidentMeta getIncidentMeta(String incidentId) {
        return incidentMeta.get(incidentId);
    }

    public synchronized void putIncidentMeta(String incidentId, IncidentMeta meta) {
        incidentMeta.put(incidentId, meta);
    }

    public synchronized long getHubVersion(String hubId) {
        return hubVersions.getOrDefault(hubId, 0L);
    }

    public synchronized void setHubVersion(String hubId, long version) {
        hubVersions.put(hubId, version);
    }

    public synchronized long getRequestVersion(String requestId) {
        return requestVersions.getOrDefault(requestId, 0L);
    }

    public synchronized void setRequestVersion(String requestId, long version) {
        requestVersions.put(requestId, version);
    }

    public synchronized void clear() {
        vehicleMeta.clear();
        spaceMeta.clear();
        portMeta.clear();
        dispatchMeta.clear();
        incidentMeta.clear();
        hubVersions.clear();
        requestVersions.clear();
    }

    public record VehicleMeta(
            BatterySource batterySource,
            Instant batteryUpdatedAt,
            ConnectionStatus connectionStatus,
            GeoPoint location,
            Boolean isLocked,
            String pendingOperationId,
            Set<String> maintenanceCauses) {
        public VehicleMeta {
            maintenanceCauses = maintenanceCauses == null
                    ? Set.of()
                    : Collections.unmodifiableSet(new LinkedHashSet<>(maintenanceCauses));
        }
    }

    public record SpaceMeta(
            Boolean blocked,
            SpaceReservationKind reservationKind,
            String vehicleId,
            String parkingReservationId,
            String dispatchOrderId,
            long version,
            String pendingOperationId,
            Instant observedAt) {
    }

    public record PortMeta(
            Double measuredPowerW,
            Boolean powerEnabled,
            Instant observedAt,
            ConnectionStatus connectionStatus,
            long version,
            String pendingOperationId,
            Set<String> maintenanceCauses) {
        public PortMeta {
            maintenanceCauses = maintenanceCauses == null
                    ? Set.of()
                    : Collections.unmodifiableSet(new LinkedHashSet<>(maintenanceCauses));
        }
    }

    public record DispatchOrderMeta(
            List<String> vehicleIds,
            List<String> reservedDestinationSpaceIds,
            TransportMode transportMode,
            String reason,
            String actorId,
            Instant updatedAt,
            Instant arrivedAt,
            Instant cancelledAt,
            long version,
            long sequenceOrder) {
        public DispatchOrderMeta {
            vehicleIds = vehicleIds == null ? List.of() : List.copyOf(vehicleIds);
            reservedDestinationSpaceIds = reservedDestinationSpaceIds == null
                    ? List.of()
                    : List.copyOf(reservedDestinationSpaceIds);
        }
    }

    public record IncidentMeta(
            ResourceType resourceType,
            IncidentSource source,
            String actorId,
            Instant updatedAt,
            Instant dismissedAt,
            String resolutionNote,
            String dismissNote,
            long version,
            String pendingOperationId,
            long sequenceOrder) {
    }
}
