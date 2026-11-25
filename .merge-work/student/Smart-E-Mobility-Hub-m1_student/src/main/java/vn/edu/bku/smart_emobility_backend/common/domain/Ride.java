package vn.edu.bku.smart_emobility_backend.common.domain;

import java.time.Instant;
public record Ride(String id, String accountId, String vehicleId, Instant startedAt,
        Instant endedAt, Double startBatteryPct, Double endBatteryPct, RideStatus status, String studentId,
        String reservationId, String requestId, String startHubId, String endHubId) implements IdentifiedEntity {
    public Ride(String id,String accountId,String vehicleId,Instant startedAt,Instant endedAt,int startBatteryPct,Integer endBatteryPct,RideStatus status) {
        this(id,accountId,vehicleId,startedAt,endedAt,(double)startBatteryPct,endBatteryPct == null ? null : endBatteryPct.doubleValue(),status,null,null,null,null,null);
    }
    public Ride complete(Instant at, double battery, String hub) {
        return new Ride(id, accountId, vehicleId, startedAt, at, startBatteryPct, battery,
                RideStatus.COMPLETED, studentId, reservationId, requestId, startHubId, hub);
    }
}
