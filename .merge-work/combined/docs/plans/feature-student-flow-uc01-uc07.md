# Student Flow UC-01–UC-07 Implementation Plan

## Current state before implementation
- UC-01 already has `SearchController`/`HubSearchController`, `HubSearchService`, geographic search, eligibility filtering, and the API response DTOs. It needs a conformance review against Submission 3 rather than replacement.
- UC-02 has partial `VehicleDetailsController`, `VehicleLookupService`, and `VehicleDetails`; lookup currently reads only the in-memory `Vehicle` and does not call the specified `IoTGateway.readVehicleStatus`.
- UC-03 has partial `BookingController`, `ReservationService`, reservation persistence adapter, and request/response DTOs. Its entry points do not yet fully match the Submission 3 request/service signatures, and reservation expiry is not wired to a scheduler.
- UC-04 through UC-07 do not yet have their Student Flow controllers/services.
- Existing common entities, `InMemoryDataStore`, `StateCoordinator`, and snapshot persistence are shared foundations. They must be reused; implementation must not bypass `StateCoordinator.commit(...)` for mutations.

## Goal
- Implement the backend-owned Student Flow behavior in UC-01–UC-07 to match the class names, method responsibilities, state transitions, and failure behavior defined by the paired UC01–UC07 Draw.io diagrams and Method Description documents in `Smart_E_Mobility_Hub_UML`.
- Keep all Student Flow mutations atomic and safe under concurrent requests.
- Integrate with shared Identity/IoT/scheduler classes only through their agreed interfaces when those classes are available.

## Non-goals and ownership boundaries
- Do not implement or modify UC-00 `IdentityService`/authentication internals, `IoTGateway`, `SimulatorAdapter`, `IoTSimulator`, or the shared `JobScheduler`; those are assigned to other members in `Task.txt`.
- Do not implement Operator use cases UC-08–UC-14 or Frontend screens.
- Do not invent or default missing IoT readings. If a dependency is not present, preserve explicit missing/unavailable outcomes and leave the integration point ready for the owning member.
- Do not add third-party dependencies.
- No new test source files per the user's earlier instruction; run available verification commands where the environment permits.

## Design decisions
- Preserve the class and operation names from Submission 3 where they belong to Student Flow:
  - UC-01: `SearchController.searchNearbyHubs`, `HubSearchService.findSuitableHubs`.
  - UC-02: `VehicleDetailsController.getVehicleDetails`, `VehicleDetailsController.onVehicleReserved`, `VehicleLookupService.getVehicleDetails`, `VehicleLookupService.onVehicleReserved`.
  - UC-03: `BookingController.createReservation`, `BookingController.cancelReservation`, `ReservationService.confirmReservation`, `validateEligibility`, `issuePickupCode`, `reserveVehicleAtomically`, `cancelReservation`, `expireReservation`.
  - UC-04: `ParkingController`, `ParkingReservationService` with search/hold/cancel/expire operations.
  - UC-05: `PickupController`, `VehiclePickupService.processScannedCode`, credential verification, unlock request, post-ACK ride creation, finalization, and failure/reconciliation operations.
  - UC-06: `ReturnController`, `VehicleReturnService` with active-ride validation, lock-at-space request, ACK confirmation, and atomic completion.
  - UC-07: `ChargingController`, `ChargingRequestService`, and `AllocationService` for estimate, eligibility, allocation, confirmation, and cancellation.
- Session identity is derived from the authenticated request context; never trust a student ID supplied by the client as proof of identity. Keep the assigned `IdentityService` implementation outside this change.
- Telemetry-dependent operations require a reading with observation time and explicit quality. A stale/missing reading cannot establish that a vehicle is rentable or safely returned.
- UC-03 hold is 15 minutes, rentable battery minimum is 20%, telemetry freshness is 10 minutes; use central configuration/domain values, not inline literals.
- Pickup success and ride creation occur only after a matching IoT `ACK_SUCCESS`. Timeout/unknown outcomes are pending reconciliation and must not create an active ride.
- Return success occurs only after a matching lock acknowledgement containing final battery data. The vehicle becomes `LOW_BATTERY` below 20%; otherwise it may become `AVAILABLE` only with valid fresh telemetry and no maintenance condition.
- Reservation/space/allocation, vehicle, and ride state changes go through `StateCoordinator.commit(expectedVersion, change)`; a conflict must leave all related state unchanged.
- Expiration operations are idempotent. The service operation belongs to Student Flow; invocation by the shared scheduler remains the other member's integration responsibility.

## Files affected
- Student Flow controllers/services/DTOs and student-owned store adapters under `src/main/java/vn/edu/bku/smart_emobility_backend/` for UC-01–UC-07.
- Existing Student Flow files: `HubSearchController`, `HubSearchService`, `VehicleDetailsController`, `VehicleLookupService`, `BookingController`, `ReservationService`, associated DTOs, `VehicleDataStore`, `InMemoryVehicleDataStore`, `ReservationDataStore`, and `InMemoryReservationDataStore`.
- Student-facing contract sections for UC-01–UC-07 in `docs/03-api-contract.md`.
- `docs/04-data-schema.md` only if implementing the already-approved Submission 3 data requires a Java/schema mismatch to be resolved.
- `src/main/resources/application.properties` for centralized Student Flow thresholds/timeouts that are not already configured.
- This plan document.

