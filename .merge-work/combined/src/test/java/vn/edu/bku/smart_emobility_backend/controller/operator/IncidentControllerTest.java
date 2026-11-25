package vn.edu.bku.smart_emobility_backend.controller.operator;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import vn.edu.bku.smart_emobility_backend.common.store.OperatorStateRegistry.PortMeta;
import vn.edu.bku.smart_emobility_backend.common.store.StoreSnapshot;
import vn.edu.bku.smart_emobility_backend.dto.operator.ConnectionStatus;
import vn.edu.bku.smart_emobility_backend.service.operator.AuditLogService;
import vn.edu.bku.smart_emobility_backend.service.operator.InProcessSimulatorDeviceGateway;
import vn.edu.bku.smart_emobility_backend.service.operator.InProcessSimulatorDeviceGateway.SimulationMode;
import vn.edu.bku.smart_emobility_backend.service.operator.IncidentService;
import vn.edu.bku.smart_emobility_backend.service.operator.OperationOrchestrator;

@SpringBootTest(properties = "app.data-file=${java.io.tmpdir}/smart-emobility-test-data.json")
@AutoConfigureMockMvc
class IncidentControllerTest {

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
    private IncidentService incidentService;

    @Autowired
    private InProcessSimulatorDeviceGateway deviceGateway;

    @Autowired
    private JsonMapper jsonMapper;

    private static final String OP_TOKEN = "op-token-12";

