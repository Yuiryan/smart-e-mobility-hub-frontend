package vn.edu.bku.smart_emobility_backend.common.domain;

public record GeoPoint(Double latitude, Double longitude) {
    public GeoPoint {
        if (latitude == null || !Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("latitude must be between -90 and 90");
        }
        if (longitude == null || !Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("longitude must be between -180 and 180");
        }
    }
}
