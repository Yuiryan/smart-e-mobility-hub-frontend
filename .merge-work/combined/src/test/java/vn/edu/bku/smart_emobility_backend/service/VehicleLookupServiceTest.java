package vn.edu.bku.smart_emobility_backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import vn.edu.bku.smart_emobility_backend.common.domain.Vehicle;
import vn.edu.bku.smart_emobility_backend.common.domain.VehicleOwnership;
import vn.edu.bku.smart_emobility_backend.common.domain.VehicleStatus;
import vn.edu.bku.smart_emobility_backend.common.domain.VehicleType;
import vn.edu.bku.smart_emobility_backend.common.store.VehicleDataStore;
import vn.edu.bku.smart_emobility_backend.dto.VehicleDetails;

@ExtendWith(MockitoExtension.class)
class VehicleLookupServiceTest {

    private static final String VEHICLE_ID = "bike_101";
    private static final Instant NOW = Instant.parse("2026-11-23T10:00:00Z");
    private static final Duration FRESHNESS_LIMIT = Duration.ofMinutes(10);

    @Mock
    private VehicleDataStore vehicleDataStore;

    private VehicleLookupService service;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new VehicleLookupService(vehicleDataStore, fixedClock, FRESHNESS_LIMIT);
    }

    // ─── Happy path ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("UC-02: xe tồn tại, telemetry FRESH → trả đủ fields, reason=null, missingData=null")
    void getVehicleDetails_freshTelemetry_returnsDetails() {
        Instant freshAt = NOW.minus(Duration.ofMinutes(5)); // 5 phút trước → FRESH
        Vehicle vehicle = makeVehicle(VEHICLE_ID, VehicleStatus.AVAILABLE, 85.0, freshAt);
        when(vehicleDataStore.findById(VEHICLE_ID)).thenReturn(Optional.of(vehicle));

        VehicleDetails result = service.getVehicleDetails(VEHICLE_ID);

        assertThat(result.vehicleId()).isEqualTo(VEHICLE_ID);
        assertThat(result.status()).isEqualTo(VehicleStatus.AVAILABLE);
        assertThat(result.batteryLevel()).isEqualTo(85.0);
        assertThat(result.updatedAt()).isEqualTo(freshAt);
        assertThat(result.reason()).isNull();
        assertThat(result.missingData()).isNull();
    }

    @Test
    @DisplayName("UC-02: xe RESERVED với telemetry FRESH → vẫn trả đúng status RESERVED")
    void getVehicleDetails_reservedVehicle_returnsStatusReserved() {
        Instant freshAt = NOW.minus(Duration.ofMinutes(1));
        Vehicle vehicle = makeVehicle(VEHICLE_ID, VehicleStatus.RESERVED, 60.0, freshAt);
        when(vehicleDataStore.findById(VEHICLE_ID)).thenReturn(Optional.of(vehicle));

        VehicleDetails result = service.getVehicleDetails(VEHICLE_ID);

        assertThat(result.status()).isEqualTo(VehicleStatus.RESERVED);
        assertThat(result.reason()).isNull();
    }

    // ─── Telemetry STALE ──────────────────────────────────────────────────────

    @Test
    @DisplayName("UC-02: telemetry STALE → reason='Telemetry is stale', missingData=null")
    void getVehicleDetails_staleTelemetry_returnsStaleReason() {
        Instant staleAt = NOW.minus(Duration.ofMinutes(11)); // 11 phút → STALE
        Vehicle vehicle = makeVehicle(VEHICLE_ID, VehicleStatus.AVAILABLE, 70.0, staleAt);
        when(vehicleDataStore.findById(VEHICLE_ID)).thenReturn(Optional.of(vehicle));

        VehicleDetails result = service.getVehicleDetails(VEHICLE_ID);

        assertThat(result.reason()).isEqualTo("Telemetry is stale");
        assertThat(result.missingData()).isNull();
    }

    // ─── Boundary: đúng ngưỡng 10 phút ──────────────────────────────────────

    @Test
    @DisplayName("UC-02 boundary: telemetry age = chính xác 10 phút → vẫn FRESH (≤ limit)")
    void getVehicleDetails_telemetryAgeExactlyAtLimit_isFresh() {
        Instant exactLimit = NOW.minus(FRESHNESS_LIMIT); // age = 10 phút đúng
        Vehicle vehicle = makeVehicle(VEHICLE_ID, VehicleStatus.AVAILABLE, 50.0, exactLimit);
        when(vehicleDataStore.findById(VEHICLE_ID)).thenReturn(Optional.of(vehicle));

        VehicleDetails result = service.getVehicleDetails(VEHICLE_ID);

        assertThat(result.reason()).isNull(); // FRESH
        assertThat(result.missingData()).isNull();
    }

    @Test
    @DisplayName("UC-02 boundary: telemetry age = 10 phút + 1 giây → STALE")
    void getVehicleDetails_telemetryAgeOneSecondOverLimit_isStale() {
        Instant justOverLimit = NOW.minus(FRESHNESS_LIMIT).minus(Duration.ofSeconds(1));
        Vehicle vehicle = makeVehicle(VEHICLE_ID, VehicleStatus.AVAILABLE, 50.0, justOverLimit);
        when(vehicleDataStore.findById(VEHICLE_ID)).thenReturn(Optional.of(vehicle));

        VehicleDetails result = service.getVehicleDetails(VEHICLE_ID);

        assertThat(result.reason()).isEqualTo("Telemetry is stale");
    }

    @Test
    @DisplayName("UC-02 boundary: telemetry age = 10 phút - 1 giây → vẫn FRESH")
    void getVehicleDetails_telemetryAgeOneSecondUnderLimit_isFresh() {
        Instant justUnderLimit = NOW.minus(FRESHNESS_LIMIT).plus(Duration.ofSeconds(1));
        Vehicle vehicle = makeVehicle(VEHICLE_ID, VehicleStatus.AVAILABLE, 50.0, justUnderLimit);
        when(vehicleDataStore.findById(VEHICLE_ID)).thenReturn(Optional.of(vehicle));

        VehicleDetails result = service.getVehicleDetails(VEHICLE_ID);

        assertThat(result.reason()).isNull();
    }

    // ─── Telemetry MISSING ────────────────────────────────────────────────────

    @Test
    @DisplayName("UC-02: telemetry=null → reason='Telemetry is missing', missingData='telemetryAt'")
    void getVehicleDetails_missingTelemetry_returnsMissingReason() {
        Vehicle vehicle = makeVehicle(VEHICLE_ID, VehicleStatus.AVAILABLE, 90.0, null);
        when(vehicleDataStore.findById(VEHICLE_ID)).thenReturn(Optional.of(vehicle));

        VehicleDetails result = service.getVehicleDetails(VEHICLE_ID);

        assertThat(result.reason()).isEqualTo("Telemetry is missing");
        assertThat(result.missingData()).isEqualTo("telemetryAt");
    }

    @Test
    @DisplayName("UC-02: batteryPct=null được trả về null trong response")
    void getVehicleDetails_nullBattery_returnedAsNull() {
        Instant freshAt = NOW.minus(Duration.ofMinutes(2));
        Vehicle vehicle = makeVehicle(VEHICLE_ID, VehicleStatus.LOW_BATTERY, null, freshAt);
        when(vehicleDataStore.findById(VEHICLE_ID)).thenReturn(Optional.of(vehicle));

        VehicleDetails result = service.getVehicleDetails(VEHICLE_ID);

        assertThat(result.batteryLevel()).isNull();
        assertThat(result.status()).isEqualTo(VehicleStatus.LOW_BATTERY);
    }

    // ─── Lỗi: xe không tồn tại ───────────────────────────────────────────────

    @Test
    @DisplayName("UC-02: vehicleId không tồn tại → 404 Not Found")
    void getVehicleDetails_vehicleNotFound_throws404() {
        when(vehicleDataStore.findById("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getVehicleDetails("unknown"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("vehicle not found");
    }

    @Test
    @DisplayName("UC-02: vehicleId rỗng → 404 Not Found")
    void getVehicleDetails_blankVehicleId_throws404() {
        when(vehicleDataStore.findById("")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getVehicleDetails(""))
                .isInstanceOf(ResponseStatusException.class);
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private Vehicle makeVehicle(String vehicleId, VehicleStatus status, Double batteryPct,
            Instant telemetryAt) {
        return new Vehicle(vehicleId, "hub_01", "space_01",
                VehicleType.E_BIKE, batteryPct, 0.5, 0.3,
                status, telemetryAt, 1L,
                VehicleOwnership.SHARED, null);
    }
}
