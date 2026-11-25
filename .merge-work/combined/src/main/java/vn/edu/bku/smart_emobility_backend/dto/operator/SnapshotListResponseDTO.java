package vn.edu.bku.smart_emobility_backend.dto.operator;

import java.util.List;

public record SnapshotListResponseDTO<T>(
        long snapshotVersion,
        List<T> items) {
    public SnapshotListResponseDTO {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
