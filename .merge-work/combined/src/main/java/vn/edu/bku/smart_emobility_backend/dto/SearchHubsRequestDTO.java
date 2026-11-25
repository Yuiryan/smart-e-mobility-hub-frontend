package vn.edu.bku.smart_emobility_backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import vn.edu.bku.smart_emobility_backend.common.domain.GeoPoint;

public record SearchHubsRequestDTO(
        @Valid GeoPoint origin,
        @Valid GeoPoint destination,
        @DecimalMin(value = "0.0", inclusive = false) double radiusKm) {
}
