package vn.edu.bku.smart_emobility_backend.dto.operator;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.util.LinkedHashMap;
import java.util.Map;

public class InterventionRequestDTO {
    private String resourceType;
    private String resourceId;
    private String action;
    private String reason;
    private Long expectedVersion;
    private Map<String, Object> parameters;
    private final Map<String, Object> unknownFields = new LinkedHashMap<>();

    public InterventionRequestDTO() {
    }

    public InterventionRequestDTO(
            String resourceType,
            String resourceId,
            String action,
            String reason,
            Long expectedVersion,
            Map<String, Object> parameters) {
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.action = action;
        this.reason = reason;
        this.expectedVersion = expectedVersion;
        this.parameters = parameters;
    }

    @JsonAnySetter
    public void setUnknownField(String key, Object value) {
        unknownFields.put(key, value);
    }

    public Map<String, Object> unknownFields() {
        return unknownFields;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getExpectedVersion() {
        return expectedVersion;
    }

    public void setExpectedVersion(Long expectedVersion) {
        this.expectedVersion = expectedVersion;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }
}
