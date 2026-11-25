package vn.edu.bku.smart_emobility_backend.dto.operator;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.util.LinkedHashMap;
import java.util.Map;

public class IncidentMaintenanceRequestDTO {
    private Long expectedVersion;
    private Long expectedResourceVersion;
    private String reason;
    private final Map<String, Object> unknownFields = new LinkedHashMap<>();

    public IncidentMaintenanceRequestDTO() {
    }

    @JsonAnySetter
    public void setUnknownField(String key, Object value) {
        unknownFields.put(key, value);
    }

    public Map<String, Object> unknownFields() {
        return unknownFields;
    }

    public Long getExpectedVersion() {
        return expectedVersion;
    }

    public void setExpectedVersion(Long expectedVersion) {
        this.expectedVersion = expectedVersion;
    }

    public Long getExpectedResourceVersion() {
        return expectedResourceVersion;
    }

    public void setExpectedResourceVersion(Long expectedResourceVersion) {
        this.expectedResourceVersion = expectedResourceVersion;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
