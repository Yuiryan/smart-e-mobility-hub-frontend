package vn.edu.bku.smart_emobility_backend.iot;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.bku.smart_emobility_backend.common.domain.DataQuality;
import vn.edu.bku.smart_emobility_backend.common.domain.VehicleStatus;
import static org.junit.jupiter.api.Assertions.*;

class VehicleTelemetryTest {
    private final Instant now = Instant.parse("2026-01-01T12:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final SimulatorAdapter adapter = new SimulatorAdapter(clock);
    private final IoTGateway gateway = new IoTGateway(adapter, clock);

    private VehicleReading sample(Instant when, Double battery) {
        return new VehicleReading("v1", battery, VehicleStatus.RESERVED, 10.0, 106.0,
                VehicleReading.LockStatus.LOCKED, when);
    }

    @Test
    void sameReadingAgesAcrossBoundaryWithoutAnotherWrite() {
        adapter.emitTelemetry(sample(now.minusSeconds(600), 0.0));
        assertEquals(DataQuality.FRESH, gateway.getVehicleTelemetry("v1").quality());
        var later = new IoTGateway(adapter, Clock.fixed(now.plusNanos(1), ZoneOffset.UTC));
        assertEquals(DataQuality.STALE, later.getVehicleTelemetry("v1").quality());
    }

    @Test
    void missingAndPartialTelemetryPreserveUnknownFields() {
        var missing = gateway.getVehicleTelemetry("v2");
        assertEquals(DataQuality.MISSING, missing.quality());
        assertNull(missing.observedAt());
        assertNull(missing.batteryPct());
        adapter.emitTelemetry(sample(now.minusSeconds(1), 90.0));
        adapter.emitTelemetry(sample(now, null));
        var partial = gateway.getVehicleTelemetry("v1");
        assertEquals(DataQuality.MISSING, partial.quality());
        assertNull(partial.batteryPct());
        assertEquals(VehicleStatus.RESERVED, partial.status());
    }

    @Test
    void lateDuplicateAndFutureSamplesCannotReplaceLatest() {
        var first = sample(now, 100.0);
        adapter.emitTelemetry(first);
        adapter.emitTelemetry(sample(now, 89.0));
        adapter.emitTelemetry(sample(now.minusSeconds(1), 70.0));
        assertThrows(IllegalArgumentException.class, () -> adapter.emitTelemetry(sample(now.plusNanos(1), 80.0)));
        assertEquals(first, adapter.readVehicleStatus("v1").orElseThrow());
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, 100.1, Double.NaN, Double.POSITIVE_INFINITY})
    void rejectsInvalidBattery(double battery) {
        assertThrows(IllegalArgumentException.class, () -> sample(now, battery));
    }

    @Test
    void validatesCoordinatesAndDeviceIdentity() {
        assertThrows(IllegalArgumentException.class, () -> gateway.getVehicleTelemetry(" "));
        assertThrows(IllegalArgumentException.class, () -> new VehicleReading("v", null, null, 90.1, null, null, now));
        assertThrows(IllegalArgumentException.class, () -> new VehicleReading("v", null, null, null, -180.1, null, now));
        var pole = new VehicleReading("v", 100.0, VehicleStatus.AVAILABLE, -90.0, 180.0,
                VehicleReading.LockStatus.UNLOCKED, now);
        adapter.emitTelemetry(pole);
        assertEquals(DataQuality.FRESH, gateway.getVehicleTelemetry("v").quality());
    }
}
