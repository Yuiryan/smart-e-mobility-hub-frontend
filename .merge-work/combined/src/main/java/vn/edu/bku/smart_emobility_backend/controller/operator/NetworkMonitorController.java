package vn.edu.bku.smart_emobility_backend.controller.operator;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.bku.smart_emobility_backend.common.domain.VehicleStatus;
import vn.edu.bku.smart_emobility_backend.common.security.OperatorAccessGuard;
import vn.edu.bku.smart_emobility_backend.common.web.QueryValidationUtils;
import vn.edu.bku.smart_emobility_backend.dto.operator.*;
import vn.edu.bku.smart_emobility_backend.service.operator.MonitoringAggregator;

@RestController
@RequestMapping("/api/operator")
public class NetworkMonitorController {
    private final MonitoringAggregator monitoringAggregator;
    private final OperatorAccessGuard accessGuard;

    public NetworkMonitorController(
            MonitoringAggregator monitoringAggregator,
            OperatorAccessGuard accessGuard) {
        this.monitoringAggregator = monitoringAggregator;
        this.accessGuard = accessGuard;
    }

    @GetMapping("/network-report")
    public ResponseEntity<NetworkReportDTO> getNetworkData(HttpServletRequest request) {
        accessGuard.requireOperator(request);
        QueryValidationUtils.validateQueryParams(request, Set.of());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(monitoringAggregator.getNetworkData());
    }

    @GetMapping("/hubs/{hubId}/report")
    public ResponseEntity<HubReportDTO> getHubDetails(
            @PathVariable String hubId,
            HttpServletRequest request) {
        accessGuard.requireOperator(request);
        QueryValidationUtils.validateQueryParams(request, Set.of());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(monitoringAggregator.getHubDetails(hubId));
    }

    @GetMapping("/vehicles")
    public ResponseEntity<SnapshotListResponseDTO<OperatorVehicleDTO>> listVehicles(
            @RequestParam(required = false) String hubId,
            @RequestParam(required = false) String status,
            HttpServletRequest request) {
        accessGuard.requireOperator(request);
        QueryValidationUtils.validateQueryParams(request, Set.of("hubId", "status"));
        VehicleStatus parsedStatus = QueryValidationUtils.parseEnumParam(VehicleStatus.class, "status", status);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(monitoringAggregator.listVehicles(hubId, parsedStatus));
    }

    @GetMapping("/vehicles/{vehicleId}")
    public ResponseEntity<OperatorVehicleDTO> getVehicle(
            @PathVariable String vehicleId,
            HttpServletRequest request) {
        accessGuard.requireOperator(request);
        QueryValidationUtils.validateQueryParams(request, Set.of());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(monitoringAggregator.getVehicle(vehicleId));
    }

    @GetMapping("/parking-spaces/{spaceId}")
    public ResponseEntity<OperatorParkingSpaceDTO> getParkingSpace(
            @PathVariable String spaceId,
            HttpServletRequest request) {
        accessGuard.requireOperator(request);
        QueryValidationUtils.validateQueryParams(request, Set.of());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(monitoringAggregator.getParkingSpace(spaceId));
    }

    @GetMapping("/charging-ports")
    public ResponseEntity<SnapshotListResponseDTO<OperatorChargingPortDTO>> listChargingPorts(
            @RequestParam(required = false) String hubId,
            @RequestParam(required = false) String status,
            HttpServletRequest request) {
        accessGuard.requireOperator(request);
        QueryValidationUtils.validateQueryParams(request, Set.of("hubId", "status"));
        OperatorPortStatus parsedStatus = QueryValidationUtils.parseEnumParam(
                OperatorPortStatus.class, "status", status);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(monitoringAggregator.listChargingPorts(hubId, parsedStatus));
    }

    @GetMapping("/charging-ports/{portId}")
    public ResponseEntity<OperatorChargingPortDTO> getChargingPort(
            @PathVariable String portId,
            HttpServletRequest request) {
        accessGuard.requireOperator(request);
        QueryValidationUtils.validateQueryParams(request, Set.of());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(monitoringAggregator.getChargingPort(portId));
    }

    @GetMapping("/charging-requests")
    public ResponseEntity<SnapshotListResponseDTO<OperatorChargingRequestDTO>> listChargingRequests(
            @RequestParam(required = false) String hubId,
            @RequestParam(required = false) String vehicleId,
            @RequestParam(required = false) String status,
            HttpServletRequest request) {
        accessGuard.requireOperator(request);
        QueryValidationUtils.validateQueryParams(request, Set.of("hubId", "vehicleId", "status"));
        OperatorChargingRequestStatus parsedStatus = QueryValidationUtils.parseEnumParam(
                OperatorChargingRequestStatus.class, "status", status);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(monitoringAggregator.listChargingRequests(hubId, vehicleId, parsedStatus));
    }
}
