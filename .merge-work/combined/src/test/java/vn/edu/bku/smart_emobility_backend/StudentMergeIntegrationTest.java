package vn.edu.bku.smart_emobility_backend;

import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import vn.edu.bku.smart_emobility_backend.common.domain.*;
import vn.edu.bku.smart_emobility_backend.common.security.SessionTokenService;
import vn.edu.bku.smart_emobility_backend.common.store.InMemoryDataStore;
import vn.edu.bku.smart_emobility_backend.iot.*;
import vn.edu.bku.smart_emobility_backend.service.*;

class StudentMergeIntegrationTest {
    @Test
    void studentAccessAcceptsHashedAuthSessionAndRejectsExpiredSession() {
        var store = new InMemoryDataStore();
        var now = Instant.parse("2026-11-25T00:00:00Z");
        var token = new SessionTokenService().generateToken();
        var hash = new SessionTokenService().hashToken(token);
        store.put(new Account("a", "Student", "s", "s@example.com", "hash",
                AccountRole.STUDENT, AccountStatus.ACTIVE, now, now));
        store.put(new Student("s", "a", "Student", AccountStatus.ACTIVE, now));
        store.put(new Session(hash, "a", now, now.plusSeconds(60)));
        assertEquals("s", new StudentAccess(store, Clock.fixed(now, ZoneOffset.UTC)).fromToken(token).id());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> new StudentAccess(store, Clock.fixed(now.plusSeconds(60), ZoneOffset.UTC)).fromToken(token));
    }

    @Test
    void pickupUsesClaimOwnerAndPollingNeverResendsCommand() {
        var gateway = mock(IoTGateway.class);
        var store = new InMemoryDataStore();
        var now = Instant.now();
        var claim = new StudentOperation("cmd", "s", "v", "space", "ride", null,
                StudentOperation.Kind.PICKUP, "fingerprint", false, now,
                StudentOperation.Status.PENDING, null, null, null);
        store.put(new Student("s", "a", "Student", AccountStatus.ACTIVE, now));
        store.put(claim);
        var command = new DeviceCommand("cmd", DeviceCommand.Type.UNLOCK, "v", null);
        when(gateway.unlock("v", "cmd", "a")).thenReturn(
                new DeviceResponse(command, CommandStatus.PENDING, now, null, null));
        when(gateway.getResult("cmd")).thenReturn(Optional.of(
                new DeviceResponse(command, CommandStatus.ACKNOWLEDGED, now, 50.0, null)));
        var adapter = new StudentIoTAdapter(gateway, store);
        assertEquals(StudentDevicePort.Outcome.UNKNOWN,
                adapter.send("cmd", StudentOperation.Kind.PICKUP, "v", "space").outcome());
        var reply = adapter.getResult("cmd");
        assertEquals(StudentDevicePort.Outcome.ACK_SUCCESS, reply.outcome());
        assertEquals("space", reply.spaceId());
        verify(gateway, times(1)).unlock("v", "cmd", "a");
        verify(gateway).getResult("cmd");
        verifyNoMoreInteractions(gateway);
    }
}
