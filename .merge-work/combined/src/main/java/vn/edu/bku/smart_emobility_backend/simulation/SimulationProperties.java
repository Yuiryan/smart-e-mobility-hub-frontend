package vn.edu.bku.smart_emobility_backend.simulation;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.simulation")
public record SimulationProperties(@DefaultValue("PT24H") Duration lookback,
        @DefaultValue("PT15M") Duration step, @DefaultValue("1.0") double consumptionPercentPerKm, @DefaultValue("100000") long maxGeneratedTrips) {
    public SimulationProperties {
        if (lookback == null || lookback.compareTo(Duration.ofHours(1)) < 0
                || lookback.compareTo(Duration.ofDays(365)) > 0
                || step == null || step.compareTo(Duration.ofMinutes(1)) < 0 || step.compareTo(Duration.ofHours(1)) > 0
                || !Double.isFinite(consumptionPercentPerKm) || consumptionPercentPerKm <= 0 || maxGeneratedTrips <= 0) {
            throw new IllegalArgumentException("Invalid simulation configuration");
        }
    }
}
