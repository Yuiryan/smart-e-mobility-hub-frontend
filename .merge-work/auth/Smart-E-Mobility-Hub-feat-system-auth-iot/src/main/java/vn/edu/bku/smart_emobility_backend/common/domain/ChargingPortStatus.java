package vn.edu.bku.smart_emobility_backend.common.domain;

import com.fasterxml.jackson.annotation.JsonAlias;

public enum ChargingPortStatus {
    AVAILABLE,
    @JsonAlias("RESERVED") SCHEDULED,
    CHARGING,
    @JsonAlias("OUT_OF_SERVICE") MAINTENANCE;

    @Deprecated public static final ChargingPortStatus RESERVED = SCHEDULED;
    @Deprecated public static final ChargingPortStatus OUT_OF_SERVICE = MAINTENANCE;
}
