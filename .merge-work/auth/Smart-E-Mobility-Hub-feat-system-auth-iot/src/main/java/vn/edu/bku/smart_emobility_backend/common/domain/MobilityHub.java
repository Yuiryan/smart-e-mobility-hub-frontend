package vn.edu.bku.smart_emobility_backend.common.domain;

public record MobilityHub(String id, String name, double latitude, double longitude,
        int parkingCapacity, double chargingCapacityKw, HubStatus status)
        implements IdentifiedEntity { }
