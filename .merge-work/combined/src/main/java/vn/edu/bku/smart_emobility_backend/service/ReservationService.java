package vn.edu.bku.smart_emobility_backend.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import vn.edu.bku.smart_emobility_backend.common.domain.ParkingSpace;
import vn.edu.bku.smart_emobility_backend.common.domain.Student;
import vn.edu.bku.smart_emobility_backend.common.domain.StudentOperation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.bku.smart_emobility_backend.common.domain.Account;
import vn.edu.bku.smart_emobility_backend.common.domain.AccountRole;
import vn.edu.bku.smart_emobility_backend.common.domain.Reservation;
import vn.edu.bku.smart_emobility_backend.common.domain.ReservationStatus;
import vn.edu.bku.smart_emobility_backend.common.domain.Ride;
import vn.edu.bku.smart_emobility_backend.common.domain.RideStatus;
import vn.edu.bku.smart_emobility_backend.common.domain.Session;
import vn.edu.bku.smart_emobility_backend.common.domain.Vehicle;
import vn.edu.bku.smart_emobility_backend.common.domain.VehicleStatus;
import vn.edu.bku.smart_emobility_backend.common.store.InMemoryDataStore;
import vn.edu.bku.smart_emobility_backend.common.store.ReservationDataStore;
import vn.edu.bku.smart_emobility_backend.common.store.StateCoordinator;
import vn.edu.bku.smart_emobility_backend.common.store.VehicleDataStore;
import vn.edu.bku.smart_emobility_backend.dto.BookingRequestDTO;
import vn.edu.bku.smart_emobility_backend.dto.BookingResponseDTO;

@Service
public class ReservationService {
    private final InMemoryDataStore dataStore;
    private final StateCoordinator stateCoordinator;
    private final VehicleDataStore vehicleDataStore;
    private final ReservationDataStore reservationDataStore;
    private final Clock clock;
    private final Duration holdTimeout;
    private final Duration telemetryFreshness;
    private final double minBatteryPct;
    private final SecureRandom secureRandom = new SecureRandom();

    public ReservationService(InMemoryDataStore dataStore, StateCoordinator stateCoordinator,
            VehicleDataStore vehicleDataStore, ReservationDataStore reservationDataStore,
            Clock clock,
            @Value("${app.reservation.hold-timeout:PT15M}") Duration holdTimeout,
            @Value("${app.search.freshness-limit:PT10M}") Duration telemetryFreshness,
            @Value("${app.search.min-battery-pct:20}") double minBatteryPct) {
        this.dataStore = dataStore;
        this.stateCoordinator = stateCoordinator;
        this.vehicleDataStore = vehicleDataStore;
        this.reservationDataStore = reservationDataStore;
        this.clock = clock;
        this.holdTimeout = holdTimeout;
        this.telemetryFreshness = telemetryFreshness;
        this.minBatteryPct = minBatteryPct;
    }

