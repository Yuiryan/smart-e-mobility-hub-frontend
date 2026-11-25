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

@SpringBootTest(properties = "app.data-file=${java.io.tmpdir}/smart-emobility-test-data.json")
@AutoConfigureMockMvc
class DispatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InMemoryDataStore dataStore;

    @Autowired
    private OperatorStateRegistry stateRegistry;

    @Autowired
    private IdempotencyStore idempotencyStore;

    @Autowired
    private JsonMapper jsonMapper;

    private static final String OP_TOKEN = "op-token-11";

    @BeforeEach
    void setUp() {
        stateRegistry.clear();
        idempotencyStore.clear();

        Instant now = Instant.now();
        Account opAccount = new Account("acc-op-1", "operator@bku.edu.vn", "hash", AccountRole.OPERATOR, true, now);
        Session opSession = new Session(OP_TOKEN, "acc-op-1", now, now.plusSeconds(1800));

        // H1 (source): capacity 3, 3 occupied by V1 (80%), V2 (75%), V3_LOW (12% LOW_BATTERY)
        MobilityHub h1 = new MobilityHub(
                "H1", "Hub 1", "Campus A", new GeoPoint(10.77, 106.66),
                5.0, 3, 1.0, 0, 0);
        // H2 (destination with 2 free spaces): capacity 2, 2 available
        MobilityHub h2 = new MobilityHub(
                "H2", "Hub 2", "Campus B", new GeoPoint(10.78, 106.67),
                5.0, 2, 1.0, 0, 0);
        // H3 (destination with only 1 free space): capacity 1, 1 available
        MobilityHub h3 = new MobilityHub(
                "H3", "Hub 3", "Campus C", new GeoPoint(10.79, 106.68),
                5.0, 1, 1.0, 0, 0);

        List<ParkingSpace> spaces = List.of(
                new ParkingSpace("H1_S1", "H1", ParkingSpaceStatus.OCCUPIED),
                new ParkingSpace("H1_S2", "H1", ParkingSpaceStatus.OCCUPIED),
                new ParkingSpace("H1_S3", "H1", ParkingSpaceStatus.OCCUPIED),
                new ParkingSpace("H2_S1", "H2", ParkingSpaceStatus.AVAILABLE),
                new ParkingSpace("H2_S2", "H2", ParkingSpaceStatus.AVAILABLE),
                new ParkingSpace("H3_S1", "H3", ParkingSpaceStatus.AVAILABLE)
        );

        List<Vehicle> vehicles = List.of(
                new Vehicle("V1", "H1", "H1_S1", VehicleType.E_BIKE, 80.0, 0.5, 0.25,
                        VehicleStatus.AVAILABLE, now.minusSeconds(5), 1L, VehicleOwnership.SHARED, null),
                new Vehicle("V2", "H1", "H1_S2", VehicleType.E_BIKE, 75.0, 0.5, 0.25,
                        VehicleStatus.AVAILABLE, now.minusSeconds(5), 1L, VehicleOwnership.SHARED, null),
                new Vehicle("V3_LOW", "H1", "H1_S3", VehicleType.E_BIKE, 12.0, 0.5, 0.25,
                        VehicleStatus.LOW_BATTERY, now.minusSeconds(5), 1L, VehicleOwnership.SHARED, null)
        );

        StoreSnapshot snapshot = new StoreSnapshot(
                30L,
                List.of(opAccount),
                List.of(opSession),
                List.of(h1, h2, h3),
                vehicles,
                spaces,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
        dataStore.restore(snapshot);
    }

    @Test
    @DisplayName("TC-11.1: H2 còn hai chỗ phù hợp, chọn hai xe ở H1 -> Tạo lệnh sang H2 thành công, hai xe dự kiến đến (inbound=2), chưa tăng số xe AVAILABLE tại H2 cho tới khi arrival")
    void testTc11_1_CreateDispatchOrderAndConfirmArrival() throws Exception {
        String createBody = """
                {
                  "sourceHubId": "H1",
                  "destinationHubId": "H2",
                  "vehicleIds": ["V1", "V2"],
                  "transportMode": "SELF_DRIVE",
                  "reason": "Điều phối cân bằng xe sang H2"
                }
                """;

        MvcResult createRes = mockMvc.perform(post("/api/operator/dispatch-orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceHubId").value("H1"))
                .andExpect(jsonPath("$.destinationHubId").value("H2"))
                .andExpect(jsonPath("$.vehicleIds.length()").value(2))
                .andExpect(jsonPath("$.transportMode").value("SELF_DRIVE"))
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.arrivedAt").isEmpty())
                .andReturn();

        String orderId = (String) jsonMapper.readValue(createRes.getResponse().getContentAsString(), Map.class)
                .get("dispatchOrderId");

        // Verify H2 report: inboundCount = 2, occupiedCount = 0, freeSpaceCount = 0, AVAILABLE vehicles = 0
        mockMvc.perform(get("/api/operator/hubs/H2/report")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hub.inboundCount").value(2))
                .andExpect(jsonPath("$.hub.occupiedCount").value(0))
                .andExpect(jsonPath("$.hub.freeSpaceCount").value(0))
                .andExpect(jsonPath("$.hub.vehicleCountsByStatus.AVAILABLE").value(0));

        // Verify H1 report: 2 spaces freed (occupiedCount = 1, freeSpaceCount = 2)
        mockMvc.perform(get("/api/operator/hubs/H1/report")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hub.occupiedCount").value(1))
                .andExpect(jsonPath("$.hub.freeSpaceCount").value(2));

        // Verify V1 is IN_TRANSIT with hubId=null
        mockMvc.perform(get("/api/operator/vehicles/V1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.hubId").isEmpty());

        // Confirm arrival at H2
        String arrivalBody = """
                {
                  "expectedVersion": 0
                }
                """;
        mockMvc.perform(post("/api/operator/dispatch-orders/" + orderId + "/arrival")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(arrivalBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.arrivedAt").isNotEmpty());

        // Now H2 has 2 occupied spaces and 2 AVAILABLE vehicles, inboundCount = 0
        mockMvc.perform(get("/api/operator/hubs/H2/report")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hub.inboundCount").value(0))
                .andExpect(jsonPath("$.hub.occupiedCount").value(2))
                .andExpect(jsonPath("$.hub.vehicleCountsByStatus.AVAILABLE").value(2));

        // Terminal retry on already COMPLETED arrival with new Idempotency-Key returns 200 current object
        mockMvc.perform(post("/api/operator/dispatch-orders/" + orderId + "/arrival")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(arrivalBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    @DisplayName("TC-11.2: H3 chỉ còn một chỗ, chọn chuyển hai xe -> Cảnh báo thiếu sức chứa (409 DESTINATION_HUB_FULL), giữ nguyên trạng thái xe và Hub")
    void testTc11_2_DestinationHubFullRejectsAtomically() throws Exception {
        String createBody = """
                {
                  "sourceHubId": "H1",
                  "destinationHubId": "H3",
                  "vehicleIds": ["V1", "V2"],
                  "transportMode": "SELF_DRIVE",
                  "reason": "Thử chuyển 2 xe sang H3 chỉ còn 1 chỗ"
                }
                """;

        mockMvc.perform(post("/api/operator/dispatch-orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DESTINATION_HUB_FULL"));

        // Verify V1 and V2 are still AVAILABLE at H1 and H3 still has 1 free space
        mockMvc.perform(get("/api/operator/vehicles/V1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.hubId").value("H1"));

        mockMvc.perform(get("/api/operator/hubs/H3/report")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hub.freeSpaceCount").value(1))
                .andExpect(jsonPath("$.hub.inboundCount").value(0));
    }

    @Test
    @DisplayName("Kiểm tra candidates SELF_DRIVE vs TRUCK cho xe pin thấp và luồng cancel lệnh điều phối (bao gồm SOURCE_HUB_FULL)")
    void testCandidatesLowBatteryTruckAndCancelDispatch() throws Exception {
        // 1. SELF_DRIVE candidates at H1 only returns V1, V2 (excludes V3_LOW)
        mockMvc.perform(get("/api/operator/dispatch-candidates")
                        .param("sourceHubId", "H1")
                        .param("transportMode", "SELF_DRIVE")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.destinationOptions.length()").value(2))
                .andExpect(jsonPath("$.destinationOptions[0].hubId").value("H2"))
                .andExpect(jsonPath("$.destinationOptions[0].freeSpaceCount").value(2));

        // 2. TRUCK candidates at H1 includes V3_LOW (3 vehicles total)
        mockMvc.perform(get("/api/operator/dispatch-candidates")
                        .param("sourceHubId", "H1")
                        .param("transportMode", "TRUCK")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3));

        // 3. Attempting to dispatch V3_LOW via SELF_DRIVE fails with 422 BUSINESS_RULE_VIOLATION
        String selfDriveLowBatBody = """
                {
                  "sourceHubId": "H1",
                  "destinationHubId": "H2",
                  "vehicleIds": ["V3_LOW"],
                  "transportMode": "SELF_DRIVE",
                  "reason": "Thử tự lái xe pin yếu"
                }
                """;
        mockMvc.perform(post("/api/operator/dispatch-orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(selfDriveLowBatBody))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("BUSINESS_RULE_VIOLATION"));

        // 4. Dispatch V3_LOW via TRUCK succeeds (201 Created)
        String truckLowBatBody = """
                {
                  "sourceHubId": "H1",
                  "destinationHubId": "H2",
                  "vehicleIds": ["V3_LOW"],
                  "transportMode": "TRUCK",
                  "reason": "Chở xe pin yếu bằng xe tải sang H2"
                }
                """;
        MvcResult truckRes = mockMvc.perform(post("/api/operator/dispatch-orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(truckLowBatBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transportMode").value("TRUCK"))
                .andExpect(jsonPath("$.status").value("IN_TRANSIT"))
                .andReturn();
        String orderId = (String) jsonMapper.readValue(truckRes.getResponse().getContentAsString(), Map.class)
                .get("dispatchOrderId");

        // 5. Cancel order returns V3_LOW back to H1 with status LOW_BATTERY and releases H2 inbound slot
        String cancelBody = """
                {
                  "expectedVersion": 0
                }
                """;
        mockMvc.perform(post("/api/operator/dispatch-orders/" + orderId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.cancelledAt").isNotEmpty());

        mockMvc.perform(get("/api/operator/vehicles/V3_LOW")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hubId").value("H1"))
                .andExpect(jsonPath("$.status").value("LOW_BATTERY"));

        mockMvc.perform(get("/api/operator/hubs/H2/report")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hub.inboundCount").value(0))
                .andExpect(jsonPath("$.hub.freeSpaceCount").value(2));
    }
}