### Documentation alignment

The FE-approved contract in `docs/03-api-contract.md` defines the public JSON and route names. Keep the UML identifiers inside the Student domain. UC-01 returns hub coordinates, available spaces and vehicle type. UC-03 reads student identity from the session and maps startsAt/endsAt to the domain window. UC-04 resolves compatibility from the selected space and the student's vehicle when known. UC-06 derives maintenance from vehicle state or recorded incidents. UC-07 computes the scheduled end for each candidate port from its effective power and the requested target. Student-only service methods remain available for composition.

### Student implementation files
Student services and HTTP controllers cover parking, pickup, return, charging and allocation. StudentAccess resolves the existing session and Student profile. StudentDevicePort is a consumer interface supplied by the integration member at assembly time. A persisted StudentOperation records resource claims and outcomes before device I/O. StoreSnapshot includes pending operations for recovery. Student endpoint advice returns the standard error envelope. Student service tests exercise the flows and boundary cases.

## Implementation steps
1. Reconcile current UC-01–UC-03 names/signatures and behavior with each UC diagram and Method Description and inspect shared types before changing any Student Flow source.
2. Complete UC-01 search request validation, distance ordering, rentability filtering, and telemetry quality labeling while preserving its existing endpoint contract.
3. Complete UC-02 vehicle detail lookup and reservation refresh behavior. Call the provided IoT gateway when available; retain an explicit missing/unavailable reading if the gateway is absent or fails. Do not create IoT-owned classes.
4. Complete UC-03 eligibility (active verified student, no active reservation/ride, valid requested window, available shared vehicle, battery/telemetry), one-time hashed pickup credential, atomic reservation, owner-only cancellation, and idempotent expiration.
5. Implement UC-04 time-window overlap search, revalidation inside the atomic hold, owner-only cancel, and idempotent timeout release.
6. Implement UC-05 QR/pickup credential verification, requestId idempotency, and matching IoT ACK workflow; never create a Ride on rejection or unknown timeout.
7. Implement UC-06 active ride/ownership/space checks, lock command, ACK/final battery validation, and atomic ride/vehicle/space update.
8. Implement UC-07 charging-time estimate, target/input validation, active schedule overlap and hub power checks, allocation on confirmation, and safe cancellation.
9. Update student API/data documentation to exactly describe implemented request/response fields and error outcomes. Record shared-member integration points without changing their modules.

## Acceptance criteria
- Class names and public method responsibilities for every implemented Student UC align with Submission 3 sections E.2–E.8.
- UC-01/02 never mark a vehicle rentable from stale, future-dated, or missing telemetry; UC-02 distinguishes business status from telemetry quality.
- UC-03 accepts the 20% battery boundary, rejects below threshold, returns a one-time pickup credential, stores only its hash, prevents duplicate active student holds/rides, and commits vehicle/reservation together.
- UC-04 rejects overlapping active intervals on the same space; adjacent intervals do not overlap; expiry/cancel releases only the matching hold.
- UC-05 repeated `requestId` returns the same result; only valid credential plus matching unlock ACK creates exactly one Ride.
- UC-06 does not finish a ride without matching lock ACK and valid final battery; wrong-owner/non-active ride requests are rejected.
- UC-07 estimates from vehicle/charger parameters, rejects invalid target/window and conflicting capacity, and records/releases allocations atomically.
- No Student Flow state mutation bypasses `StateCoordinator.commit(...)`; shared Identity/IoT/JobScheduler classes remain untouched.
- API and schema documents match the DTOs/entities after implementation.

## Điểm ghép với các thành viên khác

- UC-03 đến UC-07 lấy phiên, tài khoản STUDENT đang hoạt động và hồ sơ Student đã xác minh từ kho chung. Thành viên phụ trách UC-00 cung cấp các bản ghi này.
- UC-02 dùng số đo có thời điểm quan sát từ adapter hoặc snapshot đã lưu. UC-05 và UC-06 dùng StudentDevicePort để gửi lệnh và đối soát bằng commandId. Adapter xác minh nguồn ACK và trả UNKNOWN khi mất kết nối. Lần thử lại tra kết quả bằng cùng khóa.
- UC-07 dùng StudentChargingPort để đọc trạng thái Hub, giữ hoặc nhả cổng theo requestId. Adapter xử lý lặp cùng requestId một lần và trả UNKNOWN khi chưa rõ kết quả. Backend giữ allocation trong thời gian đối soát.
- JobScheduler của nhóm hệ thống gọi ReservationService.expireReservation và ParkingReservationService.expireReservation cho lượt đến hạn. Các service kiểm tra chủ lượt giữ, thời hạn và lệnh đang chờ trước khi thu hồi.

## Verification
- Review each Student Flow use case against its Submission 3 method sequence and assigned class list.
- `git diff --check`.
- `./mvnw.cmd test` and `./mvnw.cmd clean package -DskipTests=false` when Maven environment is usable. No new test source files are planned per user instruction.
