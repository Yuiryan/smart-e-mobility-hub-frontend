package vn.edu.bku.smart_emobility_backend.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import vn.edu.bku.smart_emobility_backend.common.domain.VehicleStatus;
import vn.edu.bku.smart_emobility_backend.dto.VehicleDetails;
import vn.edu.bku.smart_emobility_backend.service.VehicleLookupService;

@WebMvcTest(VehicleDetailsController.class)
class VehicleDetailsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VehicleLookupService vehicleLookupService;

    // ─── Happy path ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("UC-02 GET /api/vehicles/{id}: xe FRESH → 200, reason=null, missingData=null")
    void getVehicle_fresh_returns200WithNullReason() throws Exception {
        Instant updatedAt = Instant.parse("2026-11-23T09:55:00Z");
        VehicleDetails dto = new VehicleDetails("bike_101", VehicleStatus.AVAILABLE,
                85.5, updatedAt, null, null);
        when(vehicleLookupService.getVehicleDetails("bike_101")).thenReturn(dto);

        mockMvc.perform(get("/api/vehicles/bike_101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleId").value("bike_101"))
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.batteryLevel").value(85.5))
                .andExpect(jsonPath("$.reason").doesNotExist())
                .andExpect(jsonPath("$.missingData").doesNotExist());
    }

    // ─── Telemetry STALE ──────────────────────────────────────────────────────

    @Test
    @DisplayName("UC-02 GET /api/vehicles/{id}: telemetry STALE → 200, reason='Telemetry is stale'")
    void getVehicle_stale_returns200WithStaleReason() throws Exception {
        Instant staleAt = Instant.parse("2026-11-23T09:00:00Z");
        VehicleDetails dto = new VehicleDetails("bike_102", VehicleStatus.AVAILABLE,
                60.0, staleAt, "Telemetry is stale", null);
        when(vehicleLookupService.getVehicleDetails("bike_102")).thenReturn(dto);

        mockMvc.perform(get("/api/vehicles/bike_102"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicleId").value("bike_102"))
                .andExpect(jsonPath("$.reason").value("Telemetry is stale"))
                .andExpect(jsonPath("$.missingData").doesNotExist());
    }

    // ─── Telemetry MISSING ────────────────────────────────────────────────────

    @Test
    @DisplayName("UC-02 GET /api/vehicles/{id}: telemetry MISSING → 200, reason='Telemetry is missing', missingData='telemetryAt'")
    void getVehicle_missing_returns200WithMissingReason() throws Exception {
        VehicleDetails dto = new VehicleDetails("bike_103", VehicleStatus.AVAILABLE,
                90.0, null, "Telemetry is missing", "telemetryAt");
        when(vehicleLookupService.getVehicleDetails("bike_103")).thenReturn(dto);

        mockMvc.perform(get("/api/vehicles/bike_103"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value("Telemetry is missing"))
                .andExpect(jsonPath("$.missingData").value("telemetryAt"));
    }

    // ─── Lỗi: 404 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("UC-02 GET /api/vehicles/{id}: xe không tồn tại → 404 Not Found")
    void getVehicle_notFound_returns404() throws Exception {
        when(vehicleLookupService.getVehicleDetails("unknown_vehicle"))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "vehicle not found"));

        mockMvc.perform(get("/api/vehicles/unknown_vehicle"))
                .andExpect(status().isNotFound());
    }

    // ─── Các trạng thái xe khác ───────────────────────────────────────────────

    @Test
    @DisplayName("UC-02 GET /api/vehicles/{id}: xe LOW_BATTERY → 200, trả về đúng status")
    void getVehicle_lowBattery_returns200() throws Exception {
        Instant updatedAt = Instant.parse("2026-11-23T09:58:00Z");
        VehicleDetails dto = new VehicleDetails("bike_104", VehicleStatus.LOW_BATTERY,
                18.5, updatedAt, null, null);
        when(vehicleLookupService.getVehicleDetails("bike_104")).thenReturn(dto);

        mockMvc.perform(get("/api/vehicles/bike_104"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOW_BATTERY"))
                .andExpect(jsonPath("$.batteryLevel").value(18.5));
    }

    @Test
    @DisplayName("UC-02 GET /api/vehicles/{id}: xe MAINTENANCE → 200, trả về đúng status")
    void getVehicle_maintenance_returns200() throws Exception {
        Instant updatedAt = Instant.parse("2026-11-23T09:50:00Z");
        VehicleDetails dto = new VehicleDetails("bike_105", VehicleStatus.MAINTENANCE,
                50.0, updatedAt, null, null);
        when(vehicleLookupService.getVehicleDetails("bike_105")).thenReturn(dto);

        mockMvc.perform(get("/api/vehicles/bike_105"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MAINTENANCE"));
    }

    @Test
    @DisplayName("UC-02 GET /api/vehicles/{id}: batteryLevel=null → field không có trong JSON")
    void getVehicle_nullBattery_batteryLevelAbsent() throws Exception {
        Instant updatedAt = Instant.parse("2026-11-23T09:55:00Z");
        VehicleDetails dto = new VehicleDetails("bike_106", VehicleStatus.LOW_BATTERY,
                null, updatedAt, null, null);
        when(vehicleLookupService.getVehicleDetails("bike_106")).thenReturn(dto);

        mockMvc.perform(get("/api/vehicles/bike_106"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batteryLevel").doesNotExist());
    }
}
