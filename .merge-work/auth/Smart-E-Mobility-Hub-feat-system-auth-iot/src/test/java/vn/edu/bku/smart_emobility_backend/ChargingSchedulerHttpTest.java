package vn.edu.bku.smart_emobility_backend;

import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
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
        "app.data-file=./target/test-data/uc13-http-${random.uuid}.json"
})
class ChargingSchedulerHttpTest {
    @LocalServerPort private int port;
    @Autowired private InMemoryDataStore data;
    @Autowired private StateCoordinator coordinator;
    @Autowired private SessionTokenService tokens;
    @Autowired private JsonMapper mapper;
    @MockitoBean private IoTGateway telemetry;
    private final HttpClient client = HttpClient.newHttpClient();

    private record Identity(String token, String hubId) {}

    private Identity seed(AccountRole role) {
        String id = UUID.randomUUID().toString();
        String raw = tokens.generateToken();
        Instant now = Instant.now();
        assertTrue(coordinator.commit(coordinator.currentVersion(), store -> {
            store.put(new Account(id, "User", id, id + "@example.com", "hash", role, AccountStatus.ACTIVE, now, now));
            store.put(new Session(tokens.hashToken(raw), id, now, now.plusSeconds(3600)));
            store.put(new MobilityHub(id, "Hub", 10, 106, 20, 4, HubStatus.ACTIVE));
            store.put(new ChargingPort(id, id, "Port", 2, ChargingPortStatus.AVAILABLE));
            store.put(new Vehicle(id, id, 10, VehicleStatus.LOW_BATTERY, 10.0, 2.0));
            store.put(new ChargingRequest(id, id, id, id, 80, now, ChargingRequestStatus.PENDING,
                    now, now.plusSeconds(86400), now.plusSeconds(86400)));
        }));
        when(telemetry.getVehicleTelemetry(id)).thenReturn(new VehicleTelemetry(id, 10.0, VehicleStatus.LOW_BATTERY,
                10.0, 106.0, VehicleReading.LockStatus.LOCKED, now, DataQuality.FRESH));
        return new Identity(raw, id);
    }

    private HttpResponse<String> call(String path, String method, Object body, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port
                + "/api/operator/charging-schedules" + path)).header("Content-Type", "application/json");
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void operatorBuildsEditsConfirmsAndReadsPersistedSchedule() throws Exception {
        var identity = seed(AccountRole.OPERATOR);
        var response = call("", "POST", Map.of("hubId", identity.hubId()), identity.token());
        assertEquals(200, response.statusCode());
        assertTrue(mapper.readTree(response.body()).path("valid").asBoolean());
        var proposal = mapper.readValue(response.body(), ScheduleProposal.class);
        var slot = proposal.slots().getFirst();
        var edited = call("/" + proposal.id() + "/slots/" + slot.id(), "PUT",
                Map.of("revision", 0, "startAt", slot.startAt().plusSeconds(60),
                        "endAt", slot.endAt().plusSeconds(60)), identity.token());
        assertEquals(200, edited.statusCode());
        assertEquals(1, mapper.readTree(edited.body()).path("revision").asLong());
        var confirmed = call("/" + proposal.id() + "/confirm", "POST", Map.of("revision", 1), identity.token());
        assertEquals(200, confirmed.statusCode());
        var schedule = mapper.readValue(confirmed.body(), ChargingSchedule.class);
        var retry = call("/" + proposal.id() + "/confirm", "POST", Map.of("revision", 1), identity.token());
        assertEquals(schedule, mapper.readValue(retry.body(), ChargingSchedule.class));
        assertEquals(1, schedule.allocationIds().size());
        assertEquals(200, call("/" + proposal.id(), "GET", null, identity.token()).statusCode());
    }

    @Test
    void studentAndAnonymousCallerCannotAccessSchedulingRoutes() throws Exception {
        var student = seed(AccountRole.STUDENT);
        assertEquals(403, call("", "POST", Map.of("hubId", student.hubId()), student.token()).statusCode());
        assertEquals(401, call("", "POST", Map.of("hubId", student.hubId()), null).statusCode());
    }

    @Test
    void malformedInputAndMissingRevisionUseErrorEnvelope() throws Exception {
        var op = seed(AccountRole.OPERATOR);
        var empty = call("", "POST", Map.of("hubId", ""), op.token());
        assertEquals(400, empty.statusCode());
        assertEquals("VALIDATION_ERROR", mapper.readTree(empty.body()).path("error").path("code").asString());
        var response = call("", "POST", Map.of("hubId", op.hubId()), op.token());
        var proposal = mapper.readValue(response.body(), ScheduleProposal.class);
        assertEquals(400, call("/" + proposal.id() + "/confirm", "POST", Map.of(), op.token()).statusCode());
        assertEquals(409, call("/" + proposal.id() + "/confirm", "POST", Map.of("revision", 5), op.token()).statusCode());
    }
}
