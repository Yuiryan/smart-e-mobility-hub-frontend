package vn.edu.bku.smart_emobility_backend.common.store;

import java.util.List;
import vn.edu.bku.smart_emobility_backend.iot.DeviceOperation;
import vn.edu.bku.smart_emobility_backend.iot.DeviceReceipt;
import vn.edu.bku.smart_emobility_backend.common.domain.*;

public record StoreSnapshot(long version, List<Account> accounts, List<Session> sessions,
        List<MobilityHub> hubs, List<Vehicle> vehicles, List<ParkingSpace> parkingSpaces,
        List<Reservation> reservations, List<ParkingReservation> parkingReservations,
        List<Ride> rides, List<ChargingPort> chargingPorts, List<ChargingRequest> chargingRequests,
        List<ChargingAllocation> chargingAllocations, List<DispatchOrder> dispatchOrders,
        List<Incident> incidents, List<RecoveryCode> recoveryCodes,
        List<RecoverySession> recoverySessions, List<DeviceOperation> deviceOperations, List<DeviceReceipt> deviceReceipts, List<ScheduleProposal> scheduleProposals,
        List<ChargingSchedule> chargingSchedules) {
    public StoreSnapshot {
        deviceOperations = safe(deviceOperations);
        deviceReceipts = safe(deviceReceipts);
        scheduleProposals = safe(scheduleProposals); chargingSchedules = safe(chargingSchedules);
        accounts = safe(accounts); sessions = safe(sessions); hubs = safe(hubs);
        vehicles = safe(vehicles); parkingSpaces = safe(parkingSpaces);
        reservations = safe(reservations); parkingReservations = safe(parkingReservations);
        rides = safe(rides); chargingPorts = safe(chargingPorts);
        chargingRequests = safe(chargingRequests); chargingAllocations = safe(chargingAllocations);
        dispatchOrders = safe(dispatchOrders); incidents = safe(incidents);
        recoveryCodes = safe(recoveryCodes); recoverySessions = safe(recoverySessions);
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
