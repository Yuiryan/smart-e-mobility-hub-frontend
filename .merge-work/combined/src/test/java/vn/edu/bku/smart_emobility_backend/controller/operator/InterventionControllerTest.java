package vn.edu.bku.smart_emobility_backend.controller.operator;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.bku.smart_emobility_backend.common.domain.*;
import vn.edu.bku.smart_emobility_backend.common.store.IdempotencyStore;
import vn.edu.bku.smart_emobility_backend.common.store.InMemoryDataStore;
import vn.edu.bku.smart_emobility_backend.common.store.OperatorStateRegistry;
import vn.edu.bku.smart_emobility_backend.common.store.StoreSnapshot;
import vn.edu.bku.smart_emobility_backend.service.operator.AuditLogService;
import vn.edu.bku.smart_emobility_backend.service.operator.DeviceGateway.CommandOutcome;
import vn.edu.bku.smart_emobility_backend.service.operator.DeviceGateway.DeviceCommand;
import vn.edu.bku.smart_emobility_backend.service.operator.DeviceGateway.DeviceState;
import vn.edu.bku.smart_emobility_backend.service.operator.DeviceGateway.OutcomeStatus;
import vn.edu.bku.smart_emobility_backend.service.operator.InProcessSimulatorDeviceGateway;
import vn.edu.bku.smart_emobility_backend.service.operator.InProcessSimulatorDeviceGateway.SimulationMode;
import vn.edu.bku.smart_emobility_backend.service.operator.OperationOrchestrator;

