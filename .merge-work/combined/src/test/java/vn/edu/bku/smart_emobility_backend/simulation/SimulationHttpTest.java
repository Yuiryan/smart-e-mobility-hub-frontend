package vn.edu.bku.smart_emobility_backend.simulation;

import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.bku.smart_emobility_backend.common.domain.*;
import vn.edu.bku.smart_emobility_backend.common.security.SessionTokenService;
import vn.edu.bku.smart_emobility_backend.common.store.*;
import vn.edu.bku.smart_emobility_backend.iot.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.data-file=./target/test-data/uc14-http-${random.uuid}.json"
})
class SimulationHttpTest {
    @LocalServerPort private int port;
    @Autowired private InMemoryDataStore data;
    @Autowired private StateCoordinator coordinator;
    @Autowired private SessionTokenService tokens;
    @Autowired private JsonMapper mapper;
    @MockitoBean private IoTGateway telemetry;
    private final HttpClient client = HttpClient.newHttpClient();

    @BeforeEach
    void missingTelemetryForVehiclesFromEarlierTests() {
        when(telemetry.getVehicleTelemetry(anyString())).thenAnswer(invocation ->
                new VehicleTelemetry(invocation.getArgument(0), null, null, null, null, null, null, DataQuality.MISSING));
    }

    private String seed(AccountRole role) {
        String id = UUID.randomUUID().toString();
        String raw = tokens.generateToken();
        Instant now = Instant.now();
        assertTrue(coordinator.commit(coordinator.currentVersion(), store -> {
            store.put(new Account(id, "User", id, id + "@example.com", "hash", role, AccountStatus.ACTIVE, now, now));
            store.put(new Session(tokens.hashToken(raw), id, now, now.plusSeconds(3600)));
            store.put(new MobilityHub(id, "Hub", 10, 106, 1, 4, HubStatus.ACTIVE));
            store.put(new ParkingSpace(id, id, ParkingSpaceStatus.OCCUPIED));
            store.put(new Vehicle(id, id, 100, VehicleStatus.AVAILABLE));
        }));
        when(telemetry.getVehicleTelemetry(id)).thenReturn(new VehicleTelemetry(id, 100.0, VehicleStatus.AVAILABLE,
                10.0, 106.0, VehicleReading.LockStatus.LOCKED, now, DataQuality.FRESH));
        return raw;
    }

    private HttpResponse<String> call(Object body, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/operator/simulations"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private Scenario scenario(double hours, double multiplier, String outage) {
        return new Scenario("HTTP Test", new Scenario.Parameters(hours, multiplier, outage));
    }

    @Test
    void operatorReceivesContractResponseWithoutCreatingDispatchOrDeviceOperations() throws Exception {
        String token = seed(AccountRole.OPERATOR);
        int dispatchBefore = data.findAll(DispatchOrder.class).size();
        int operationBefore = data.findAll(DeviceOperation.class).size();
        var response = call(scenario(1, 0, null), token);
        assertEquals(200, response.statusCode());
        var result = mapper.readValue(response.body(), SimulationResult.class);
        assertEquals("COMPLETED", result.status());
        assertNotNull(result.simulationId());
        assertTrue(result.missingData().contains("MISSING_DEMAND_BASELINE"));
        assertEquals(dispatchBefore, data.findAll(DispatchOrder.class).size());
        assertEquals(operationBefore, data.findAll(DeviceOperation.class).size());
        assertTrue(mockingDetails(telemetry).getInvocations().stream()
                .allMatch(invocation -> invocation.getMethod().getName().equals("getVehicleTelemetry")));
    }

    @Test
    void studentAndAnonymousCallerCannotRunSimulation() throws Exception {
        String token = seed(AccountRole.STUDENT);
        assertEquals(403, call(scenario(1, 1, null), token).statusCode());
        assertEquals(401, call(scenario(1, 1, null), null).statusCode());
    }

    @Test
    void invalidParametersAndMissingParametersReturnValidationEnvelope() throws Exception {
        String token = seed(AccountRole.OPERATOR);
        var invalid = call(scenario(0, 1, null), token);
        assertEquals(400, invalid.statusCode());
        assertEquals("VALIDATION_ERROR", mapper.readTree(invalid.body()).path("error").path("code").asString());
        assertEquals(400, call(Map.of("scenarioName", "Missing parameters"), token).statusCode());
        assertEquals(404, call(scenario(1, 1, "nonexistent"), token).statusCode());
    }
}
