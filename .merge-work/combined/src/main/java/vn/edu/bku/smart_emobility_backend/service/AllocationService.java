package vn.edu.bku.smart_emobility_backend.service;

import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import vn.edu.bku.smart_emobility_backend.common.domain.*;

@Service
public class AllocationService {
    public boolean fits(List<ChargingAllocation> allocations, List<ChargingPort> ports, ChargingPort port,
            MobilityHub hub, Instant start, Instant end, double power) {
        Set<String> hubPorts = new HashSet<>();
        ports.stream().filter(p -> hub.id().equals(p.hubId())).forEach(p -> hubPorts.add(p.id()));
        TreeMap<Instant,Double> changes = new TreeMap<>();
        for (ChargingAllocation a : allocations) {
            if (a.status() == ChargingAllocationStatus.CANCELLED || a.status() == ChargingAllocationStatus.COMPLETED
                    || !hubPorts.contains(a.chargingPortId()) || !StudentState.overlaps(start,end,a.startsAt(),a.endsAt())) continue;
            if (port.id().equals(a.chargingPortId())) return false;
            changes.merge(a.startsAt().isBefore(start) ? start : a.startsAt(), a.allocatedPowerKw(), Double::sum);
            changes.merge(a.endsAt(), -a.allocatedPowerKw(), Double::sum);
        }
        double load = power;
        if (!(load <= hub.chargingCapacityKw())) return false;
        for (double delta : changes.values()) {
            load += delta;
            if (!(load <= hub.chargingCapacityKw())) return false;
        }
        return true;
    }
}
