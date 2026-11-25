package vn.edu.bku.smart_emobility_backend.dto.operator;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.util.LinkedHashMap;
import java.util.Map;

public class DispatchVersionRequestDTO {
    private Long expectedVersion;
    private final Map<String, Object> unknownFields = new LinkedHashMap<>();

    public DispatchVersionRequestDTO() {
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
}
