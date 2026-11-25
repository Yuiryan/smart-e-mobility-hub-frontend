package vn.edu.bku.smart_emobility_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.*;
import vn.edu.bku.smart_emobility_backend.common.domain.*;
import vn.edu.bku.smart_emobility_backend.common.store.StoreSnapshot;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.bku.smart_emobility_backend.common.domain.Account;
import vn.edu.bku.smart_emobility_backend.common.domain.AccountRole;
import vn.edu.bku.smart_emobility_backend.common.domain.Reservation;
import vn.edu.bku.smart_emobility_backend.common.domain.Session;
import vn.edu.bku.smart_emobility_backend.common.domain.Vehicle;
import vn.edu.bku.smart_emobility_backend.common.domain.VehicleOwnership;
import vn.edu.bku.smart_emobility_backend.common.domain.VehicleStatus;
import vn.edu.bku.smart_emobility_backend.common.domain.VehicleType;
import vn.edu.bku.smart_emobility_backend.common.store.InMemoryDataStore;
import vn.edu.bku.smart_emobility_backend.common.store.InMemoryReservationDataStore;
import vn.edu.bku.smart_emobility_backend.common.store.InMemoryVehicleDataStore;
import vn.edu.bku.smart_emobility_backend.common.store.JsonStore;
import vn.edu.bku.smart_emobility_backend.common.store.StateCoordinator;
import vn.edu.bku.smart_emobility_backend.dto.BookingRequestDTO;
import vn.edu.bku.smart_emobility_backend.dto.BookingResponseDTO;

class ReservationServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");
    private static final String TOKEN = "student-session";
    private InMemoryDataStore store;
    private ReservationService service;

    @BeforeEach
    void setUp() {
        store = new InMemoryDataStore();
        JsonStore jsonStore = mock(JsonStore.class);
        StateCoordinator coordinator = new StateCoordinator(store, jsonStore);
        store.put(new Account("student-1", "student@bku.edu.vn", "hash",
                AccountRole.STUDENT, true, NOW));
        store.put(new vn.edu.bku.smart_emobility_backend.common.domain.Student("s1", "student-1", "Student",
                vn.edu.bku.smart_emobility_backend.common.domain.AccountStatus.ACTIVE, NOW));
        store.put(new Session(TOKEN, "student-1", NOW.minusSeconds(30), NOW.plusSeconds(1800)));
        Vehicle vehicle = new Vehicle("bike-1", "hub-1", "space-1", VehicleType.E_BIKE,
                80.0, 0.5, 0.3, VehicleStatus.AVAILABLE, NOW.minusSeconds(60), 1L,
                VehicleOwnership.SHARED, null);
        store.put(vehicle);
        service = new ReservationService(store, coordinator, new InMemoryVehicleDataStore(store),
                new InMemoryReservationDataStore(store), Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofMinutes(15), Duration.ofMinutes(10), 20.0);
    }

    @Test
    void validRentalWindowIsReturnedAndPersistedWithIndependentHoldExpiry() {
        Instant startsAt = NOW.plus(Duration.ofHours(1));
        Instant endsAt = startsAt.plus(Duration.ofMinutes(90));

        BookingResponseDTO response = service.confirmReservation(TOKEN,
                new BookingRequestDTO("bike-1", startsAt, endsAt));

        assertThat(response.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        Reservation saved = store.findById(Reservation.class, response.reservationId()).orElseThrow();
        assertThat(saved.startAt()).isEqualTo(startsAt);
        assertThat(saved.endAt()).isEqualTo(endsAt);
        assertThat(saved.pickupCodeHash()).isNotEqualTo(response.pickupCode());
        assertThat(saved.pickupCodeHash()).isNotBlank();
    }

    @Test
    void endEqualToStartIsRejected() {
        Instant startsAt = NOW.plusSeconds(60);
        assertThatThrownBy(() -> service.confirmReservation(TOKEN,
                new BookingRequestDTO("bike-1", startsAt, startsAt)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void endBeforeStartIsRejected() {
        Instant startsAt = NOW.plusSeconds(120);
        assertThatThrownBy(() -> service.confirmReservation(TOKEN,
                new BookingRequestDTO("bike-1", startsAt, startsAt.minusSeconds(1))))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void missingRentalTimestampIsRejected() {
        assertThatThrownBy(() -> service.confirmReservation(TOKEN,
                new BookingRequestDTO("bike-1", NOW.plusSeconds(60), null)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}

@org.springframework.boot.test.context.SpringBootTest
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class StudentFlowIntegrationTest {
    static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");
    @Autowired InMemoryDataStore store;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired tools.jackson.databind.json.JsonMapper json;
    @Autowired ParkingReservationService parking;
    @Autowired ChargingRequestService charging;
    @Autowired StudentRideCommands commands;
    @MockitoBean StudentDevicePort device;
    @MockitoBean StudentChargingPort charger;
    @MockitoBean Clock clock;
    @MockitoBean JsonStore disk;
    Student student;
    @BeforeEach
    void seed() {
        when(clock.instant()).thenReturn(NOW);
        store.restore(new InMemoryDataStore().snapshot());
        student = new Student("s","a","Student",AccountStatus.ACTIVE,NOW);
        store.put(student);
        store.put(new Account("a","student@example.test","hash",AccountRole.STUDENT,true,NOW));
        store.put(new Session("token","a",NOW.minusSeconds(1),NOW.plusSeconds(3600)));
        store.put(new MobilityHub("h","Hub","Campus",new GeoPoint(10.0,106.0),5,3,1,0,0));
        store.put(new ParkingSpace("dock","h",ParkingSpaceStatus.OCCUPIED,VehicleType.E_BIKE,"bike",null,null));
        store.put(new ParkingSpace("return","h",ParkingSpaceStatus.AVAILABLE,VehicleType.E_BIKE,null,null,null));
        store.put(vehicle("bike",20.0,NOW.minusSeconds(600),VehicleOwnership.SHARED));
    }
    Vehicle vehicle(String id,double pct,Instant at,VehicleOwnership ownership) {
        return new Vehicle(id,"h","dock",VehicleType.E_BIKE,pct,1,1,VehicleStatus.AVAILABLE,at,1,ownership,
                ownership == VehicleOwnership.PRIVATE ? "a" : null);
    }
    org.springframework.test.web.servlet.ResultActions postJson(String path,Object body,String key) throws Exception {
        var request = post(path).header("Authorization","Bearer token").contentType("application/json").content(json.writeValueAsString(body));
        if (key != null) request.header("Idempotency-Key",key);
        return mvc.perform(request);
    }
    tools.jackson.databind.JsonNode body(org.springframework.test.web.servlet.ResultActions action) throws Exception {
        return json.readTree(action.andReturn().getResponse().getContentAsString());
    }
    @Test
    void searchAndDetailRespectBatteryRadiusAndFreshnessBoundaries() throws Exception {
        store.put(vehicle("low",19.9,NOW,VehicleOwnership.SHARED));
        store.put(vehicle("old",80,NOW.minusSeconds(601),VehicleOwnership.SHARED));
        store.put(vehicle("private",90,NOW,VehicleOwnership.PRIVATE));
        store.put(vehicle("future",90,NOW.plusSeconds(1),VehicleOwnership.SHARED));
        var center = Map.of("latitude",10,"longitude",106);
        postJson("/api/search/hubs",Map.of("origin",center,"radiusKm",50),null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.hubs[0].availableVehicleCount").value(1))
                .andExpect(jsonPath("$.hubs[0].vehicles[0].vehicleId").value("bike"));
        for (double radius : new double[]{0,-1,51}) {
            postJson("/api/search/hubs",Map.of("origin",center,"radiusKm",radius),null).andExpect(status().isBadRequest());
        }
        when(device.readVehicleStatus("bike")).thenReturn(new StudentDevicePort.Reading("bike",55.5,NOW));
        mvc.perform(get("/api/vehicles/bike")).andExpect(status().isOk()).andExpect(jsonPath("$.batteryLevel").value(55.5));
    }

    @Test
    void heldPickupAndReturnSurviveWrongAckRetryAndRestore() throws Exception {
        var booking = body(postJson("/api/reservations",Map.of("vehicleId","bike","startsAt",NOW,"endsAt",NOW.plusSeconds(3600)),null)
                .andExpect(status().isCreated()));
        String reservation = booking.get("reservationId").asString();
        String key = UUID.randomUUID().toString();
        var pickup = Map.of("vehicleId","bike","reservationId",reservation,"pickupCode",booking.get("pickupCode").asString());
        postJson("/api/pickups",Map.of("vehicleId","bike","reservationId",reservation,"pickupCode","wrong"),key)
                .andExpect(status().isForbidden());
        when(device.send(key,StudentOperation.Kind.PICKUP,"bike","dock")).thenReturn(
                new StudentDevicePort.Reply(key,"other","dock",StudentDevicePort.Outcome.ACK_SUCCESS,20.0));
        postJson("/api/pickups",pickup,key).andExpect(status().isAccepted());
        assertThat(store.findAll(Ride.class)).isEmpty();
        store.restore(json.readValue(json.writeValueAsString(store.snapshot()),StoreSnapshot.class));
        when(device.getResult(key)).thenReturn(new StudentDevicePort.Reply(key,"bike","dock",StudentDevicePort.Outcome.ACK_SUCCESS,20.0));
        var rideId = body(postJson("/api/pickups",pickup,key).andExpect(status().isCreated())).get("rideId").asString();
        postJson("/api/pickups",pickup,key).andExpect(status().isCreated()).andExpect(jsonPath("$.rideId").value(rideId));
        verify(device,times(1)).send(key,StudentOperation.Kind.PICKUP,"bike","dock");
        assertThat(store.findAll(Reservation.class).getFirst().pickupCodeHash()).isNull();
        var hold = parking.reserveSpace(student,new ParkingReservationService.ParkingRequest("h",VehicleType.E_BIKE,NOW,NOW.plusSeconds(3600),"return"));
        var destination = Map.of("hubId","h","spaceId","return");
        String lock = UUID.randomUUID().toString(), path = "/api/rides/"+rideId+"/return";
        when(device.send(lock,StudentOperation.Kind.RETURN,"bike","return")).thenReturn(
                new StudentDevicePort.Reply(lock,"bike","return",StudentDevicePort.Outcome.ACK_SUCCESS,null));
        postJson(path,destination,lock).andExpect(status().isServiceUnavailable());
        assertThat(store.findAll(Ride.class).getFirst().status()).isEqualTo(RideStatus.ACTIVE);
        when(device.getResult(lock)).thenReturn(new StudentDevicePort.Reply(lock,"bike","return",StudentDevicePort.Outcome.ACK_SUCCESS,19.9));
        mvc.perform(get("/api/operations/"+lock).header("Authorization","Bearer token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.rideId").value(rideId));
        postJson(path,destination,lock).andExpect(status().isOk()).andExpect(jsonPath("$.vehicleStatus").value("LOW_BATTERY"));
        assertThat(store.findById(ParkingReservation.class,hold.id()).orElseThrow().status()).isEqualTo(ParkingReservationStatus.COMPLETED);
        assertThat(store.findAll(Ride.class)).hasSize(1);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"20,false,AVAILABLE","20.1,false,AVAILABLE","5,true,MAINTENANCE"})
    void directRideClosesWithCorrectFinalStatus(double battery,boolean issue,String expected) throws Exception {
        var direct = Map.of("vehicleId","bike","startAt",NOW,"endAt",NOW.plusSeconds(3600));
        String key = UUID.randomUUID().toString();
        when(device.send(key,StudentOperation.Kind.PICKUP,"bike","dock")).thenReturn(new StudentDevicePort.Reply(key,"bike","dock",StudentDevicePort.Outcome.ACK_SUCCESS,null));
        String id = body(postJson("/api/pickups",direct,key).andExpect(status().isCreated())).get("rideId").asString();
        postJson("/api/pickups",Map.of("vehicleId","other","startAt",NOW,"endAt",NOW.plusSeconds(3600)),key).andExpect(status().isConflict());
        String lock = UUID.randomUUID().toString();
        when(device.send(lock,StudentOperation.Kind.RETURN,"bike","return")).thenReturn(new StudentDevicePort.Reply(lock,"bike","return",StudentDevicePort.Outcome.ACK_SUCCESS,battery));
        if (issue) store.put(new Incident("i","bike","h","Reported fault",IncidentSeverity.MEDIUM,NOW,null,IncidentStatus.OPEN));
        postJson("/api/rides/"+id+"/return",Map.of("hubId","h","spaceId","return"),lock)
                .andExpect(status().isOk()).andExpect(jsonPath("$.vehicleStatus").value(expected));
    }
    @Test
    void parkingOverlapAdjacentAndExpiryReleaseOnlyMatchingHold() {
        var first = new ParkingReservationService.ParkingRequest("h",VehicleType.E_BIKE,NOW,NOW.plusSeconds(1800),"return");
        var hold = parking.reserveSpace(student,first);
        assertThatThrownBy(() -> parking.reserveSpace(student,first)).isInstanceOf(ResponseStatusException.class);
        var second = parking.reserveSpace(student,new ParkingReservationService.ParkingRequest("h",VehicleType.E_BIKE,NOW.plusSeconds(1800),NOW.plusSeconds(3600),"return"));
        parking.expireReservation(hold.id(),NOW.plusSeconds(899));
        assertThat(store.findById(ParkingReservation.class,hold.id()).orElseThrow().status()).isEqualTo(ParkingReservationStatus.ACTIVE);
        parking.expireReservation(hold.id(),NOW.plusSeconds(900));
        parking.expireReservation(hold.id(),NOW.plusSeconds(901));
        assertThat(store.findById(ParkingSpace.class,"return").orElseThrow().parkingReservationId()).isEqualTo(second.id());
        parking.cancelReservation(student,second.id());
        assertThat(store.findById(ParkingSpace.class,"return").orElseThrow().status()).isEqualTo(ParkingSpaceStatus.AVAILABLE);
    }
    @Test
    void bookingChargingChecksPowerAndRecoversUnknownDeviceResult() throws Exception {
        store.put(vehicle("private",20,NOW,VehicleOwnership.PRIVATE));
        store.put(vehicle("second",20,NOW,VehicleOwnership.PRIVATE));
        for (String port : List.of("p1","p2")) store.put(new ChargingPort(port,"h",port,1,ChargingPortStatus.AVAILABLE));
        when(charger.readHubStatus("h")).thenReturn(new StudentChargingPort.HubStatus("h",NOW,Set.of("p1","p2")));
        when(charger.reservePort(anyString(),anyString(),any(),any())).thenReturn(StudentChargingPort.Result.UNKNOWN,StudentChargingPort.Result.CONFIRMED);
        assertThat(charging.estimateChargingTime(student,"private","p1",70.0)).isEqualTo(30);
        var b = new ChargingRequestService.Booking("private","h",NOW,70.0);
        postJson("/api/charging-requests",b,null).andExpect(status().isServiceUnavailable());
        var booked = body(postJson("/api/charging-requests",b,null).andExpect(status().isCreated()));
        postJson("/api/charging-requests",new ChargingRequestService.Booking("second","h",NOW,70.0),null)
                .andExpect(status().isUnprocessableContent());
        assertThat(store.findAll(ChargingAllocation.class)).hasSize(1);
        when(charger.releasePort(anyString(),anyString())).thenReturn(StudentChargingPort.Result.CONFIRMED);
        charging.cancelRequest(student,booked.get("chargingRequestId").asString());
        assertThat(store.findAll(ChargingAllocation.class).getFirst().status()).isEqualTo(ChargingAllocationStatus.CANCELLED);
    }

    @Test
    void physicalRejectionAndMissingAdapterDoNotCreateRide() throws Exception {
        var payload = new VehiclePickupService.PickupRequestDTO("bike",null,null,NOW,NOW.plusSeconds(3600));
        UUID key = UUID.randomUUID();
        StudentState state = commands.state();
        var offline = new StudentRideCommands(state,clock,Optional.empty(),20);
        var service = new VehiclePickupService(offline,state,clock,Duration.ofMinutes(10),20);
        assertThatThrownBy(() -> service.processScannedCode(student,key,payload))
                .isInstanceOfSatisfying(ResponseStatusException.class,e -> assertThat(e.getStatusCode().value()).isEqualTo(503));
        assertThat(store.findAll(StudentOperation.class)).isEmpty();
        when(device.send(key.toString(),StudentOperation.Kind.PICKUP,"bike","dock")).thenReturn(
                new StudentDevicePort.Reply(key.toString(),"bike","dock",StudentDevicePort.Outcome.REJECTED,null));
        postJson("/api/pickups",payload,key.toString()).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.error.code").value("IOT_COMMAND_FAILED"));
        assertThat(store.findAll(Ride.class)).isEmpty();
        assertThat(store.findAll(StudentOperation.class).getFirst().status()).isEqualTo(StudentOperation.Status.FAILED);
    }

    @Test
    void vehicleExpiryAtDeadlineRevokesCredentialAndAllowsBookingAgain() throws Exception {
        var b = Map.of("vehicleId","bike","startsAt",NOW,"endsAt",NOW.plusSeconds(3600));
        var id = body(postJson("/api/reservations",b,null).andExpect(status().isCreated())).get("reservationId").asString();
        var reservations = new ReservationService(store,commands.state().coordinator(),new InMemoryVehicleDataStore(store),
                new InMemoryReservationDataStore(store),clock,Duration.ofMinutes(15),Duration.ofMinutes(10),20);
        when(clock.instant()).thenReturn(NOW.plusSeconds(899));
        reservations.expireReservation(id);
        assertThat(store.findAll(Reservation.class).getFirst().status()).isEqualTo(ReservationStatus.ACTIVE);
        when(clock.instant()).thenReturn(NOW.plusSeconds(900));
        Vehicle reserved = store.findAll(Vehicle.class).getFirst();
        store.put(StudentRideCommands.relocate(reserved,"h","dock",VehicleStatus.RESERVED,20.1,clock.instant()));
        reservations.expireReservation(id);
        reservations.expireReservation(id);
        assertThat(store.findById(Reservation.class,id).orElseThrow().pickupCodeHash()).isNull();
        postJson("/api/reservations",b,null).andExpect(status().isCreated());
    }

    @Test
    void invalidChargingTargetWindowAndStaleTelemetryAreRejected() {
        store.put(vehicle("private",20,NOW,VehicleOwnership.PRIVATE));
        store.put(new ChargingPort("p","h","Port",1,ChargingPortStatus.AVAILABLE));
        for (double target : new double[]{0,20,101,Double.NaN}) {
            assertThatThrownBy(() -> charging.estimateChargingTime(student,"private","p",target)).isInstanceOf(ResponseStatusException.class);
        }
        assertThat(charging.estimateChargingTime(student,"private","p",100.0)).isEqualTo(48);
        assertThatThrownBy(() -> charging.bookCharging(student,new ChargingRequestService.Booking("private","h",null,100.0)))
                .isInstanceOf(ResponseStatusException.class);
        store.put(vehicle("private",20,NOW.minusSeconds(601),VehicleOwnership.PRIVATE));
        assertThatThrownBy(() -> charging.estimateChargingTime(student,"private","p",80.0)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(charger);
    }

    @Test
    void adjacentAllocationsDoNotSumTheirPowerAcrossWindows() {
        var allocator = new AllocationService();
        var hub = new MobilityHub("h","Hub","Campus",new GeoPoint(10.0,106.0),5,3,2,0,0);
        var p1 = new ChargingPort("p1","h","one",1,ChargingPortStatus.AVAILABLE);
        var p2 = new ChargingPort("p2","h","two",1,ChargingPortStatus.AVAILABLE);
        var reservations = List.of(new ChargingAllocation("a1","r1","p1",NOW,NOW.plusSeconds(60),1,ChargingAllocationStatus.RESERVED),
                new ChargingAllocation("a2","r2","p1",NOW.plusSeconds(60),NOW.plusSeconds(120),1,ChargingAllocationStatus.RESERVED));
        assertThat(allocator.fits(reservations,List.of(p1,p2),p2,hub,NOW,NOW.plusSeconds(120),1)).isTrue();
        assertThat(allocator.fits(reservations,List.of(p1,p2),p2,hub,NOW,NOW.plusSeconds(120),1.01)).isFalse();
        assertThat(allocator.fits(reservations,List.of(p1,p2),p1,hub,NOW.plusSeconds(120),NOW.plusSeconds(150),1)).isTrue();
    }

    @Test
    void invalidSessionOwnerOrMissingKeyCannotIssueCommand() throws Exception {
        var direct = Map.of("vehicleId","bike","startAt",NOW,"endAt",NOW.plusSeconds(60));
        postJson("/api/pickups",direct,null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mvc.perform(post("/api/pickups").header("Idempotency-Key",UUID.randomUUID()).contentType("application/json").content(json.writeValueAsString(direct)))
                .andExpect(status().isUnauthorized());
        store.put(new Account("a","student@example.test","hash",AccountRole.OPERATOR,true,NOW));
        postJson("/api/reservations",Map.of("vehicleId","bike","startsAt",NOW,"endsAt",NOW.plusSeconds(60)),null)
                .andExpect(status().isForbidden());
        verifyNoInteractions(device);
    }

    @Test
    void previousSnapshotResolvesReservationOwnerByAccount() {
        var old = json.readValue("""
                {"reservations":[{"id":"r","accountId":"a","vehicleId":"bike","startsAt":"2026-11-23T10:00:00Z",
                "endsAt":"2026-11-23T11:00:00Z","expiresAt":"2026-11-23T10:15:00Z","status":"ACTIVE","pickupCodeHash":"hash"}]}
                """,StoreSnapshot.class);
        var r = old.reservations().getFirst();
        assertThat(r.id()).isEqualTo("r");
        assertThat(r.isOwnedBy(student)).isTrue();
        assertThat(r.isActiveAt(NOW)).isTrue();
        assertThat(old.studentOperations()).isEmpty();
    }
}
