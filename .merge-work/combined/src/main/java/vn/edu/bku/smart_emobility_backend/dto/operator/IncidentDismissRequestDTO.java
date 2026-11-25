package vn.edu.bku.smart_emobility_backend.dto.operator;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.util.LinkedHashMap;
import java.util.Map;

public class IncidentDismissRequestDTO {
    private Long expectedVersion;
    private String dismissNote;
    private final Map<String, Object> unknownFields = new LinkedHashMap<>();

    public IncidentDismissRequestDTO() {
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

    public String getDismissNote() {
        return dismissNote;
    }

    public void setDismissNote(String dismissNote) {
        this.dismissNote = dismissNote;
    }
}
