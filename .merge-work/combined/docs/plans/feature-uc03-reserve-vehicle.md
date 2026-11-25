# UC-03: Align Reservation Model With UC03 UML And Method Specification

## Current state
- UC-03 has partial controller/service/storage and bearer-session validation. It has no `Student` entity or `StudentDataStore`.
- The DTO uses `startsAt`/`endsAt` and omits `studentId`; UML specifies `studentId`, `startAt`, and `endAt`. The response uses `expiresAt` instead of `holdUntil`.
- Reservation currently refers to an account ID rather than the UML `studentId`; snapshot persistence has no students collection.
- Vehicle updates must use `StateCoordinator.commit`; current coordinator versions the whole snapshot.
- UC03 UML and method description specify `BookingRequestDTO(vehicleId, studentId, startAt, endAt)`, a distinct Student entity, and a response with `reservationId`, `pickupCode`, and `holdUntil`.

## Hợp đồng với FE

POST nhận vehicleId, startsAt và endsAt. Phiên xác thực cung cấp studentId. Response theo mục UC-03 của docs/03-api-contract.md, trong đó expiresAt ánh xạ đến holdUntil. Chữ ký và DTO trong sơ đồ bên dưới mô tả mô hình nội bộ.

## Goal
- Implement the UC03 Student entity/store and map the public DTOs to the entity relationships in the diagram.
- Authenticated students can reserve eligible shared vehicles for `[startAt, endAt)`, receive a one-time pickup code, and cancel their active reservation.
- Enforce one active reservation or ride per student and atomic vehicle/reservation updates.

## Non-goals
- Implement pickup/ride UC-05, QR camera, or frontend.
- Use a client-supplied studentId as proof of identity.
- Add dependencies.

## Design decisions
- Resolve a live, unexpired bearer session to an active STUDENT account and Student profile; derive studentId from that profile.
- `Student.isAuthenticated()` requires active account status and a non-null verification time.
- Check all UC-01 eligibility rules including telemetry timestamp/freshness and battery boundary.
- Generate the code with `SecureRandom`; store only a SHA-256 hash and return raw code only at creation.
- Commit reservation, vehicle status, and version changes together using the snapshot version; stale commits map to conflict.
- Cancellation is limited to the owning student and ACTIVE reservation; it restores the vehicle to AVAILABLE only when safe eligibility still holds, otherwise LOW_BATTERY or MAINTENANCE as applicable.
- Use configured `app.reservation.hold-timeout=PT15M`; no magic timeout.
- Follow UML field names `startAt`, `endAt`, and `holdUntil`; require `endAt` strictly after `startAt`. The 15-minute `holdUntil` is independent of the requested rental window.
- Persist students in `StoreSnapshot`; keep profile and reservation mutations in the same `StateCoordinator.commit` workflow as applicable.

## Files affected
- `src/main/java/vn/edu/bku/smart_emobility_backend/controller/BookingController.java`
- `src/main/java/vn/edu/bku/smart_emobility_backend/service/ReservationService.java`
- `src/main/java/vn/edu/bku/smart_emobility_backend/common/store/ReservationDataStore.java`
- `src/main/java/vn/edu/bku/smart_emobility_backend/common/store/InMemoryReservationDataStore.java`
- `src/main/java/vn/edu/bku/smart_emobility_backend/dto/BookingRequestDTO.java`
- `src/main/java/vn/edu/bku/smart_emobility_backend/dto/BookingResponseDTO.java`
- `src/main/java/vn/edu/bku/smart_emobility_backend/common/domain/Reservation.java`
- `src/main/java/vn/edu/bku/smart_emobility_backend/common/domain/Student.java`
- `src/main/java/vn/edu/bku/smart_emobility_backend/common/domain/AccountStatus.java`
- `src/main/java/vn/edu/bku/smart_emobility_backend/common/store/StudentDataStore.java`
- `src/main/java/vn/edu/bku/smart_emobility_backend/common/store/InMemoryStudentDataStore.java`
- `src/main/java/vn/edu/bku/smart_emobility_backend/common/store/StoreSnapshot.java`
- `src/main/java/vn/edu/bku/smart_emobility_backend/common/store/InMemoryDataStore.java`
- Reservation call sites/tests identified by repository search
- `src/test/java/vn/edu/bku/smart_emobility_backend/service/ReservationServiceTest.java` (or existing UC-03 test files)
- `src/test/java/vn/edu/bku/smart_emobility_backend/controller/VehicleDetailsControllerTest.java` (update the `@WebMvcTest` import to the Spring Boot 4 package so the repository test sources compile)
- `docs/01-domain-rules.md`
- `src/main/java/vn/edu/bku/smart_emobility_backend/common/domain/Vehicle.java`
- `src/main/java/vn/edu/bku/smart_emobility_backend/common/store/StateCoordinator.java`
- `src/main/resources/application.properties`
- `docs/03-api-contract.md`
- `docs/04-data-schema.md`
- `docs/02-architecture.md`

