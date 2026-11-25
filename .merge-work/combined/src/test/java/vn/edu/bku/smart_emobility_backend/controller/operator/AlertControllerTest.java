package vn.edu.bku.smart_emobility_backend.controller.operator;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.ArrayList;
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
import vn.edu.bku.smart_emobility_backend.common.store.StoreSnapshot;
import vn.edu.bku.smart_emobility_backend.dto.operator.ConnectionStatus;
import vn.edu.bku.smart_emobility_backend.service.operator.AlertNotifier;
import vn.edu.bku.smart_emobility_backend.service.operator.AlertRepository;

@SpringBootTest(properties = "app.data-file=${java.io.tmpdir}/smart-emobility-test-data.json")
@AutoConfigureMockMvc
class AlertControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InMemoryDataStore dataStore;

    @Autowired
    private OperatorStateRegistry stateRegistry;

    @Autowired
    private LatestDataStore latestDataStore;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private AlertNotifier alertNotifier;

    private static final String OP_TOKEN = "op-token-10";

    @BeforeEach
    void setUp() {
        stateRegistry.clear();
        latestDataStore.clear();
        alertRepository.clear();
        alertNotifier.clear();
    }

    private void seedHub1WithSpaces(int totalSpaces, int occupiedSpaces, Instant telemetryAt, double chargingCapKw, List<ChargingPort> ports) {
        Instant now = Instant.now();
        Account opAccount = new Account("acc-op-1", "operator@bku.edu.vn", "hash", AccountRole.OPERATOR, true, now);
        Session opSession = new Session(OP_TOKEN, "acc-op-1", now, now.plusSeconds(1800));

        MobilityHub h1 = new MobilityHub(
                "H1", "Hub 1", "Campus A", new GeoPoint(10.77, 106.66),
                5.0, totalSpaces, chargingCapKw, 0, 0);

        List<ParkingSpace> spaces = new ArrayList<>();
        List<Vehicle> vehicles = new ArrayList<>();
        for (int i = 1; i <= totalSpaces; i++) {
            String spaceId = String.format("S%02d", i);
            boolean isOccupied = i <= occupiedSpaces;
            spaces.add(new ParkingSpace(spaceId, "H1",
                    isOccupied ? ParkingSpaceStatus.OCCUPIED : ParkingSpaceStatus.AVAILABLE));
            if (isOccupied) {
                String vehicleId = String.format("V%02d", i);
                vehicles.add(new Vehicle(vehicleId, "H1", spaceId, VehicleType.E_BIKE, 80.0, 0.5, 0.25,
                        VehicleStatus.AVAILABLE, telemetryAt, 1L, VehicleOwnership.SHARED, null));
            }
        }

        StoreSnapshot snapshot = new StoreSnapshot(
                20L,
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
    @DisplayName("TC-10.1: H1 có 17/20 chỗ đã dùng -> Cảnh báo lấp đầy tại ngưỡng 85% (WARNING), bảng cảnh báo và chi tiết Hub thống nhất")
    void testTc10_1_Hub17Of20SpacesShows85PercentWarningAndMatchesHubDetail() throws Exception {
        Instant now = Instant.now();
        List<ChargingPort> ports = List.of(
                new ChargingPort("C1", "H1", "Port 1", 0.25, ChargingPortStatus.AVAILABLE),
                new ChargingPort("C2", "H1", "Port 2", 0.25, ChargingPortStatus.AVAILABLE)
        );
        seedHub1WithSpaces(20, 17, now.minusSeconds(5), 1.0, ports);

        // 1. Check GET /api/operator/alerts?dimension=PARKING
        mockMvc.perform(get("/api/operator/alerts")
                        .param("dimension", "PARKING")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stateVersion").value(20))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].alertId").value("H1:PARKING"))
                .andExpect(jsonPath("$.items[0].hubId").value("H1"))
                .andExpect(jsonPath("$.items[0].dimension").value("PARKING"))
                .andExpect(jsonPath("$.items[0].severity").value("WARNING"))
                .andExpect(jsonPath("$.items[0].value").value(85.0))
                .andExpect(jsonPath("$.items[0].threshold").value(80.0))
                .andExpect(jsonPath("$.items[0].unit").value("PERCENT"))
                .andExpect(jsonPath("$.items[0].reasonCode").value("HIGH_UTILIZATION"))
                .andExpect(jsonPath("$.items[0].dataQuality").value("FRESH"));

        // 2. Verify charging dimensions are NORMAL (0%) and omitted by default when severity filter is not passed
        mockMvc.perform(get("/api/operator/alerts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].alertId").value("H1:PARKING"));

        // 3. Verify GET /api/operator/hubs/H1/report matches 17/20 = 85.0% and WARNING
        mockMvc.perform(get("/api/operator/hubs/H1/report")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hub.hubId").value("H1"))
                .andExpect(jsonPath("$.hub.capacity").value(20))
                .andExpect(jsonPath("$.hub.occupiedCount").value(17))
                .andExpect(jsonPath("$.hub.freeSpaceCount").value(3))
                .andExpect(jsonPath("$.hub.occupancyPct").value(85.0))
                .andExpect(jsonPath("$.hub.parkingSeverity").value("WARNING"));
    }

    @Test
    @DisplayName("TC-10.2: H1 mất dữ liệu sức chứa (telemetry missing/stale hoặc capacity=0) -> Hiển thị MISSING_DATA, không suy ra Hub an toàn")
    void testTc10_2_MissingCapacityOrTelemetryShowsMissingDataNotNormal() throws Exception {
        // Case A: Telemetry missing (null) on vehicles and no ports
        seedHub1WithSpaces(20, 5, null, 1.0, List.of());

        mockMvc.perform(get("/api/operator/alerts")
                        .param("hubId", "H1")
                        .param("dimension", "PARKING")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].alertId").value("H1:PARKING"))
                .andExpect(jsonPath("$.items[0].severity").value("MISSING_DATA"))
                .andExpect(jsonPath("$.items[0].reasonCode").value("MISSING_DATA"))
                .andExpect(jsonPath("$.items[0].threshold").isEmpty())
                .andExpect(jsonPath("$.items[0].dataQuality").value("MISSING"));

        mockMvc.perform(get("/api/operator/hubs/H1/report")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hub.parkingSeverity").value("MISSING_DATA"))
                .andExpect(jsonPath("$.hub.severity").value("MISSING_DATA"))
                .andExpect(jsonPath("$.hub.dataQuality").value("MISSING"));

        // Case B: Capacity = 0 (INVALID_CAPACITY)
        seedHub1WithSpaces(0, 0, Instant.now(), 0.0, List.of());

        mockMvc.perform(get("/api/operator/alerts")
                        .param("hubId", "H1")
                        .param("dimension", "PARKING")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].severity").value("MISSING_DATA"))
                .andExpect(jsonPath("$.items[0].reasonCode").value("INVALID_CAPACITY"))
                .andExpect(jsonPath("$.items[0].value").isEmpty());
    }

    @Test
    @DisplayName("Kiểm tra cảnh báo CRITICAL (>=95%), công suất sạc >100%, thứ tự sắp xếp, và AlertNotifier")
    void testCriticalOverloadAndPowerOver100AndSorting() throws Exception {
        Instant now = Instant.now();
        List<ChargingPort> ports = List.of(
                new ChargingPort("C1", "H1", "Port 1", 0.5, ChargingPortStatus.CHARGING),
                new ChargingPort("C2", "H1", "Port 2", 0.5, ChargingPortStatus.CHARGING)
        );
        // 19/20 parking = 95% (CRITICAL), charging limit = 0.8 kW (800W) while 2 ports measure 600W each = 1200W (150% > 100%)
        seedHub1WithSpaces(20, 19, now.minusSeconds(3), 0.8, ports);
        stateRegistry.putPortMeta("C1", new PortMeta(600.0, true, now.minusSeconds(3), ConnectionStatus.ONLINE, 1L, null, Set.of()));
        stateRegistry.putPortMeta("C2", new PortMeta(600.0, true, now.minusSeconds(3), ConnectionStatus.ONLINE, 1L, null, Set.of()));

        mockMvc.perform(get("/api/operator/alerts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                // All 3 dimensions for H1 are CRITICAL (95% PARKING, 100% CHARGING_PORTS, 150% CHARGING_POWER)
                .andExpect(jsonPath("$.items[0].alertId").value("H1:PARKING"))
                .andExpect(jsonPath("$.items[0].severity").value("CRITICAL"))
                .andExpect(jsonPath("$.items[0].value").value(95.0))
                .andExpect(jsonPath("$.items[0].threshold").value(95.0))
                .andExpect(jsonPath("$.items[1].alertId").value("H1:CHARGING_PORTS"))
                .andExpect(jsonPath("$.items[1].severity").value("CRITICAL"))
                .andExpect(jsonPath("$.items[1].value").value(100.0))
                .andExpect(jsonPath("$.items[2].alertId").value("H1:CHARGING_POWER"))
                .andExpect(jsonPath("$.items[2].severity").value("CRITICAL"))
                .andExpect(jsonPath("$.items[2].value").value(150.0))
                .andExpect(jsonPath("$.items[2].threshold").value(95.0));

        // AlertNotifier should have recorded the 3 CRITICAL alerts exactly once
        org.junit.jupiter.api.Assertions.assertEquals(3, alertNotifier.publishedCriticalAlerts().size());

        // Polling again while still CRITICAL does not spam duplicate notifications
        mockMvc.perform(get("/api/operator/alerts")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + OP_TOKEN))
                .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertEquals(3, alertNotifier.publishedCriticalAlerts().size());
    }
}
