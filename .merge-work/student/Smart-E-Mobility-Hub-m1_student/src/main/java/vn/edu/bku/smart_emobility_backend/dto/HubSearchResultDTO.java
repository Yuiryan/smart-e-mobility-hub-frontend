package vn.edu.bku.smart_emobility_backend.dto;

import java.util.List;
import vn.edu.bku.smart_emobility_backend.common.domain.DataQuality;
import vn.edu.bku.smart_emobility_backend.common.domain.GeoPoint;

public record HubSearchResultDTO(String hubId, String hubName, GeoPoint location, double distanceKm,
        int availableVehicleCount, int availableSpaceCount, List<VehicleSummaryDTO> vehicles,
        DataQuality telemetryStatus) {
    public HubSearchResultDTO {
        vehicles = List.copyOf(vehicles);
    }
}