## Implementation
1. Add Student/AccountStatus entities and StudentDataStore backed by the snapshot store.
2. Map the FE request and response to the Reservation model and resolve the Student through the authenticated session.
3. Resolve the bearer session to its Student; implement eligibility and atomic reserve/cancel/expiry flows.
4. Keep pickup code hashed at rest; preserve the requested window and hold deadline across status changes.

## Acceptance criteria
- Eligible vehicle reservation returns 201, a 6-digit code once, and expiry exactly 15 minutes after creation.
- Battery below 20%, stale/missing telemetry, private, non-available, or invalid session requests fail safely.
- Concurrent requests cannot both reserve the same vehicle; second attempt conflicts.
- A student with an active reservation or ride cannot create another reservation.
- Only the owner may cancel an active reservation.
- Missing or invalid session, missing rental times, or `endsAt <= startsAt` are rejected; valid fields round-trip through API and snapshot.

## Verification
- `./mvnw.cmd test`
- `./mvnw.cmd clean package -DskipTests=false`

## Mô hình lớp và phương thức

Nguồn thiết kế: `UC03/UC03.drawio` và `UC03/UC03_Method_Description.docx`. Dấu `?` biểu thị giá trị tùy chọn. Phạm vi của mỗi lớp bên dưới là phần xuất hiện trong UC03.

### ReservationScreen «boundary»

Thuộc tính: `-selectedVehicleId: ID`, `-requestedStartAt: DateTime`, `-requestedEndAt: DateTime`.

- `+selectVehicle(vehicleId: ID): void` — Ghi nhận mã xe người dùng chọn cho thao tác hiện tại.
- `+confirmReservation(): void` — Gửi mã xe và khung thời gian đã chọn đến BookingController để xác nhận lượt đặt.
- `+requestCancellation(reservationId: ID): void` — Gửi mã lượt đặt đến BookingController để kiểm tra quyền hủy của chủ lượt đặt.
- `+displayReservation(result: BookingResponseDTO): void` — Hiển thị mã nhận xe và hạn giữ chỗ từ kết quả đặt thành công.

### BookingController «controller»

Thuộc tính: `-reservationService: ReservationService`.

- `+createReservation(request: BookingRequestDTO): BookingResponseDTO` — Tiếp nhận yêu cầu đặt xe và chuyển cho Service.
- `+cancelReservation(reservationId: ID, studentId: ID): void` — Tiếp nhận yêu cầu cancelReservation qua REST và trả kết quả cho trình duyệt.
- `+mapResultToHttpResponse(result: ServiceResult): ResponseEntity` — Tiếp nhận yêu cầu mapResultToHttpResponse qua REST và trả kết quả cho trình duyệt.

### ReservationService «service»

Thuộc tính: `-holdDuration: Duration`, `-minBatteryPct: double = 20`, `-clock: Clock`.

