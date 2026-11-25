package vn.edu.bku.smart_emobility_backend.dto.operator;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.util.LinkedHashMap;
import java.util.Map;

public class IncidentResolveRequestDTO {
    private Long expectedVersion;
    private Long expectedResourceVersion;
    private String resolutionNote;
    private final Map<String, Object> unknownFields = new LinkedHashMap<>();

    public IncidentResolveRequestDTO() {
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

    public String getResolutionNote() {
        return resolutionNote;
    }

    public void setResolutionNote(String resolutionNote) {
        this.resolutionNote = resolutionNote;
    }
}
