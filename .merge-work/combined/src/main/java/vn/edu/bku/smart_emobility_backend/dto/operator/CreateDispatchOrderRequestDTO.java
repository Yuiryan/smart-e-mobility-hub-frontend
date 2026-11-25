package vn.edu.bku.smart_emobility_backend.dto.operator;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CreateDispatchOrderRequestDTO {
    private String sourceHubId;
    private String destinationHubId;
    private List<String> vehicleIds;
    private String transportMode;
    private String reason;
    private final Map<String, Object> unknownFields = new LinkedHashMap<>();

    public CreateDispatchOrderRequestDTO() {
    }

    @JsonAnySetter
    public void setUnknownField(String key, Object value) {
        unknownFields.put(key, value);
    }

    public Map<String, Object> unknownFields() {
        return unknownFields;
    }

    public String getSourceHubId() {
        return sourceHubId;
    }

    public void setSourceHubId(String sourceHubId) {
        this.sourceHubId = sourceHubId;
    }

    public String getDestinationHubId() {
        return destinationHubId;
    }

    public void setDestinationHubId(String destinationHubId) {
        this.destinationHubId = destinationHubId;
    }

    public List<String> getVehicleIds() {
        return vehicleIds;
    }

    public void setVehicleIds(List<String> vehicleIds) {
        this.vehicleIds = vehicleIds;
    }

    public String getTransportMode() {
        return transportMode;
    }

    public void setTransportMode(String transportMode) {
        this.transportMode = transportMode;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
