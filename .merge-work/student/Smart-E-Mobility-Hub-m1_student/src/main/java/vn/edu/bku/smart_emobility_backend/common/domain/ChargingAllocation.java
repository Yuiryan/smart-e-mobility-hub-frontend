package vn.edu.bku.smart_emobility_backend.common.domain;

import java.time.Instant;

public record ChargingAllocation(String id, String chargingRequestId, String chargingPortId,
        Instant startsAt, Instant endsAt, double allocatedPowerKw,
        ChargingAllocationStatus status, String vehicleId, String scheduleId) implements IdentifiedEntity {
    public ChargingAllocation(String id,String requestId,String portId,Instant start,Instant end,double power,ChargingAllocationStatus status) {
        this(id,requestId,portId,start,end,power,status,null,null);
    }
    public ChargingAllocation cancel() {
        return new ChargingAllocation(id, chargingRequestId, chargingPortId, startsAt, endsAt,
                allocatedPowerKw,ChargingAllocationStatus.CANCELLED,vehicleId,scheduleId);
    }
}