    public BookingResponseDTO confirmReservation(String bearerToken, BookingRequestDTO request) {
        Student student = new StudentAccess(dataStore, clock).fromToken(bearerToken);
        if (request.startsAt() == null || request.endsAt() == null
                || !request.endsAt().isAfter(request.startsAt()) || !clock.instant().isBefore(request.endsAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "startsAt and endsAt are required and endsAt must be after startsAt");
        }
        String vehicleId = request.vehicleId();
        Instant now = clock.instant();
        Vehicle vehicle = vehicleDataStore.findById(vehicleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "vehicle not found"));
        if (vehicle.status() == VehicleStatus.RESERVED) throw new StudentFailure(HttpStatus.CONFLICT,"VEHICLE_ALREADY_RESERVED","Vehicle already reserved");
        if (!vehicle.isSearchable(now, telemetryFreshness, minBatteryPct)) {
            throw new StudentFailure(HttpStatus.UNPROCESSABLE_CONTENT,"VEHICLE_NOT_RENTABLE",
                    "vehicle is not rentable");
        }
        boolean hasReservation = reservationDataStore.listAll().stream()
                .anyMatch(reservation -> reservation.isOwnedBy(student)
                        && reservation.status() == ReservationStatus.ACTIVE
                        && reservation.isActiveAt(now));
        boolean hasRide = dataStore.findAll(Ride.class).stream()
                .anyMatch(ride -> student.accountId().equals(ride.accountId())
                        && ride.status() == RideStatus.ACTIVE);
        if (hasRide) throw new StudentFailure(HttpStatus.CONFLICT,"ACTIVE_RIDE_EXISTS","Student already has an active ride");
        if (hasReservation) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "student already has an active reservation or ride");
        }
        if (holdTimeout == null || holdTimeout.isNegative() || holdTimeout.isZero()) {
            throw new IllegalStateException("invalid reservation hold timeout configuration");
        }
        String pickupCode = "%06d".formatted(secureRandom.nextInt(1_000_000));
        String reservationId = "res_" + UUID.randomUUID();
        Reservation reservation = new Reservation(reservationId, student.id(), vehicleId, request.startsAt(), request.endsAt(),
                now.plus(holdTimeout), ReservationStatus.ACTIVE, sha256(pickupCode));
        long expectedVersion = dataStore.version();
        boolean committed = stateCoordinator.commit(expectedVersion, store -> {
            Vehicle current = store.findById(Vehicle.class, vehicleId).orElse(null);
            if (current == null || !current.isSearchable(now, telemetryFreshness, minBatteryPct)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "vehicle is no longer available");
            }
            boolean alreadyReserved = store.findAll(Reservation.class).stream()
                    .anyMatch(existing -> existing.isOwnedBy(student)
                            && existing.status() == ReservationStatus.ACTIVE
                            && existing.isActiveAt(now));
            boolean rideStarted = store.findAll(Ride.class).stream()
                    .anyMatch(ride -> student.accountId().equals(ride.accountId())
                            && ride.status() == RideStatus.ACTIVE);
            boolean pending = store.findAll(StudentOperation.class).stream().anyMatch(op ->
                    op.status() == StudentOperation.Status.PENDING && (op.studentId().equals(student.id()) || op.vehicleId().equals(vehicleId)));
            if (alreadyReserved || rideStarted || pending) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "student already has an active reservation or ride");
            }
            store.put(copyWithStatus(current, VehicleStatus.RESERVED));
            store.put(reservation);
            updateSpaceHold(current, reservation.id(), true);
        });
        if (!committed) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "state changed; retry reservation");
        }
        return new BookingResponseDTO(reservationId, vehicle.id(), vehicle.hubId(), reservation.status(), pickupCode,
                reservation.startAt(), reservation.endAt(), reservation.holdUntil(), now);
    }

    public void cancelReservation(String bearerToken, String reservationId) {
        Student student = new StudentAccess(dataStore, clock).fromToken(bearerToken);
        Reservation reservation = reservationDataStore.findById(reservationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "reservation not found"));
        if (!reservation.isOwnedBy(student)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "reservation belongs to another student");
        }
        Instant now = clock.instant();
        if (!reservation.isActiveAt(now)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "reservation is no longer active");
        }
        vehicleDataStore.findById(reservation.vehicleId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "reserved vehicle no longer exists"));
        long expectedVersion = dataStore.version();
        boolean committed = stateCoordinator.commit(expectedVersion, store -> {
            Reservation currentReservation = store.findById(Reservation.class, reservationId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                            "reservation changed"));
            Vehicle currentVehicle = store.findById(Vehicle.class, reservation.vehicleId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                            "vehicle changed"));
            if (currentReservation.status() != ReservationStatus.ACTIVE
                    || !currentReservation.isOwnedBy(student)
                    || currentVehicle.status() != VehicleStatus.RESERVED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "reservation changed");
            }
            if (pendingPickup(reservationId)) throw StudentState.conflict("Pickup awaiting device confirmation");
            Reservation cancelled = currentReservation.cancel(now);
            store.put(cancelled);
            updateSpaceHold(currentVehicle,reservationId,false);
            VehicleStatus restoredStatus = releasedStatus(currentVehicle,now);
            store.put(copyWithStatus(currentVehicle, restoredStatus));
        });
        if (!committed) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "state changed; retry cancellation");
        }
    }

    public void expireReservation(String reservationId) {
        Reservation reservation = reservationDataStore.findById(reservationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "reservation not found"));
        Instant now = clock.instant();
        if (reservation.status() != ReservationStatus.ACTIVE
                || reservation.holdUntil() == null || reservation.holdUntil().isAfter(now)) {
            return;
        }
        long expectedVersion = dataStore.version();
        boolean committed = stateCoordinator.commit(expectedVersion, store -> {
            Reservation currentReservation = store.findById(Reservation.class, reservationId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "reservation not found"));
            if (currentReservation.status() != ReservationStatus.ACTIVE
                    || currentReservation.holdUntil() == null
                    || currentReservation.holdUntil().isAfter(now)) {
                return;
            }
            if (pendingPickup(reservationId)) return;
            store.put(currentReservation.withStatus(ReservationStatus.EXPIRED));
            Vehicle currentVehicle = store.findById(Vehicle.class,
                    currentReservation.vehicleId()).orElse(null);
            boolean anotherActiveHold = store.findAll(Reservation.class).stream()
                    .anyMatch(other -> !other.id().equals(currentReservation.id())
                            && other.vehicleId().equals(currentReservation.vehicleId())
                            && other.status() == ReservationStatus.ACTIVE
                            && other.holdUntil() != null && other.holdUntil().isAfter(now));
            if (currentVehicle != null && currentVehicle.status() == VehicleStatus.RESERVED
                    && !anotherActiveHold) {
                updateSpaceHold(currentVehicle,reservationId,false);
                VehicleStatus restoredStatus = releasedStatus(currentVehicle,now);
                store.put(copyWithStatus(currentVehicle, restoredStatus));
            }
        });
        if (!committed) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "state changed; retry reservation expiry");
        }
    }

    private VehicleStatus releasedStatus(Vehicle vehicle,Instant now) {
        if (copyWithStatus(vehicle, VehicleStatus.AVAILABLE).isSearchable(now,telemetryFreshness,minBatteryPct)) return VehicleStatus.AVAILABLE;
        return vehicle.batteryPct() != null && vehicle.batteryPct() >= 0 && vehicle.batteryPct() < minBatteryPct
                ? VehicleStatus.LOW_BATTERY : VehicleStatus.MAINTENANCE;
    }
    private void updateSpaceHold(Vehicle vehicle,String reservationId,boolean acquire) {
        if (vehicle.parkingSpaceId() == null) return;
        dataStore.findById(ParkingSpace.class,vehicle.parkingSpaceId()).ifPresent(space -> {
            if (acquire && space.vehicleReservationId() != null) throw StudentState.conflict("Dock already holds a reservation");
            if (acquire || reservationId.equals(space.vehicleReservationId())) dataStore.put(space.vehicleHold(acquire ? reservationId : null));
        });
    }
    private boolean pendingPickup(String reservationId) {
        return dataStore.findAll(StudentOperation.class).stream().anyMatch(op -> reservationId.equals(op.reservationId())
                && op.status() == StudentOperation.Status.PENDING);
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private Vehicle copyWithStatus(Vehicle vehicle, VehicleStatus status) {
        return new Vehicle(vehicle.vehicleId(), vehicle.hubId(), vehicle.parkingSpaceId(),
                vehicle.vehicleType(), vehicle.batteryPct(), vehicle.batteryCapacityKwh(),
                vehicle.maxChargingPowerKw(), status, vehicle.telemetryAt(),
                vehicle.version() + 1, vehicle.ownership(), vehicle.ownerAccountId());
    }
}
