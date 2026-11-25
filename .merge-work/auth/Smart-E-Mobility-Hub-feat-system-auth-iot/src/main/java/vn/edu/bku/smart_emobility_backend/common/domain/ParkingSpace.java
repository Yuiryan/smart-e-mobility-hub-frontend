package vn.edu.bku.smart_emobility_backend.common.domain;

public record ParkingSpace(String id, String hubId, ParkingSpaceStatus status)
        implements IdentifiedEntity { }
