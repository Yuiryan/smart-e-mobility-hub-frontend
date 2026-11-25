package vn.edu.bku.smart_emobility_backend.common.domain;

public record MobilityHub(String hubId, String name, String address, GeoPoint location,
        double serviceRadiusKm, int parkingCapacity, double chargingCapacityKw,
        int expectedIncoming, int expectedOutgoing, HubStatus status) implements IdentifiedEntity {

    public MobilityHub {
        status = status == null ? HubStatus.ACTIVE : status;
    }

    public MobilityHub(String hubId, String name, String address, GeoPoint location,
            double serviceRadiusKm, int parkingCapacity, double chargingCapacityKw,
            int expectedIncoming, int expectedOutgoing) {
        this(hubId, name, address, location, serviceRadiusKm, parkingCapacity,
                chargingCapacityKw, expectedIncoming, expectedOutgoing, HubStatus.ACTIVE);
    }

    public MobilityHub(String id, String name, double latitude, double longitude,
            int parkingCapacity, double chargingCapacityKw, HubStatus status) {
        this(id, name, null, new GeoPoint(latitude, longitude), 0.0,
                parkingCapacity, chargingCapacityKw, 0, 0, status);
    }

    @Override
    public String id() {
        return hubId;
    }

    public double latitude() {
        return location.latitude();
    }

    public double longitude() {
        return location.longitude();
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
