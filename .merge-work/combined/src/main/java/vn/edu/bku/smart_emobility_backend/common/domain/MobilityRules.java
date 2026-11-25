package vn.edu.bku.smart_emobility_backend.common.domain;

import java.time.Duration;

public final class MobilityRules {
    public static final double MIN_RENTAL_BATTERY_PCT = 20.0;
    public static final Duration TELEMETRY_FRESHNESS_LIMIT = Duration.ofMinutes(10);
    public static final double OVERLOAD_OCCUPANCY_PCT = 85.0;
    public static final int LOW_VEHICLE_COUNT = 2;

    private MobilityRules() {}
}
