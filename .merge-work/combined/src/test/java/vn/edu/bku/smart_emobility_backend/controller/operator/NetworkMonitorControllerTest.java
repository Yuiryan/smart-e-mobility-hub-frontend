package vn.edu.bku.smart_emobility_backend.controller.operator;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.bku.smart_emobility_backend.common.domain.*;
import vn.edu.bku.smart_emobility_backend.common.store.InMemoryDataStore;
import vn.edu.bku.smart_emobility_backend.common.store.LatestDataStore;
import vn.edu.bku.smart_emobility_backend.common.store.OperatorStateRegistry;
import vn.edu.bku.smart_emobility_backend.common.store.OperatorStateRegistry.PortMeta;
import vn.edu.bku.smart_emobility_backend.common.store.OperatorStateRegistry.SpaceMeta;
import vn.edu.bku.smart_emobility_backend.common.store.StoreSnapshot;
import vn.edu.bku.smart_emobility_backend.dto.operator.ConnectionStatus;
import vn.edu.bku.smart_emobility_backend.dto.operator.SpaceReservationKind;

@SpringBootTest(properties = "app.data-file=${java.io.tmpdir}/smart-emobility-test-data.json")
@AutoConfigureMockMvc
class NetworkMonitorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InMemoryDataStore dataStore;

    @Autowired
    private OperatorStateRegistry stateRegistry;

    @Autowired
    private LatestDataStore latestDataStore;

    private static final String OP_TOKEN = "op-session-token";
    private static final String STU_TOKEN = "stu-session-token";

    @BeforeEach
    void setUp() {
        stateRegistry.clear();
        latestDataStore.clear();

        Instant now = Instant.now();
        Account opAccount = new Account("acc-op-1", "operator@bku.edu.vn", "hash", AccountRole.OPERATOR, true, now);
        Account stuAccount = new Account("acc-stu-1", "student@bku.edu.vn", "hash", AccountRole.STUDENT, true, now);

        Session opSession = new Session(OP_TOKEN, "acc-op-1", now, now.plusSeconds(1800));
        Session stuSession = new Session(STU_TOKEN, "acc-stu-1", now, now.plusSeconds(1800));

        MobilityHub h1 = new MobilityHub(
                "H1", "Hub 1", "Campus A", new GeoPoint(10.77, 106.66),
                5.0, 10, 1.0, 0, 0);

        // 8 OCCUPIED spaces, 2 AVAILABLE spaces -> 8/10 used (80%)
        List<ParkingSpace> spaces = List.of(
                new ParkingSpace("S01", "H1", ParkingSpaceStatus.OCCUPIED),
                new ParkingSpace("S02", "H1", ParkingSpaceStatus.OCCUPIED),
                new ParkingSpace("S03", "H1", ParkingSpaceStatus.OCCUPIED),
                new ParkingSpace("S04", "H1", ParkingSpaceStatus.OCCUPIED),
                new ParkingSpace("S05", "H1", ParkingSpaceStatus.OCCUPIED),
                new ParkingSpace("S06", "H1", ParkingSpaceStatus.OCCUPIED),
                new ParkingSpace("S07", "H1", ParkingSpaceStatus.OCCUPIED),
                new ParkingSpace("S08", "H1", ParkingSpaceStatus.OCCUPIED),
                new ParkingSpace("S09", "H1", ParkingSpaceStatus.AVAILABLE),
                new ParkingSpace("S10", "H1", ParkingSpaceStatus.AVAILABLE)
        );

        List<Vehicle> vehicles = List.of(
                new Vehicle("V01", "H1", "S01", VehicleType.E_BIKE, 85.0, 0.5, 0.25, VehicleStatus.AVAILABLE, now.minusSeconds(5), 1, VehicleOwnership.SHARED, null),
                new Vehicle("V02", "H1", "S02", VehicleType.E_BIKE, 75.0, 0.5, 0.25, VehicleStatus.AVAILABLE, now.minusSeconds(5), 1, VehicleOwnership.SHARED, null),
                new Vehicle("V03", "H1", "S03", VehicleType.E_BIKE, 65.0, 0.5, 0.25, VehicleStatus.AVAILABLE, now.minusSeconds(5), 1, VehicleOwnership.SHARED, null),
                new Vehicle("V04", "H1", "S04", VehicleType.E_BIKE, 55.0, 0.5, 0.25, VehicleStatus.AVAILABLE, now.minusSeconds(5), 1, VehicleOwnership.SHARED, null),
                new Vehicle("V05", "H1", "S05", VehicleType.E_BIKE, 45.0, 0.5, 0.25, VehicleStatus.AVAILABLE, now.minusSeconds(5), 1, VehicleOwnership.SHARED, null),
                new Vehicle("V06", "H1", "S06", VehicleType.E_BIKE, 90.0, 0.5, 0.25, VehicleStatus.RESERVED, now.minusSeconds(5), 1, VehicleOwnership.SHARED, null),
                new Vehicle("V07", "H1", "S07", VehicleType.E_BIKE, 15.0, 0.5, 0.25, VehicleStatus.LOW_BATTERY, now.minusSeconds(5), 1, VehicleOwnership.SHARED, null),
                new Vehicle("V08", "H1", "S08", VehicleType.E_BIKE, 50.0, 0.5, 0.25, VehicleStatus.MAINTENANCE, now.minusSeconds(5), 1, VehicleOwnership.SHARED, null)
        );

        List<ChargingPort> ports = List.of(
                new ChargingPort("C1", "H1", "Port 1", 0.25, ChargingPortStatus.AVAILABLE),
                new ChargingPort("C2", "H1", "Port 2", 0.25, ChargingPortStatus.CHARGING)
        );

        ChargingRequest chgReq = new ChargingRequest("CR1", "acc-stu-1", "V07", "H1", 80, now.minusSeconds(60), ChargingRequestStatus.CHARGING);
        ChargingAllocation chgAlloc = new ChargingAllocation("CA1", "CR1", "C2", now.minusSeconds(60), now.plusSeconds(1800), 0.25, ChargingAllocationStatus.ACTIVE);

        StoreSnapshot snapshot = new StoreSnapshot(
                12L,
                List.of(opAccount, stuAccount),
                List.of(opSession, stuSession),
                List.of(h1),
                vehicles,
                spaces,
                List.of(),
                List.of(),
                List.of(),
                ports,
                List.of(chgReq),
                List.of(chgAlloc),
                List.of(),
                List.of()
        );
        dataStore.restore(snapshot);
    }

    @Test
    @DisplayName("TC-08.1: H1 có 8/10 chỗ đang sử dụng -> Tổng quan và chi tiết H1 hiển thị đúng 8 chỗ đang dùng trong 10 chỗ (80%, WARNING)")
    void testTc08_1_NetworkReportAndHubDetail() throws Exception {
        mockMvc.perform(get("/api/operator/network-report")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.stateVersion").value(12))
                .andExpect(jsonPath("$.hubs.length()").value(1))
                .andExpect(jsonPath("$.hubs[0].hubId").value("H1"))
                .andExpect(jsonPath("$.hubs[0].capacity").value(10))
                .andExpect(jsonPath("$.hubs[0].usableCapacity").value(10))
                .andExpect(jsonPath("$.hubs[0].occupiedCount").value(8))
                .andExpect(jsonPath("$.hubs[0].reservedCount").value(0))
                .andExpect(jsonPath("$.hubs[0].inboundCount").value(0))
                .andExpect(jsonPath("$.hubs[0].freeSpaceCount").value(2))
                .andExpect(jsonPath("$.hubs[0].occupancyPct").value(80.0))
                .andExpect(jsonPath("$.hubs[0].parkingSeverity").value("WARNING"))
                .andExpect(jsonPath("$.hubs[0].dataQuality").value("FRESH"))
                .andExpect(jsonPath("$.hubs[0].vehicleCountsByStatus.AVAILABLE").value(5))
                .andExpect(jsonPath("$.hubs[0].vehicleCountsByStatus.RESERVED").value(1))
                .andExpect(jsonPath("$.hubs[0].vehicleCountsByStatus.LOW_BATTERY").value(1))
                .andExpect(jsonPath("$.hubs[0].vehicleCountsByStatus.MAINTENANCE").value(1));

        mockMvc.perform(get("/api/operator/hubs/H1/report")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stateVersion").value(12))
                .andExpect(jsonPath("$.hub.hubId").value("H1"))
                .andExpect(jsonPath("$.hub.capacity").value(10))
                .andExpect(jsonPath("$.hub.occupiedCount").value(8))
                .andExpect(jsonPath("$.hub.freeSpaceCount").value(2))
                .andExpect(jsonPath("$.hub.occupancyPct").value(80.0));
    }

    @Test
    @DisplayName("TC-08.2: Cổng C1 không phản hồi (OFFLINE / dữ liệu trễ) -> C1 hiện STALE/OFFLINE kèm thời điểm đọc cuối, charging severity MISSING_DATA")
    void testTc08_2_PortUnresponsiveShowsStaleAndLastObservedAt() throws Exception {
        Instant lastRead = Instant.parse("2026-11-24T06:00:00Z"); // 13:00:00+07:00
        stateRegistry.putPortMeta("C1", new PortMeta(
                null,
                true,
                lastRead,
                ConnectionStatus.OFFLINE,
                2L,
                null,
                Set.of()
        ));

        mockMvc.perform(get("/api/operator/charging-ports/C1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portId").value("C1"))
                .andExpect(jsonPath("$.hubId").value("H1"))
                .andExpect(jsonPath("$.connectionStatus").value("OFFLINE"))
                .andExpect(jsonPath("$.dataQuality").value("STALE"))
                .andExpect(jsonPath("$.observedAt").value("2026-11-24T13:00:00+07:00"))
                .andExpect(jsonPath("$.measuredPowerW").isEmpty());

        mockMvc.perform(get("/api/operator/hubs/H1/report")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hub.charging.dataQuality").value("MISSING"))
                .andExpect(jsonPath("$.hub.charging.severity").value("MISSING_DATA"))
                .andExpect(jsonPath("$.hub.charging.observedAt").value("2026-11-24T13:00:00+07:00"))
                .andExpect(jsonPath("$.hub.severity").value("MISSING_DATA"));
    }

    @Test
    @DisplayName("Kiểm tra danh mục dùng chung GET /api/hubs và GET /api/hubs/{hubId}/spaces cho cả Student và Operator")
    void testHubCatalogAndSpacesEndpoints() throws Exception {
        // Block space S10 and reserve S09 as DISPATCH_INBOUND
        stateRegistry.putSpaceMeta("S10", new SpaceMeta(true, null, null, null, null, 1L, null, Instant.now()));
        stateRegistry.putSpaceMeta("S09", new SpaceMeta(false, SpaceReservationKind.DISPATCH_INBOUND, null, null, "disp-01", 2L, null, Instant.now()));

        mockMvc.perform(get("/api/hubs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + STU_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshotVersion").value(12))
                .andExpect(jsonPath("$.items[0].hubId").value("H1"))
                .andExpect(jsonPath("$.items[0].capacity").value(10))
                .andExpect(jsonPath("$.items[0].usableCapacity").value(9))
                .andExpect(jsonPath("$.items[0].blockedCount").value(1))
                .andExpect(jsonPath("$.items[0].freeSpaceCount").value(1));

        mockMvc.perform(get("/api/hubs/H1/spaces")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshotVersion").value(12))
                .andExpect(jsonPath("$.items.length()").value(10))
                .andExpect(jsonPath("$.items[0].spaceId").value("S01"))
                .andExpect(jsonPath("$.items[0].status").value("OCCUPIED"))
                .andExpect(jsonPath("$.items[0].vehicleId").value("V01"))
                .andExpect(jsonPath("$.items[9].spaceId").value("S10"))
                .andExpect(jsonPath("$.items[9].blocked").value(true))
                .andExpect(jsonPath("$.items[9].allowedActions[0]").value("UNBLOCK_SPACE"));
    }

    @Test
    @DisplayName("Kiểm tra các endpoints danh mục tài nguyên Operator (vehicles, parking-spaces, charging-ports, charging-requests)")
    void testOperatorResourceCatalogsAndDetails() throws Exception {
        mockMvc.perform(get("/api/operator/vehicles")
                        .param("hubId", "H1")
                        .param("status", "LOW_BATTERY")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].vehicleId").value("V07"))
                .andExpect(jsonPath("$.items[0].activeUsage.chargingRequestId").value("CR1"))
                .andExpect(jsonPath("$.items[0].activeUsage.allocationId").value("CA1"));

        mockMvc.perform(get("/api/operator/vehicles/V01")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleId").value("V01"))
                .andExpect(jsonPath("$.dispatchModes[0]").value("SELF_DRIVE"))
                .andExpect(jsonPath("$.dispatchModes[1]").value("TRUCK"));

        mockMvc.perform(get("/api/operator/parking-spaces/S01")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spaceId").value("S01"))
                .andExpect(jsonPath("$.vehicleId").value("V01"));

        mockMvc.perform(get("/api/operator/charging-ports")
                        .param("hubId", "H1")
                        .param("status", "CHARGING")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].portId").value("C2"))
                .andExpect(jsonPath("$.items[0].activeChargingRequestId").value("CR1"))
                .andExpect(jsonPath("$.items[0].allocationId").value("CA1"))
                .andExpect(jsonPath("$.items[0].vehicleId").value("V07"));

        mockMvc.perform(get("/api/operator/charging-requests")
                        .param("hubId", "H1")
                        .param("status", "CHARGING")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].chargingRequestId").value("CR1"))
                .andExpect(jsonPath("$.items[0].portId").value("C2"))
                .andExpect(jsonPath("$.items[0].allocationId").value("CA1"));
    }

    @Test
    @DisplayName("Kiểm tra phân quyền (401/403), validate query/enum (400), hub không tồn tại (404), và invariant (500 STATE_INCONSISTENT)")
    void testAuthAndValidationAndInvariantErrors() throws Exception {
        // 401 when missing Authorization header
        mockMvc.perform(get("/api/operator/network-report"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        // 403 when Student calls /api/operator/*
        mockMvc.perform(get("/api/operator/network-report")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + STU_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        // 400 when unknown query param is passed
        mockMvc.perform(get("/api/operator/vehicles")
                        .param("unknownParam", "123")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        // 400 when invalid enum value is passed
        mockMvc.perform(get("/api/operator/vehicles")
                        .param("status", "INVALID_ENUM")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        // 404 when hub filter does not exist
        mockMvc.perform(get("/api/operator/vehicles")
                        .param("hubId", "NON_EXISTENT_HUB")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        // 500 STATE_INCONSISTENT when occupied spaces exceed hub capacity
        MobilityHub brokenHub = new MobilityHub(
                "H1", "Hub 1", "Campus A", new GeoPoint(10.77, 106.66),
                5.0, 5, 1.0, 0, 0); // capacity=5, but 8 spaces are OCCUPIED
        dataStore.put(brokenHub);

        mockMvc.perform(get("/api/operator/network-report")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("STATE_INCONSISTENT"));
    }
}