    @BeforeEach
    void setUp() {
        stateRegistry.clear();
        idempotencyStore.clear();
        auditLogService.clear();
        operationOrchestrator.clear();
        incidentService.clear();
        deviceGateway.reset();

        Instant now = Instant.now();
        Account opAccount = new Account("acc-op-1", "operator@bku.edu.vn", "hash", AccountRole.OPERATOR, true, now);
        Session opSession = new Session(OP_TOKEN, "acc-op-1", now, now.plusSeconds(1800));

        MobilityHub h1 = new MobilityHub(
                "H1", "Hub 1", "Campus A", new GeoPoint(10.77, 106.66),
                5.0, 10, 1.0, 0, 0);

        List<ParkingSpace> spaces = List.of(
                new ParkingSpace("S01", "H1", ParkingSpaceStatus.OCCUPIED)
        );

        List<Vehicle> vehicles = List.of(
                new Vehicle("V01", "H1", "S01", VehicleType.E_BIKE, 80.0, 0.5, 0.25,
                        VehicleStatus.AVAILABLE, now.minusSeconds(5), 3L, VehicleOwnership.SHARED, null)
        );

        List<ChargingPort> ports = List.of(
                new ChargingPort("C1", "H1", "Port 1", 0.25, ChargingPortStatus.AVAILABLE),
                new ChargingPort("C2", "H1", "Port 2", 0.25, ChargingPortStatus.AVAILABLE)
        );

        StoreSnapshot snapshot = new StoreSnapshot(
                40L,
                List.of(opAccount),
                List.of(opSession),
                List.of(h1),
                vehicles,
                spaces,
                List.of(),
                List.of(),
                List.of(),
                ports,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
        dataStore.restore(snapshot);
    }

    @Test
    @DisplayName("TC-12.1: C1 lỗi và thiết bị xác nhận khóa -> Chọn đưa vào bảo trì -> C1 ở trạng thái MAINTENANCE và không còn khả dụng")
    void testTc12_1_SendPortC1ToMaintenanceAfterAckLocksAndRemovesFromAvailable() throws Exception {
        // Step 1: Create incident on C1 (create only records OPEN, does not change C1 status yet)
        String createBody = """
                {
                  "resourceType": "CHARGING_PORT",
                  "resourceId": "C1",
                  "severity": "MEDIUM",
                  "description": "Cổng sạc C1 rò điện bất thường"
                }
                """;

        MvcResult createRes = mockMvc.perform(post("/api/operator/incidents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.resourceType").value("CHARGING_PORT"))
                .andExpect(jsonPath("$.resourceId").value("C1"))
                .andExpect(jsonPath("$.hubId").value("H1"))
                .andExpect(jsonPath("$.severity").value("MEDIUM"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.source").value("OPERATOR"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.resourceVersion").value(0))
                .andReturn();

        String incidentId = (String) jsonMapper.readValue(createRes.getResponse().getContentAsString(), Map.class)
                .get("incidentId");

        // Step 2: Send C1 to maintenance -> returns 202 Accepted, after ACK_SUCCESS transitions incident & C1 to MAINTENANCE
        String maintBody = """
                {
                  "expectedVersion": 0,
                  "expectedResourceVersion": 0,
                  "reason": "Khóa nguồn cổng sạc C1 để bảo trì"
                }
                """;

        MvcResult maintRes = mockMvc.perform(post("/api/operator/incidents/" + incidentId + "/maintenance")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(maintBody))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        String opId = (String) jsonMapper.readValue(maintRes.getResponse().getContentAsString(), Map.class)
                .get("operationId");

        // Verify operation SUCCEEDED with INCIDENT result showing status=MAINTENANCE
        mockMvc.perform(get("/api/operations/" + opId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kind").value("INCIDENT_MAINTENANCE"))
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.resultType").value("INCIDENT"))
                .andExpect(jsonPath("$.result.incidentId").value(incidentId))
                .andExpect(jsonPath("$.result.status").value("MAINTENANCE"))
                .andExpect(jsonPath("$.result.version").value(1))
                .andExpect(jsonPath("$.result.resourceVersion").value(1));

        // Verify C1 is now MAINTENANCE, powerEnabled=false, removed from AVAILABLE port list
        mockMvc.perform(get("/api/operator/charging-ports/C1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MAINTENANCE"))
                .andExpect(jsonPath("$.powerEnabled").value(false))
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(get("/api/operator/charging-ports")
                        .param("hubId", "H1")
                        .param("status", "AVAILABLE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].portId").value("C2"));
    }

    @Test
    @DisplayName("TC-12.2: C1 còn lỗi (đang MAINTENANCE), bấm bỏ qua cảnh báo nhẹ (LOW) -> Cảnh báo chuyển DISMISSED, nhưng C1 vẫn giữ nguyên MAINTENANCE (không khả dụng)")
    void testTc12_2_DismissLowIncidentDoesNotRecoverFaultyDevice() throws Exception {
        // Put C1 in faulty MAINTENANCE state with an existing cause
        dataStore.put(new ChargingPort("C1", "H1", "Port 1", 0.25, ChargingPortStatus.OUT_OF_SERVICE));
        stateRegistry.putPortMeta("C1", new PortMeta(
                0.0, false, Instant.now(), ConnectionStatus.ONLINE, 5L, null, Set.of("MANUAL")));

        // Create a minor LOW severity alert on C1
        String createLowBody = """
                {
                  "resourceType": "CHARGING_PORT",
                  "resourceId": "C1",
                  "severity": "LOW",
                  "description": "Đèn LED báo hiệu trên C1 hơi mờ"
                }
                """;

        MvcResult createRes = mockMvc.perform(post("/api/operator/incidents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createLowBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.resourceVersion").value(5))
                .andReturn();

        String incidentId = (String) jsonMapper.readValue(createRes.getResponse().getContentAsString(), Map.class)
                .get("incidentId");

        // Dismiss the LOW alert
        String dismissBody = """
                {
                  "expectedVersion": 0,
                  "dismissNote": "Cảnh báo nhẹ đã kiểm tra, chưa ảnh hưởng nhưng C1 vẫn đang bảo trì phần cứng"
                }
                """;

        mockMvc.perform(post("/api/operator/incidents/" + incidentId + "/dismiss")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dismissBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value(incidentId))
                .andExpect(jsonPath("$.status").value("DISMISSED"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.resourceVersion").value(5))
                .andExpect(jsonPath("$.dismissedAt").isNotEmpty())
                .andExpect(jsonPath("$.allowedActions").isEmpty());

        // C1 must STILL be in MAINTENANCE and version=5 (not recovered to AVAILABLE!)
        mockMvc.perform(get("/api/operator/charging-ports/C1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MAINTENANCE"))
                .andExpect(jsonPath("$.powerEnabled").value(false))
                .andExpect(jsonPath("$.version").value(5));
    }

    @Test
    @DisplayName("Kiểm tra khôi phục theo nguyên nhân (cause-aware restoration) khi thiết bị có nhiều sự cố và khi IoT từ chối lệnh")
    void testCauseAwareRestorationWithMultipleIncidentsAndIotRejection() throws Exception {
        // Create 2 incidents on V01 (initial version = 3)
        String inc1Body = """
                {
                  "resourceType": "VEHICLE",
                  "resourceId": "V01",
                  "severity": "MEDIUM",
                  "description": "Phanh trước cần chỉnh"
                }
                """;
        String inc2Body = """
                {
                  "resourceType": "VEHICLE",
                  "resourceId": "V01",
                  "severity": "HIGH",
                  "description": "Khóa cổ xe bị kẹt"
                }
                """;

        MvcResult r1 = mockMvc.perform(post("/api/operator/incidents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inc1Body))
                .andExpect(status().isCreated())
                .andReturn();
        String incId1 = (String) jsonMapper.readValue(r1.getResponse().getContentAsString(), Map.class).get("incidentId");

        MvcResult r2 = mockMvc.perform(post("/api/operator/incidents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inc2Body))
                .andExpect(status().isCreated())
                .andReturn();
        String incId2 = (String) jsonMapper.readValue(r2.getResponse().getContentAsString(), Map.class).get("incidentId");

        // Send incId1 to maintenance -> V01 becomes MAINTENANCE (version 4)
        mockMvc.perform(post("/api/operator/incidents/" + incId1 + "/maintenance")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion": 0, "expectedResourceVersion": 3, "reason": "Bảo trì phanh"}
                                """))
                .andExpect(status().isAccepted());

        // Send incId2 to maintenance -> V01 version becomes 5, has both INCIDENT:incId1 and INCIDENT:incId2 causes
        mockMvc.perform(post("/api/operator/incidents/" + incId2 + "/maintenance")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion": 0, "expectedResourceVersion": 4, "reason": "Bảo trì khóa cổ"}
                                """))
                .andExpect(status().isAccepted());

        // Resolve incId1 -> incId1 becomes RESOLVED, but V01 MUST stay MAINTENANCE because incId2 is still active!
        mockMvc.perform(post("/api/operator/incidents/" + incId1 + "/resolve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion": 1, "expectedResourceVersion": 5, "resolutionNote": "Đã sửa xong phanh"}
                                """))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/operator/incidents/" + incId1)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        mockMvc.perform(get("/api/operator/vehicles/V01")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MAINTENANCE"))
                .andExpect(jsonPath("$.version").value(6));

        // Now resolve incId2 (the last remaining cause) -> V01 recovers to AVAILABLE (version 7)
        mockMvc.perform(post("/api/operator/incidents/" + incId2 + "/resolve")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion": 1, "expectedResourceVersion": 6, "resolutionNote": "Đã sửa xong khóa cổ"}
                                """))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/operator/vehicles/V01")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.version").value(7));

        // Test IoT ACK_REJECTED during maintenance keeps incident OPEN and device AVAILABLE
        deviceGateway.setMode(SimulationMode.AUTO_ACK_REJECTED);
        MvcResult r3 = mockMvc.perform(post("/api/operator/incidents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inc1Body))
                .andExpect(status().isCreated())
                .andReturn();
        String incId3 = (String) jsonMapper.readValue(r3.getResponse().getContentAsString(), Map.class).get("incidentId");

        MvcResult maintFailRes = mockMvc.perform(post("/api/operator/incidents/" + incId3 + "/maintenance")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"expectedVersion": 0, "expectedResourceVersion": 7, "reason": "Thử khóa khi IoT lỗi"}
                                """))
                .andExpect(status().isAccepted())
                .andReturn();
        String failOpId = (String) jsonMapper.readValue(maintFailRes.getResponse().getContentAsString(), Map.class)
                .get("operationId");

        mockMvc.perform(get("/api/operations/" + failOpId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.error.code").value("IOT_REJECTED"));

        mockMvc.perform(get("/api/operator/incidents/" + incId3)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.version").value(0));
    }
}
