package vn.edu.bku.smart_emobility_backend.common.store;

import java.util.List;
import vn.edu.bku.smart_emobility_backend.iot.DeviceOperation;
import vn.edu.bku.smart_emobility_backend.iot.DeviceReceipt;
import vn.edu.bku.smart_emobility_backend.common.domain.*;

public record StoreSnapshot(Long version, List<Account> accounts, List<Student> students, List<Session> sessions,
        List<MobilityHub> hubs, List<Vehicle> vehicles, List<ParkingSpace> parkingSpaces,
        List<Reservation> reservations, List<ParkingReservation> parkingReservations,
        List<Ride> rides, List<ChargingPort> chargingPorts, List<ChargingRequest> chargingRequests,
        List<ChargingAllocation> chargingAllocations, List<DispatchOrder> dispatchOrders,
        List<Incident> incidents, List<StudentOperation> studentOperations,
        List<ChargingSession> chargingSessions, List<RecoveryCode> recoveryCodes,
        List<RecoverySession> recoverySessions, List<DeviceOperation> deviceOperations,
        List<DeviceReceipt> deviceReceipts, List<ScheduleProposal> scheduleProposals,
        List<ChargingSchedule> chargingSchedules) {
    public StoreSnapshot {
        version = version == null ? 0L : version;
        chargingSessions = safe(chargingSessions);
        studentOperations = safe(studentOperations);
        deviceOperations = safe(deviceOperations);
        deviceReceipts = safe(deviceReceipts);
        scheduleProposals = safe(scheduleProposals); chargingSchedules = safe(chargingSchedules);
        accounts = safe(accounts); students = safe(students); sessions = safe(sessions); hubs = safe(hubs);
        vehicles = safe(vehicles); parkingSpaces = safe(parkingSpaces);
        reservations = safe(reservations); parkingReservations = safe(parkingReservations);
        rides = safe(rides); chargingPorts = safe(chargingPorts);
        chargingRequests = safe(chargingRequests); chargingAllocations = safe(chargingAllocations);
        dispatchOrders = safe(dispatchOrders); incidents = safe(incidents);
        recoveryCodes = safe(recoveryCodes); recoverySessions = safe(recoverySessions);
    }

    public StoreSnapshot(Long version, List<Account> accounts, List<Student> students,
            List<Session> sessions, List<MobilityHub> hubs, List<Vehicle> vehicles,
            List<ParkingSpace> parkingSpaces, List<Reservation> reservations,
            List<ParkingReservation> parkingReservations, List<Ride> rides,
            List<ChargingPort> chargingPorts, List<ChargingRequest> chargingRequests,
            List<ChargingAllocation> chargingAllocations, List<DispatchOrder> dispatchOrders,
            List<Incident> incidents, List<StudentOperation> studentOperations,
            List<ChargingSession> chargingSessions) {
        this(version, accounts, students, sessions, hubs, vehicles, parkingSpaces,
                reservations, parkingReservations, rides, chargingPorts, chargingRequests,
                chargingAllocations, dispatchOrders, incidents, studentOperations,
                chargingSessions, null, null, null, null, null, null);
    }

    public StoreSnapshot(Long version, List<Account> accounts, List<Session> sessions,
            List<MobilityHub> hubs, List<Vehicle> vehicles, List<ParkingSpace> parkingSpaces,
            List<Reservation> reservations, List<ParkingReservation> parkingReservations,
            List<Ride> rides, List<ChargingPort> chargingPorts,
            List<ChargingRequest> chargingRequests, List<ChargingAllocation> chargingAllocations,
            List<DispatchOrder> dispatchOrders, List<Incident> incidents) {
        this(version, accounts, null, sessions, hubs, vehicles, parkingSpaces,
                reservations, parkingReservations, rides, chargingPorts, chargingRequests,
                chargingAllocations, dispatchOrders, incidents, null, null,
                null, null, null, null, null, null);
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