- `+confirmReservation(request: BookingRequestDTO): BookingResponseDTO` — Điều phối toàn bộ nghiệp vụ tạo lượt đặt xe. 1. Gọi validateEligibility(...) để kiểm tra sinh viên và xe. 2. Tạo đối tượng Reservation ở trạng thái ACTIVE. 3. Tạo pickup code ngẫu nhiên; chỉ lưu hash vào Reservation. 4. Gọi reserveVehicleAtomically(...) để kiểm tra và chuyển xe AVAILABLE → RESERVED trong vùng đồng bộ. 5. Nếu cập nhật xe thất bại, không lưu Reservation và trả lỗi xung đột. 6. Lưu Reservation bằng ReservationDataStore.saveReservation(...). 7. Trả BookingResponseDTO cùng pickupCode và holdUntil.
- `+validateEligibility(studentId: ID, vehicleId: ID, startAt: DateTime, endAt: DateTime): void` — Kiểm tra điều kiện đặt xe trước khi giữ tài nguyên. 1. Kiểm tra tài khoản sinh viên đang ACTIVE và đã xác thực. 2. Kiểm tra sinh viên không có Reservation đang hoạt động. 3. Kiểm tra sinh viên không có Ride đang mở. 4. Kiểm tra startAt < endAt và khoảng thời gian hợp lệ. 5. Đọc trạng thái xe mới nhất; kiểm tra AVAILABLE, pin và telemetry.
- `+issuePickupCode(reservation: Reservation): String` — Tạo mã nhận xe có thời hạn và gắn với Reservation. 1. Sinh credential ngẫu nhiên khó đoán. 2. Tạo hash của credential để lưu trong Reservation. 3. Thiết lập holdUntil theo holdDuration. 4. Trả credential gốc cho response một lần.
- `+reserveVehicleAtomically(vehicleId: ID, reservation: Reservation): Boolean` — Đảm bảo chỉ một request có thể giữ một xe tại cùng thời điểm. 1. Đọc xe mới nhất bằng getVehicleForUpdate(vehicleId). 2. Kiểm tra status vẫn là AVAILABLE và version chưa đổi. 3. Gọi reserveIfAvailable(vehicleId, expectedVersion). 4. Chỉ trả true khi cập nhật trạng thái thành công; request cạnh tranh sẽ nhận false.
- `+cancelReservation(reservationId: ID, studentId: ID): void` — Hủy Reservation theo yêu cầu của đúng chủ sở hữu. 1. Tìm Reservation theo ID. 2. Kiểm tra studentId trùng chủ sở hữu. 3. Kiểm tra trạng thái còn được phép hủy. 4. Chuyển Reservation sang CANCELLED và thu hồi pickup code. 5. Chỉ chuyển xe về AVAILABLE nếu xe vẫn RESERVED cho chính Reservation này.
- `+expireReservation(reservationId: ID): void` — Xử lý Reservation hết thời gian giữ xe; có thể được gọi lặp an toàn. 1. Tìm Reservation. 2. Nếu holdUntil chưa tới hoặc trạng thái không còn ACTIVE thì dừng. 3. Đánh dấu EXPIRED và thu hồi pickup code. 4. Giải phóng xe chỉ khi xe vẫn RESERVED cho Reservation này.

### StudentDataStore «interface»

- `+findById(studentId: ID): Optional<Student>` — Tra cứu StudentDataStore theo định danh bản ghi.

### VehicleDataStore «interface»

- `+findById(vehicleId: ID): Optional<Vehicle>` — Tra cứu VehicleDataStore theo định danh bản ghi.
- `+getVehicleForUpdate(vehicleId: ID): Optional<Vehicle>` — Đọc xe theo mã trong phạm vi cập nhật để dịch vụ kiểm tra trạng thái và phiên bản.
- `+reserveIfAvailable(vehicleId: ID, expectedVersion: long): Boolean` — Cập nhật xe có điều kiện để tránh ghi đè trạng thái mới.

### ReservationDataStore «interface»

- `+findActiveReservation(studentId: ID): Optional<Reservation>` — Tìm Reservation đang hiệu lực của sinh viên.
- `+findById(reservationId: ID): Optional<Reservation>` — Tra cứu ReservationDataStore theo định danh bản ghi.
- `+saveReservation(reservation: Reservation): void` — Ghi bản ghi Reservation theo khóa định danh trong kho dữ liệu.

### RideDataStore «interface»

- `+findById(rideId: ID): Optional<Ride>` — Tra cứu RideDataStore theo định danh bản ghi.
- `+findActiveByStudent(studentId: ID): Optional<Ride>` — Tra cứu RideDataStore theo sinh viên và trạng thái đang hoạt động.

### Student «entity»

Thuộc tính: `-studentId: ID`, `-accountId: ID`, `-name: String`, `-accountStatus: AccountStatus`, `-verifiedAt: DateTime?`.

- `+isAuthenticated(): Boolean` — Kiểm tra tài khoản sinh viên đã xác thực và đang hoạt động.
- `+hasActiveReservation(): Boolean {derived}` — Xét các bản ghi của sinh viên trong dữ liệu đã nạp và trả về việc còn lượt đang hiệu lực.
- `+hasActiveRide(): Boolean {derived}` — Xét các bản ghi của sinh viên trong dữ liệu đã nạp và trả về việc còn lượt đang hiệu lực.

### Vehicle «entity»

Thuộc tính: `-vehicleId: ID`, `-hubId: ID?`, `-parkingSpaceId: ID?`, `-vehicleType: VehicleType`, `-batteryPct: double?`, `-batteryCapacityKWh: double`, `-maxChargingPowerKW: double`, `-status: VehicleStatus`, `-telemetryAt: DateTime?`, `-version: long`, `-ownership: VehicleOwnership`, `-ownerAccountId: ID?`.

