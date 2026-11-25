package vn.edu.bku.smart_emobility_backend.simulation;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record SimulationResult(String simulationId, String status, List<String> projectedOverloadHubs,
        List<DispatchProposal> recommendedDispatch, String disclaimer, long sourceVersion, Instant generatedAt,
        long failedBookingsCount, long failedReturnsCount, Map<String, Double> congestionMetrics, List<String> missingData) {
    public SimulationResult {
        projectedOverloadHubs = List.copyOf(projectedOverloadHubs);
        recommendedDispatch = List.copyOf(recommendedDispatch);
        congestionMetrics = Map.copyOf(congestionMetrics);
        missingData = List.copyOf(missingData);
    }

    public record DispatchProposal(String proposalId, String fromHub, String toHub, int vehicleCount) {}
}
