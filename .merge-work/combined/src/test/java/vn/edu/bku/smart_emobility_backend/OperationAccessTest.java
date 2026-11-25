package vn.edu.bku.smart_emobility_backend;

import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.json.JsonMapper;
import vn.edu.bku.smart_emobility_backend.common.domain.*;
import vn.edu.bku.smart_emobility_backend.common.exception.AuthorizationException;
import vn.edu.bku.smart_emobility_backend.common.security.SessionTokenService;
import vn.edu.bku.smart_emobility_backend.common.store.*;
import vn.edu.bku.smart_emobility_backend.dto.OperationResponseDTO;
import vn.edu.bku.smart_emobility_backend.iot.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.data-file=./target/test-data/operation-access-${random.uuid}.json",
        "app.iot.simulator.command-mode=NORMAL",
        "app.iot.simulator.response-delay=PT0S",
        "app.iot.simulator.command-timeout=PT5S"
})
class OperationAccessTest {
    @LocalServerPort private int port;
    @Autowired private InMemoryDataStore data;
    @Autowired private StateCoordinator coordinator;
    @Autowired private SessionTokenService tokens;
    @Autowired private JsonMapper mapper;
    @Autowired private JsonStore json;
    @Autowired private IoTGateway gateway;
    @Autowired private ChargingIoTGateway charging;
    @Autowired private SimulatorAdapter device;
    private final HttpClient client = HttpClient.newHttpClient();

    private record Identity(String id, String token) {}

    private Identity seed(AccountRole role) {
        String id = UUID.randomUUID().toString();
        String token = tokens.generateToken();
        Instant now = Instant.now();
        assertTrue(coordinator.commit(coordinator.currentVersion(), store -> {
            store.put(new Account(id, "User", id, id + "@example.com", "hash", role, AccountStatus.ACTIVE, now, now));
            store.put(new Session(tokens.hashToken(token), id, now, now.plusSeconds(3600)));
        }));
        device.emitTelemetry(new VehicleReading(id, 50.0, VehicleStatus.AVAILABLE, 10.0, 106.0,
                VehicleReading.LockStatus.LOCKED, Instant.now(), "space"));
        return new Identity(id, token);
    }

    private String unlock(Identity owner) {
        String id = UUID.randomUUID().toString();
        assertEquals(CommandStatus.ACKNOWLEDGED, gateway.unlock(owner.id(), id, owner.id()).status());
        return id;
    }