- `+isReservable(now: DateTime, freshnessLimit: Duration, minBatteryPct: double): Boolean` — Kiểm tra xe có đủ điều kiện được đặt. 1. Kiểm tra AVAILABLE. 2. Kiểm tra pin >= minBatteryPct. 3. Kiểm tra telemetry mới và không null.
- `+transitionTo(expected: VehicleStatus, next: VehicleStatus): Boolean` — So sánh trạng thái hiện tại với expected. Chỉ chuyển sang next nếu khớp và chuyển trạng thái được phép.

### Reservation «entity»

Thuộc tính: `-reservationId: ID`, `-studentId: ID`, `-vehicleId: ID`, `-startAt: DateTime`, `-endAt: DateTime`, `-holdUntil: DateTime`, `-status: ReservationStatus`, `-pickupCodeHash: String?`.

- `+isActiveAt(now: DateTime): Boolean` — Kiểm tra lượt đặt còn hiệu lực tại một thời điểm. 1. Kiểm tra status == ACTIVE. 2. Kiểm tra now chưa vượt holdUntil và khoảng đặt còn hiệu lực.
- `+revokePickupCode(): void` — Vô hiệu hóa mã nhận xe. 1. Xóa hoặc đánh dấu credential hash không còn hợp lệ.
- `+cancel(now: DateTime): void` — Chuyển lượt đặt đang hoạt động sang trạng thái đã hủy, vô hiệu hóa mã nhận xe gắn với lượt đặt.

### IdentityService «service»

- `+authenticate(studentId: ID): AuthenticationResult` — Đối chiếu thông tin phiên với tài khoản đang hoạt động và kiểm tra vai trò mà thao tác yêu cầu.
- `+verifyPermission(subjectId: ID, action: String): Boolean` — Đối chiếu quyền của tác nhân đã xác thực với hành động và tài nguyên đích.

### DataStore «interface»

- `+readSnapshot(): StateSnapshot` — Chụp trạng thái đã công bố cùng phiên bản để bên gọi đọc một tập dữ liệu nhất quán.
- `+commitAtomically(expectedVersions: Map<ID,long>, snapshot: StateSnapshot): CommitResult` — So sánh các phiên bản kỳ vọng. Nếu khớp, lưu snapshot mới và công bố toàn bộ thay đổi như một giao dịch.

### InMemoryDataStore «repository implementation»

Thuộc tính: `-state: StateSnapshot`, `-maps: ConcurrentHashMap`.

- `+readSnapshot(): StateSnapshot` — Chụp trạng thái đã công bố cùng phiên bản để bên gọi đọc một tập dữ liệu nhất quán.
- `+commitAtomically(expectedVersions: Map<ID,long>, snapshot: StateSnapshot): CommitResult` — So sánh các phiên bản kỳ vọng. Nếu khớp, lưu snapshot mới và công bố toàn bộ thay đổi như một giao dịch.

### JsonStore «persistence»

Thuộc tính: `-path: String = data.json`.

- `+load(): StateSnapshot` — Đọc data.json khi khởi động, giải mã và kiểm tra snapshot trước khi nạp trạng thái vào RAM.
- `+writeAtomically(snapshot: StateSnapshot): void` — Ghi snapshot vào tệp tạm, hoàn tất ghi rồi thay thế data.json. Báo lỗi ghi để giao dịch chưa được xác nhận.

### StateCoordinator «service»

- `+commit(expectedVersions: Map<ID,long>, changes: ChangeSet, events: List<Event>): CommitResult` — Kiểm tra các phiên bản kỳ vọng trong vùng đồng bộ. Ghi toàn bộ thay đổi liên quan và chỉ công bố trạng thái mới khi lưu thành công.

### JobScheduler «background job»

- `+tick(now: DateTime): void` — Dùng thời điểm hiện tại để chạy các tác vụ định kỳ đến hạn.

### BookingRequestDTO «DTO»

Thuộc tính: `+vehicleId: ID`, `+studentId: ID`, `+startAt: DateTime`, `+endAt: DateTime`.

- `+validate(): ValidationResult` — Kiểm tra trường bắt buộc, kiểu dữ liệu và miền giá trị trước khi chuyển yêu cầu sang bước tiếp theo.

### BookingResponseDTO «DTO»

Thuộc tính: `+reservationId: ID`, `+pickupCode: String`, `+holdUntil: DateTime`.

### Quan hệ với thực thể

- `StudentDataStore` → `Student` (stores / reads).
- `VehicleDataStore` → `Vehicle` (stores / reads).
- `ReservationDataStore` → `Reservation` (stores / reads).
- `Student` → `Reservation` (history · 1 / 0..*).
- `Vehicle` → `Reservation` (history · 1 / 0..*).
