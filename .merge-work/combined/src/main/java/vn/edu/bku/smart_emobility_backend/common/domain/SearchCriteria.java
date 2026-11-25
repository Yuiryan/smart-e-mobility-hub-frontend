package vn.edu.bku.smart_emobility_backend.common.domain;

public record SearchCriteria(GeoPoint origin, GeoPoint destination, double radiusKm) {
    public GeoPoint referencePoint() {
        return origin != null ? origin : destination;
    }
}
