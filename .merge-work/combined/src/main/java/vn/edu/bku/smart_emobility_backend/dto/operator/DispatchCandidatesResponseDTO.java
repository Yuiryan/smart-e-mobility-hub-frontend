package vn.edu.bku.smart_emobility_backend.dto.operator;

import java.util.List;

public record DispatchCandidatesResponseDTO(
        long snapshotVersion,
        String sourceHubId,
        TransportMode transportMode,
        List<OperatorVehicleDTO> items,
        List<DestinationOptionDTO> destinationOptions) {
    public DispatchCandidatesResponseDTO {
        items = items == null ? List.of() : List.copyOf(items);
        destinationOptions = destinationOptions == null ? List.of() : List.copyOf(destinationOptions);
    }

    public record DestinationOptionDTO(
            String hubId,
            String hubName,
            int freeSpaceCount) {
    }
}
