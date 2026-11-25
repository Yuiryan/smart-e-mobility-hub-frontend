package vn.edu.bku.smart_emobility_backend.common.domain;

public record ChargingPort(String id, String hubId, String name, double powerKw,
        ChargingPortStatus status) implements IdentifiedEntity { }
