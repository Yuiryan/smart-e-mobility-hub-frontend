package vn.edu.bku.smart_emobility_backend.dto.operator;

import java.util.List;

public record DispatchOrderDTO(
        String dispatchOrderId,
        String sourceHubId,
        String destinationHubId,
        List<String> vehicleIds,
        TransportMode transportMode,
        String reason,
        OperatorDispatchStatus status,
        String actorId,
        String createdAt,
        String updatedAt,
        String arrivedAt,
        String cancelledAt,
        long version) {
    public DispatchOrderDTO {
        vehicleIds = vehicleIds == null ? List.of() : List.copyOf(vehicleIds);
    }

    public record DispatchOrderListResponseDTO(List<DispatchOrderDTO> items) {
        public DispatchOrderListResponseDTO {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }
}