@SpringBootTest(properties = "app.data-file=${java.io.tmpdir}/smart-emobility-test-data.json")
@AutoConfigureMockMvc
class InterventionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InMemoryDataStore dataStore;

    @Autowired
    private OperatorStateRegistry stateRegistry;

    @Autowired
    private IdempotencyStore idempotencyStore;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private OperationOrchestrator operationOrchestrator;

    @Autowired
    private InProcessSimulatorDeviceGateway deviceGateway;

    @Autowired
    private JsonMapper jsonMapper;

    private static final String OP_TOKEN = "op-token-09";

    @BeforeEach
    void setUp() {
        stateRegistry.clear();
        idempotencyStore.clear();
        auditLogService.clear();
        operationOrchestrator.clear();
        deviceGateway.reset();

        Instant now = Instant.now();
        Account opAccount = new Account("acc-op-1", "operator@bku.edu.vn", "hash", AccountRole.OPERATOR, true, now);
        Session opSession = new Session(OP_TOKEN, "acc-op-1", now, now.plusSeconds(1800));

        MobilityHub h1 = new MobilityHub(
                "H1", "Hub 1", "Campus A", new GeoPoint(10.77, 106.66),
                5.0, 10, 1.0, 0, 0);

        List<ParkingSpace> spaces = List.of(
                new ParkingSpace("S01", "H1", ParkingSpaceStatus.OCCUPIED),
                new ParkingSpace("S02", "H1", ParkingSpaceStatus.RESERVED),
                new ParkingSpace("S03", "H1", ParkingSpaceStatus.AVAILABLE)
        );

        List<Vehicle> vehicles = List.of(
                new Vehicle("V_IDLE", "H1", "S01", VehicleType.E_BIKE, 80.0, 0.5, 0.25,
                        VehicleStatus.AVAILABLE, now.minusSeconds(5), 3L, VehicleOwnership.SHARED, null),
                new Vehicle("V_RUNNING", null, null, VehicleType.E_BIKE, 60.0, 0.5, 0.25,
                        VehicleStatus.IN_USE, now.minusSeconds(5), 2L, VehicleOwnership.SHARED, null),
                new Vehicle("V_RESERVED", "H1", null, VehicleType.E_BIKE, 75.0, 0.5, 0.25,
                        VehicleStatus.RESERVED, now.minusSeconds(5), 1L, VehicleOwnership.SHARED, null)
        );

        Ride activeRide = new Ride("R01", "acc-stu-1", "V_RUNNING", now.minusSeconds(300), null, 80, null, RideStatus.ACTIVE);
        Reservation activeRes = new Reservation("RES01", "acc-stu-1", "V_RESERVED", "hash", now.minusSeconds(60), now.plusSeconds(840), ReservationStatus.ACTIVE);
        ParkingReservation activeParkRes = new ParkingReservation("PRES01", "acc-stu-1", "V_RUNNING", "S02", now, now.plusSeconds(900), now, now.plusSeconds(900), ParkingReservationStatus.ACTIVE);

        List<ChargingPort> ports = List.of(
                new ChargingPort("C1", "H1", "Port 1", 0.25, ChargingPortStatus.CHARGING),
                new ChargingPort("C2", "H1", "Port 2", 0.25, ChargingPortStatus.AVAILABLE)
        );

        ChargingRequest chgReq1 = new ChargingRequest("CR1", "acc-stu-1", "V_IDLE", "H1", 90, now.minusSeconds(120), ChargingRequestStatus.CHARGING);
        ChargingAllocation chgAlloc1 = new ChargingAllocation("CA1", "CR1", "C1", now.minusSeconds(120), now.plusSeconds(1200), 0.25, ChargingAllocationStatus.ACTIVE);

        StoreSnapshot snapshot = new StoreSnapshot(
                10L,
                List.of(opAccount),
                List.of(opSession),
                List.of(h1),
                vehicles,
                spaces,
                List.of(activeRes),
                List.of(activeParkRes),
                List.of(activeRide),
                ports,
                List.of(chgReq1),
                List.of(chgAlloc1),
                List.of(),
                List.of()
        );
        dataStore.restore(snapshot);
    }

    @Test
    @DisplayName("TC-09.1: Admin yêu cầu khóa xe đang chạy -> Chặn thao tác (409), không phát lệnh khóa xuống IoT, ghi nhật ký REJECTED")
    void testTc09_1_RejectLockOnRunningVehicle() throws Exception {
        String body = """
                {
                  "resourceType": "VEHICLE",
                  "resourceId": "V_RUNNING",
                  "action": "LOCK",
                  "reason": "Thử khóa xe đang chạy chuyến",
                  "expectedVersion": 2,
                  "parameters": {}
                }
                """;

        mockMvc.perform(post("/api/operator/interventions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_BUSY"));

        // Verify no IoT command was submitted
        org.junit.jupiter.api.Assertions.assertTrue(deviceGateway.submittedCommands().isEmpty());

        // Verify AuditLog recorded REJECTED
        mockMvc.perform(get("/api/operator/audit-logs")
                        .param("resourceType", "VEHICLE")
                        .param("resourceId", "V_RUNNING")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].action").value("LOCK"))
                .andExpect(jsonPath("$.items[0].outcome").value("REJECTED"))
                .andExpect(jsonPath("$.items[0].errorCode").value("RESOURCE_BUSY"));
    }

    @Test
    @DisplayName("TC-09.2: Cổng C1 đang sạc, gửi lệnh ngắt khẩn nhưng thiết bị không phản hồi -> Nhật ký & Operation ghi PENDING/RECONCILING, không báo đã ngắt thành công")
    void testTc09_2_StopChargingWithoutDeviceResponseStaysPendingAndGuardsResource() throws Exception {
        deviceGateway.setMode(SimulationMode.DEFER_ACK);

        String body = """
                {
                  "resourceType": "CHARGING_PORT",
                  "resourceId": "C1",
                  "action": "STOP_CHARGING",
                  "reason": "Ngắt sạc khẩn cấp do quá nhiệt",
                  "expectedVersion": 0,
                  "parameters": {
                    "chargingRequestId": "CR1",
                    "allocationId": "CA1"
                  }
                }
                """;

        MvcResult postResult = mockMvc.perform(post("/api/operator/interventions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(2))
                .andReturn();

        Map<?, ?> responseMap = jsonMapper.readValue(postResult.getResponse().getContentAsString(), Map.class);
        String operationId = (String) responseMap.get("operationId");

        // Operation is PENDING, result is null (not reported as SUCCEEDED)
        mockMvc.perform(get("/api/operations/" + operationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationId").value(operationId))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.result").isEmpty());

        // C1 is still CHARGING (state not prematurely mutated) and has pendingOperationId guard
        mockMvc.perform(get("/api/operator/charging-ports/C1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHARGING"))
                .andExpect(jsonPath("$.pendingOperationId").value(operationId));

        // When device returns UNKNOWN status, operation transitions to RECONCILING and audit is RECONCILING
        DeviceCommand cmd = deviceGateway.submittedCommands().values().iterator().next();
        deviceGateway.emitOutcome(new CommandOutcome(
                cmd.commandId(),
                operationId,
                OutcomeStatus.UNKNOWN,
                Instant.now(),
                null,
                null));

        mockMvc.perform(get("/api/operations/" + operationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECONCILING"))
                .andExpect(jsonPath("$.result").isEmpty());

        mockMvc.perform(get("/api/operator/audit-logs")
                        .param("operationId", operationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].outcome").value("RECONCILING"));
    }

    @Test
    @DisplayName("TC-09.3: Admin chọn cổng C1, ghi lý do ngắt và xác nhận, IoT trả ACK thành công đúng mã lệnh -> Cập nhật C1 đúng 1 lần và ghi nhật ký SUCCEEDED")
    void testTc09_3_StopChargingAckSuccessCommitsOnceAndUpdatesAudit() throws Exception {
        deviceGateway.setMode(SimulationMode.DEFER_ACK);

        String body = """
                {
                  "resourceType": "CHARGING_PORT",
                  "resourceId": "C1",
                  "action": "STOP_CHARGING",
                  "reason": "Ngắt điện khẩn cấp cổng C1",
                  "expectedVersion": 0,
                  "parameters": {
                    "chargingRequestId": "CR1",
                    "allocationId": "CA1"
                  }
                }
                """;

        MvcResult postResult = mockMvc.perform(post("/api/operator/interventions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andReturn();

        Map<?, ?> responseMap = jsonMapper.readValue(postResult.getResponse().getContentAsString(), Map.class);
        String operationId = (String) responseMap.get("operationId");

        DeviceCommand cmd = deviceGateway.submittedCommands().values().iterator().next();
        CommandOutcome ackSuccess = new CommandOutcome(
                cmd.commandId(),
                operationId,
                OutcomeStatus.ACK_SUCCESS,
                Instant.now(),
                new DeviceState(null, false, 82.0, true, null),
                null);

        // Emit ACK_SUCCESS twice to verify deduplication (updates state exactly once)
        deviceGateway.emitOutcome(ackSuccess);
        deviceGateway.emitOutcome(ackSuccess);

        mockMvc.perform(get("/api/operations/" + operationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.resultType").value("INTERVENTION"))
                .andExpect(jsonPath("$.result.resourceId").value("C1"))
                .andExpect(jsonPath("$.result.action").value("STOP_CHARGING"))
                .andExpect(jsonPath("$.result.resourceVersion").value(1));

        // C1 is now MAINTENANCE with version=1 (not 2) and powerEnabled=false
        mockMvc.perform(get("/api/operator/charging-ports/C1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MAINTENANCE"))
                .andExpect(jsonPath("$.powerEnabled").value(false))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.pendingOperationId").isEmpty());

        // AuditLog shows SUCCEEDED
        mockMvc.perform(get("/api/operator/audit-logs")
                        .param("operationId", operationId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].outcome").value("SUCCEEDED"));
    }

    @Test
    @DisplayName("Kiểm tra các hành động khác: CANCEL_RESERVATION, CANCEL_PARKING_RESERVATION, SET_BATTERY, BLOCK_SPACE/UNBLOCK_SPACE, và Idempotency")
    void testOtherInterventionActionsAndIdempotency() throws Exception {
        // 1. CANCEL_RESERVATION on V_RESERVED
        String cancelResBody = """
                {
                  "resourceType": "VEHICLE",
                  "resourceId": "V_RESERVED",
                  "action": "CANCEL_RESERVATION",
                  "reason": "Người dùng báo không tới nhận xe",
                  "expectedVersion": 1,
                  "parameters": {
                    "reservationId": "RES01"
                  }
                }
                """;
        String idemKey = UUID.randomUUID().toString();
        MvcResult res1 = mockMvc.perform(post("/api/operator/interventions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN)
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelResBody))
                .andExpect(status().isAccepted())
                .andReturn();
        String op1 = (String) jsonMapper.readValue(res1.getResponse().getContentAsString(), Map.class).get("operationId");

        // Replay with same Idempotency-Key and same payload returns exact same operationId
        mockMvc.perform(post("/api/operator/interventions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN)
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelResBody))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.operationId").value(op1));

        // Replay with same Idempotency-Key but different payload returns 409 IDEMPOTENCY_CONFLICT
        String diffPayload = cancelResBody.replace("Người dùng báo không tới nhận xe", "Lý do khác");
        mockMvc.perform(post("/api/operator/interventions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN)
                        .header("Idempotency-Key", idemKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(diffPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("IDEMPOTENCY_CONFLICT"));

        // Verify V_RESERVED is now AVAILABLE with version=2
        mockMvc.perform(get("/api/operator/vehicles/V_RESERVED")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.version").value(2));

        // 2. SET_BATTERY on V_RESERVED to 15% -> becomes LOW_BATTERY, batterySource=MANUAL
        String setBatteryBody = """
                {
                  "resourceType": "VEHICLE",
                  "resourceId": "V_RESERVED",
                  "action": "SET_BATTERY",
                  "reason": "Hiệu chỉnh SoC theo đo thực địa",
                  "expectedVersion": 2,
                  "parameters": {
                    "batteryPct": 15
                  }
                }
                """;
        mockMvc.perform(post("/api/operator/interventions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(setBatteryBody))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/operator/vehicles/V_RESERVED")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batteryPct").value(15.0))
                .andExpect(jsonPath("$.batterySource").value("MANUAL"))
                .andExpect(jsonPath("$.status").value("LOW_BATTERY"))
                .andExpect(jsonPath("$.version").value(3));

        // 3. BLOCK_SPACE on S03
        String blockSpaceBody = """
                {
                  "resourceType": "PARKING_SPACE",
                  "resourceId": "S03",
                  "action": "BLOCK_SPACE",
                  "reason": "Khóa ô đỗ để sơn kẻ vạch",
                  "expectedVersion": 0,
                  "parameters": {}
                }
                """;
        mockMvc.perform(post("/api/operator/interventions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(blockSpaceBody))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/operator/parking-spaces/S03")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocked").value(true))
                .andExpect(jsonPath("$.version").value(1));

        // 4. CANCEL_PARKING_RESERVATION on S02
        String cancelParkBody = """
                {
                  "resourceType": "PARKING_SPACE",
                  "resourceId": "S02",
                  "action": "CANCEL_PARKING_RESERVATION",
                  "reason": "Giải phóng giữ chỗ đỗ quá hạn",
                  "expectedVersion": 0,
                  "parameters": {
                    "parkingReservationId": "PRES01"
                  }
                }
                """;
        mockMvc.perform(post("/api/operator/interventions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelParkBody))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/operator/parking-spaces/S02")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.version").value(1));
    }
}