    private HttpResponse<String> get(String id, String token) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/operations/" + id)).GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void ownerReceivesOnlySafeDeviceResult() throws Exception {
        var owner = seed(AccountRole.STUDENT);
        String id = unlock(owner);
        var response = get(id, owner.token());
        assertEquals(200, response.statusCode());
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        var body = mapper.readValue(response.body(), OperationResponseDTO.class);
        assertEquals(id, body.operationId());
        assertEquals("UNLOCK_VEHICLE", body.type());
        assertEquals("SUCCEEDED", body.status());
        assertEquals(owner.id(), body.result().get("vehicleId"));
        assertNull(body.error());
        assertFalse(response.body().contains(owner.token()));
        assertFalse(response.body().contains(tokens.hashToken(owner.token())));
    }

    @Test
    void anotherStudentCannotReadOrChangeOwnershipViaQuery() throws Exception {
        var owner = seed(AccountRole.STUDENT);
        var other = seed(AccountRole.STUDENT);
        String id = unlock(owner);
        assertEquals(403, get(id, other.token()).statusCode());
        assertEquals(403, get(id + "?accountId=" + owner.id(), other.token()).statusCode());
        assertEquals(owner.id(), data.findById(DeviceOperation.class, id).orElseThrow().accountId());
    }

    @Test
    void operatorCanReadStudentAndLegacyOperations() throws Exception {
        var owner = seed(AccountRole.STUDENT);
        var op = seed(AccountRole.OPERATOR);
        assertEquals(200, get(unlock(owner), op.token()).statusCode());
        String legacy = UUID.randomUUID().toString();
        gateway.unlock(owner.id(), legacy);
        assertEquals(403, get(legacy, owner.token()).statusCode());
        assertEquals(200, get(legacy, op.token()).statusCode());
    }

    @Test
    void unknownAndAnonymousRequestsUseStandardErrors() throws Exception {
        var owner = seed(AccountRole.STUDENT);
        String id = unlock(owner);
        assertEquals(401, get(id, null).statusCode());
        var missing = get("unknown", owner.token());
        assertEquals(404, missing.statusCode());
        assertEquals("RESOURCE_NOT_FOUND", mapper.readTree(missing.body()).path("error").path("code").asString());
    }

    @Test
    void retryKeepsOwnerAndPayloadAndDoesNotCreateAnotherReceipt() {
        var owner = seed(AccountRole.STUDENT);
        var other = seed(AccountRole.STUDENT);
        String id = unlock(owner);
        long version = data.version();
        gateway.unlock(owner.id(), id, owner.id());
        assertEquals(version, data.version());
        assertThrows(AuthorizationException.class, () -> gateway.unlock(owner.id(), id, other.id()));
        assertThrows(AuthorizationException.class, () -> gateway.unlock(owner.id(), id));
        assertThrows(IllegalArgumentException.class, () -> gateway.unlock("another-vehicle", id, owner.id()));
        assertEquals(owner.id(), data.findById(DeviceOperation.class, id).orElseThrow().accountId());
    }

    @Test
    void forceLockRequiresOperatorAndRejectedCallDoesNotCreateIntent() {
        var student = seed(AccountRole.STUDENT);
        var op = seed(AccountRole.OPERATOR);
        String rejected = UUID.randomUUID().toString();
        assertThrows(AuthorizationException.class, () -> gateway.forceLock(student.id(), rejected, student.id()));
        assertTrue(data.findById(DeviceOperation.class, rejected).isEmpty());
        String accepted = UUID.randomUUID().toString();
        assertEquals(CommandStatus.ACKNOWLEDGED, gateway.forceLock(op.id(), accepted, op.id()).status());
    }

    @Test
    void ownershipAndUpdatedTimeSurviveJsonReload() {
        var owner = seed(AccountRole.STUDENT);
        String id = unlock(owner);
        var operation = json.load().orElseThrow().deviceOperations().stream()
                .filter(value -> value.id().equals(id)).findFirst().orElseThrow();
        assertEquals(owner.id(), operation.accountId());
        assertFalse(operation.updatedAt().isBefore(operation.createdAt()));
    }

    @Test
    void pollingMissingReceiptPreservesOwnerAndReturnsReconciliationError() throws Exception {
        var owner = seed(AccountRole.STUDENT);
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        var command = new DeviceCommand(id, DeviceCommand.Type.UNLOCK, owner.id(), null);
        assertTrue(coordinator.commit(coordinator.currentVersion(), store -> store.put(
                new DeviceOperation(now, new DeviceResponse(command, CommandStatus.PENDING, now, null, null), owner.id(), now))));
        var response = get(id, owner.token());
        assertEquals(200, response.statusCode());
        var body = mapper.readValue(response.body(), OperationResponseDTO.class);
        assertEquals("AWAITING_RECONCILIATION", body.status());
        assertEquals("IOT_RECONCILIATION_REQUIRED", body.error().code());
        assertNull(body.result());
        assertEquals(owner.id(), data.findById(DeviceOperation.class, id).orElseThrow().accountId());
    }

    @Test
    void failedDeviceCommandHasFailedResultAndNoSuccessPayload() throws Exception {
        var owner = seed(AccountRole.STUDENT);
        String id = UUID.randomUUID().toString();
        gateway.lockAtSpace(owner.id(), "wrong-space", id, owner.id());
        var body = mapper.readValue(get(id, owner.token()).body(), OperationResponseDTO.class);
        assertEquals("FAILED", body.status());
        assertEquals("IOT_COMMAND_FAILED", body.error().code());
        assertNull(body.result());
    }

    @Test
    void inactiveAccountCannotCreateOrPollOperations() throws Exception {
        var owner = seed(AccountRole.STUDENT);
        String id = unlock(owner);
        var account = data.findById(Account.class, owner.id()).orElseThrow();
        assertTrue(coordinator.commit(coordinator.currentVersion(), store -> store.put(new Account(
                account.id(), account.fullName(), account.studentId(), account.email(), account.passwordHash(),
                account.role(), AccountStatus.SUSPENDED, account.createdAt(), Instant.now()))));
        assertEquals(403, get(id, owner.token()).statusCode());
        assertThrows(AuthorizationException.class, () -> gateway.unlock(owner.id(), UUID.randomUUID().toString(), owner.id()));
    }

    @Test
    void chargingGatewayAlsoBindsOperationToCaller() throws Exception {
        var owner = seed(AccountRole.STUDENT);
        var other = seed(AccountRole.STUDENT);
        String id = UUID.randomUUID().toString();
        Instant start = Instant.now().plusSeconds(60);
        charging.reservePort("unknown-hub", "port", "reservation", start, start.plusSeconds(60), id, owner.id());
        assertEquals(owner.id(), data.findById(DeviceOperation.class, id).orElseThrow().accountId());
        assertEquals(403, get(id, other.token()).statusCode());
        assertEquals(200, get(id, owner.token()).statusCode());
    }

    @Test
    void concurrentAttemptsCannotAssignTheSameCommandToTwoOwners() throws Exception {
        var first = seed(AccountRole.STUDENT);
        var second = seed(AccountRole.STUDENT);
        String id = UUID.randomUUID().toString();
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var results = List.of(executor.submit(() -> attempt(first.id(), first.id(), id, ready, start)),
                    executor.submit(() -> attempt(first.id(), second.id(), id, ready, start)));
            boolean reached = ready.await(5, java.util.concurrent.TimeUnit.SECONDS);
            start.countDown();
            assertTrue(reached);
            int accepted = 0;
            for (var result : results) {
                if (result.get(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    accepted++;
                }
            }
            assertEquals(1, accepted);
        }
    }

    private boolean attempt(String vehicleId, String accountId, String id,
            java.util.concurrent.CountDownLatch ready, java.util.concurrent.CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        try {
            gateway.unlock(vehicleId, id, accountId);
            return true;
        } catch (AuthorizationException denied) {
            return false;
        }
    }
    @Test
    void deniedPollDoesNotQueryAndUpdatePendingOperation() throws Exception {
        var owner = seed(AccountRole.STUDENT);
        var other = seed(AccountRole.STUDENT);
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        var command = new DeviceCommand(id, DeviceCommand.Type.UNLOCK, owner.id(), null);
        var original = new DeviceOperation(now,
                new DeviceResponse(command, CommandStatus.PENDING, now, null, null), owner.id(), now);
        assertTrue(coordinator.commit(coordinator.currentVersion(), store -> store.put(original)));
        assertEquals(403, get(id, other.token()).statusCode());
        assertEquals(original, data.findById(DeviceOperation.class, id).orElseThrow());
    }

    @Test
    void anotherAccountCannotClaimOrReleaseAnExistingPortReservation() {
        var owner = seed(AccountRole.STUDENT);
        var other = seed(AccountRole.STUDENT);
        String reservation = UUID.randomUUID().toString();
        Instant start = Instant.now().plusSeconds(60);
        charging.reservePort("hub", "port", reservation, start, start.plusSeconds(60),
                UUID.randomUUID().toString(), owner.id());
        assertThrows(AuthorizationException.class, () -> charging.releasePort("hub", "port", reservation,
                UUID.randomUUID().toString(), other.id()));
        assertThrows(AuthorizationException.class, () -> charging.reservePort("hub", "port", reservation,
                start, start.plusSeconds(60), UUID.randomUUID().toString(), other.id()));
    }

}
