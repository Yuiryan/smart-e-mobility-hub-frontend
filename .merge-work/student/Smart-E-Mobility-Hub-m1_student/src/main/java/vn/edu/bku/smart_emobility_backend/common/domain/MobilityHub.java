package vn.edu.bku.smart_emobility_backend.common.domain;

public record MobilityHub(String hubId, String name, String address, GeoPoint location,
        double serviceRadiusKm, int parkingCapacity, double chargingCapacityKw,
        int expectedIncoming, int expectedOutgoing) implements IdentifiedEntity {

    @Override
    public String id() {
        return hubId;
    }

    public double distanceTo(GeoPoint point) {
        double earthRadiusKm = 6371.0;
        double latitude1 = Math.toRadians(location.latitude());
        double latitude2 = Math.toRadians(point.latitude());
        double deltaLatitude = Math.toRadians(point.latitude() - location.latitude());
        double deltaLongitude = Math.toRadians(point.longitude() - location.longitude());
        double haversine = Math.pow(Math.sin(deltaLatitude / 2), 2)
                + Math.cos(latitude1) * Math.cos(latitude2)
                        * Math.pow(Math.sin(deltaLongitude / 2), 2);
        return 2 * earthRadiusKm * Math.asin(Math.sqrt(Math.min(1.0, haversine)));
    }
}
