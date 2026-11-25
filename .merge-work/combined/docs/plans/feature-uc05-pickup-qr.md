# UC-05 — Nhận xe bằng mã QR


## Mục tiêu và luồng

Xác thực QR và điều kiện nhận xe cho nhánh đã đặt hoặc thuê trực tiếp. `requestId` giữ nguyên khi retry cùng thao tác. Chỉ tạo một chuyến sau ACK mở khóa khớp yêu cầu. IoT timeout/kết quả không rõ phải đánh dấu chờ đối soát, không tạo Ride và không báo thành công.

## Ranh giới triển khai

`IdentityService`, `IoTGateway`, adapter/simulator và `StateCoordinator` là dependency dùng chung được mô tả trong đặc tả. UC-05 chỉ gọi các contract đó; file này không thêm implementation cho thành viên phụ trách các thành phần ấy.

## Mô hình lớp và phương thức

Nguồn thiết kế: `UC05/UC05.drawio` và `UC05/UC05_Method_Description.docx`. Dấu `?` biểu thị giá trị tùy chọn. Phạm vi của mỗi lớp bên dưới là phần xuất hiện trong UC05.

### QRScannerScreen «boundary»

Thuộc tính: `-scannedCode: String`, `-requestId: ID`.

- `+scanQRCode(): String` — Đọc mã QR trên xe từ camera sau khi người dùng cấp quyền, trả chuỗi nhận diện xe.
- `+submitPickupRequest(): void` — Gửi mã QR, phương thức nhận xe và mã yêu cầu đến PickupController. Giữ requestId khi thử lại cùng thao tác.
- `+displayPickupResult(result: PickupResponseDTO): void` — Hiển thị kết quả mở khóa và mã chuyến do backend xác nhận.
- `+displayPickupError(message: String): void` — Hiển thị lý do xử lý thất bại do API hoặc trình duyệt trả về và cho phép thử lại.

### PickupController «controller»

Thuộc tính: `-vehiclePickupService: VehiclePickupService`.

- `+processPickup(request: PickupRequestDTO): PickupResponseDTO` — Tiếp nhận yêu cầu quét QR và trả kết quả nhận xe.
- `+validateRequest(request: PickupRequestDTO): void` — Tiếp nhận yêu cầu validateRequest qua REST và trả kết quả cho trình duyệt.
- `+mapPickupResult(result: PickupResult): ResponseEntity` — Tiếp nhận yêu cầu mapPickupResult qua REST và trả kết quả cho trình duyệt.

### VehiclePickupService «service»

Thuộc tính: `-clock: Clock`, `-idempotencyStore: Map<ID,PickupResult>`.

- `+processScannedCode(request: PickupRequestDTO): PickupResponseDTO` — Điều phối toàn bộ quy trình nhận xe bằng QR. 1. Kiểm tra idempotencyStore hoặc RideDataStore theo requestId; nếu đã xử lý thì trả kết quả cũ. 2. Gọi verifyPickupCredential(...) để tìm Reservation hợp lệ. 3. Gọi requestVehicleUnlock(...) với vehicleId và requestId làm idempotency key. 4. Nếu IoT trả ACK hợp lệ, gọi startRideAfterUnlock(...). 5. Gọi finalizePickup(...) để cập nhật Reservation, Vehicle và ParkingSpace. 6. Lưu kết quả theo requestId và trả response. 7. Nếu IoT timeout không rõ kết quả, đánh dấu PENDING_RECONCILIATION; không tạo Ride và không trả SUCCESS.
- `+verifyPickupCredential(studentId: ID, pickupCode: String, now: DateTime): Reservation` — Xác minh QR thuộc đúng sinh viên và Reservation còn hiệu lực. 1. Hash qrCode để so sánh với pickupCodeHash. 2. Tìm Reservation bằng ReservationDataStore.findCredential(codeHash). 3. Kiểm tra studentId và trạng thái Reservation. 4. Kiểm tra holdUntil chưa hết hạn và mã chưa bị thu hồi.
- `+requestVehicleUnlock(vehicleId: ID, requestId: ID): IoTCommandResult` — Gửi lệnh mở khóa qua IoT Gateway. 1. Gọi iotGateway.unlock(vehicleId, requestId). 2. Kiểm tra chữ ký/nguồn gốc và nội dung ACK theo adapter. 3. Phân loại kết quả thành ACK_SUCCESS, REJECTED hoặc UNKNOWN/TIMEOUT.
- `+startRideAfterUnlock(reservation: Reservation, now: DateTime): Ride` — Tạo Ride sau khi đã xác nhận mở khóa thành công. 1. Tạo Ride gắn studentId, vehicleId và reservationId. 2. Gọi Ride.start(now). 3. Lưu Ride qua RideDataStore.createRideOnce(...).
- `+finalizePickup(reservation: Reservation, ride: Ride): void` — Hoàn tất cập nhật trạng thái miền sau khi mở khóa thành công. 1. Đánh dấu Reservation PICKED_UP và thu hồi pickup code. 2. Chuyển Vehicle RESERVED → IN_USE bằng cập nhật có kiểm tra version. 3. Giải phóng ParkingSpace gắn đúng reservationId. 4. Đảm bảo các cập nhật được thực hiện nhất quán trong một vùng đồng bộ.
- `+reportUnlockFailure(vehicleId: ID, requestId: ID, reason: String): void` — Ghi nhận lỗi khi lệnh mở khóa không thành công hoặc kết quả không rõ. 1. Ghi nhận sự cố và thời điểm. 2. Nếu kết quả UNKNOWN/TIMEOUT, đánh dấu chờ đối soát. 3. Không tạo Ride và không đánh dấu Reservation đã nhận.
- `+pickupDirect(vehicleId: ID, startAt: DateTime, endAt: DateTime, requestId: ID): PickupResponseDTO` — Xác thực QR của xe, kiểm tra xe đủ điều kiện và khoảng sử dụng. Gửi lệnh mở khóa có mã yêu cầu để nhận xe trực tiếp.
- `+startDirectRideAfterUnlock(studentId: ID, vehicleId: ID, now: DateTime, requestId: ID): Ride` — Đối chiếu ACK với xe và lệnh đang chờ. Tạo chuyến một lần theo requestId, cập nhật xe và ô đỗ trong cùng giao dịch.

### IncidentService «service»

- `+recordIncident(deviceId: ID, description: String, source: AlertSource): Incident` — Tiếp nhận cảnh báo hợp lệ và tạo bản ghi Incident cho nhân viên vận hành. Việc ghi nhận không tự động đưa thiết bị vào bảo trì. Trả về Incident mới.

### VehicleDataStore «interface»

- `+findById(vehicleId: ID): Optional<Vehicle>` — Tra cứu VehicleDataStore theo định danh bản ghi.
- `+getVehicleForUpdate(vehicleId: ID): Optional<Vehicle>` — Đọc xe theo mã trong phạm vi cập nhật để dịch vụ kiểm tra trạng thái và phiên bản.
- `+markInUse(vehicleId: ID, expectedVersion: long): Boolean` — Đối chiếu phiên bản của xe rồi chuyển xe sang trạng thái đang sử dụng.

### ReservationDataStore «interface»

- `+findById(reservationId: ID): Optional<Reservation>` — Tra cứu ReservationDataStore theo định danh bản ghi.
- `+findCredential(codeHash: String): Optional<Reservation>` — Tìm lượt đặt ứng với giá trị băm của mã nhận xe.
- `+markPickedUp(reservationId: ID): void` — Đánh dấu lượt đặt đã được sử dụng sau khi hoàn thành nhận xe.

### ParkingSpaceDataStore «interface»

- `+findById(spaceId: ID): Optional<ParkingSpace>` — Tra cứu ParkingSpaceDataStore theo định danh bản ghi.
- `+releaseAfterPickup(reservationId: ID): Boolean` — Đối chiếu chủ lượt giữ hoặc xe đang chiếm ô trước khi giải phóng vị trí.

### RideDataStore «interface»

- `+findByRequestId(requestId: ID): Optional<Ride>` — Tra cứu RideDataStore theo mã yêu cầu để nhận diện thao tác đã xử lý.
- `+findById(rideId: ID): Optional<Ride>` — Tra cứu RideDataStore theo định danh bản ghi.
- `+createRideOnce(ride: Ride, requestId: ID): Ride` — Lưu Ride theo cách chống tạo trùng request.

### Student «entity»

Thuộc tính: `-studentId: ID`, `-accountId: ID`, `-name: String`, `-accountStatus: AccountStatus`, `-verifiedAt: DateTime?`.

### Vehicle «entity»

Thuộc tính: `-vehicleId: ID`, `-hubId: ID?`, `-parkingSpaceId: ID?`, `-vehicleType: VehicleType`, `-batteryPct: double?`, `-batteryCapacityKWh: double`, `-maxChargingPowerKW: double`, `-status: VehicleStatus`, `-telemetryAt: DateTime?`, `-version: long`, `-ownership: VehicleOwnership`, `-ownerAccountId: ID?`.

### Reservation «entity»

Thuộc tính: `-reservationId: ID`, `-studentId: ID`, `-vehicleId: ID`, `-startAt: DateTime`, `-endAt: DateTime`, `-holdUntil: DateTime`, `-status: ReservationStatus`, `-pickupCodeHash: String?`.

- `+isActiveAt(now: DateTime): Boolean` — Kiểm tra lượt đặt còn hiệu lực tại một thời điểm. 1. Kiểm tra status == ACTIVE. 2. Kiểm tra now chưa vượt holdUntil và khoảng đặt còn hiệu lực.
- `+isPickupValid(studentId: ID, now: DateTime): Boolean` — Kiểm tra Reservation có được phép nhận bởi sinh viên tại thời điểm hiện tại. 1. So sánh studentId với chủ Reservation. 2. Kiểm tra status == ACTIVE. 3. Kiểm tra pickup code chưa bị thu hồi và now <= holdUntil.
- `+revokePickupCode(): void` — Vô hiệu hóa mã nhận xe. 1. Xóa hoặc đánh dấu credential hash không còn hợp lệ.
- `+markPickedUp(): void` — Đánh dấu lượt đặt đã được sử dụng sau khi hoàn thành nhận xe.

### ParkingSpace «entity»

Thuộc tính: `-spaceId: ID`, `-hubId: ID`, `-vehicleType: VehicleType`, `-status: ParkingSpaceStatus`, `-occupiedVehicleId: ID?`, `-vehicleReservationId: ID?`, `-parkingReservationId: ID?`.

- `+releaseForReservation(reservationId: ID): Boolean` — Chỉ giải phóng vị trí đỗ được gắn với lượt đặt tương ứng. 1. Kiểm tra parkingSpace.reservationId trùng reservationId. 2. Kiểm tra trạng thái vị trí là RESERVED. 3. Chuyển trạng thái sang AVAILABLE và xóa reservationId.
- `+releaseIfOwned(vehicleId: ID): void` — Đối chiếu chủ lượt giữ hoặc xe đang chiếm ô trước khi giải phóng vị trí.

### Ride «entity»

Thuộc tính: `-rideId: ID`, `-studentId: ID`, `-vehicleId: ID`, `-reservationId: ID?`, `-requestId: ID`, `-startedAt: DateTime`, `-endedAt: DateTime?`, `-status: RideStatus`.

- `+start(now: DateTime): void` — Đánh dấu thời điểm bắt đầu chuyến đi. 1. Thiết lập startedAt = now. 2. Chuyển status sang ACTIVE.

### IdentityService «service»

- `+authenticate(studentId: ID): AuthenticationResult` — Đối chiếu thông tin phiên với tài khoản đang hoạt động và kiểm tra vai trò mà thao tác yêu cầu.
- `+verifyPermission(subjectId: ID, action: String): Boolean` — Đối chiếu quyền của tác nhân đã xác thực với hành động và tài nguyên đích.

### IoTGateway «interface»

- `+unlock(vehicleId: ID, idempotencyKey: ID): IoTCommandResult` — Trừu tượng hóa lệnh mở khóa với thiết bị thật hoặc simulator.
- `+send(command: DeviceCommand): DeviceDispatchResult` — Gửi lệnh nhận diện bằng commandId đến bộ giả lập.
- `+getResult(commandId: ID): DeviceResponse` — Tìm kết quả lệnh thiết bị theo commandId. Giữ trạng thái chờ nếu chưa nhận được ACK.

### SimulatorAdapter «adapter»

- `+send(command: DeviceCommand): DeviceDispatchResult` — Gửi lệnh nhận diện bằng commandId đến bộ giả lập.
- `+getResult(commandId: ID): DeviceResponse` — Tìm kết quả lệnh thiết bị theo commandId. Giữ trạng thái chờ nếu chưa nhận được ACK.
- `+emitTelemetry(): void` — Phát mẫu pin, vị trí và trạng thái cổng sạc có thời điểm quan sát từ mô phỏng tới backend.

### IoTSimulator «simulator»

Thuộc tính: `-tickInterval: Duration = 3..5 seconds`.

- `+tick(now: DateTime): void` — Tiến đồng hồ mô phỏng tới thời điểm now, cập nhật dữ liệu pin, GPS và cổng sạc cho chu kỳ kế tiếp.
- `+handleCommand(command: DeviceCommand): DeviceResponse` — Đối chiếu đích và trạng thái của lệnh mô phỏng, thực hiện tác động hợp lệ rồi trả ACK mang đúng mã lệnh.

### StateCoordinator «service»

- `+commit(expectedVersions: Map<ID,long>, changes: ChangeSet, events: List<Event>): CommitResult` — Kiểm tra các phiên bản kỳ vọng trong vùng đồng bộ. Ghi toàn bộ thay đổi liên quan và chỉ công bố trạng thái mới khi lưu thành công.

### PickupRequestDTO «DTO»

Thuộc tính: `+studentId: ID`, `+qrCode: String`, `+requestId: ID`, `+pickupMode: PickupMode`, `+pickupCode: String?`, `+reservationId: ID?`, `+startAt: DateTime?`, `+endAt: DateTime?`.

- `+validate(): ValidationResult` — Kiểm tra trường bắt buộc, kiểu dữ liệu và miền giá trị trước khi chuyển yêu cầu sang bước tiếp theo.

### PickupResponseDTO «DTO»

Thuộc tính: `+rideId: ID?`, `+pickupStatus: PickupStatus`, `+message: String`.

### DeviceResponse «value object»

Thuộc tính: `+commandId: ID`, `+status: CommandStatus`, `+observedAt: DateTime`, `+reason: String?`.

### Quan hệ với thực thể

- `VehicleDataStore` → `Vehicle` (stores / reads).
- `ReservationDataStore` → `Reservation` (stores / reads).
- `ParkingSpaceDataStore` → `ParkingSpace` (stores / reads).
- `RideDataStore` → `Ride` (stores / reads).
- `Student` → `Reservation` (history · 1 / 0..*).
- `Vehicle` → `Reservation` (history · 1 / 0..*).
- `Student` → `Ride` (history · 1 / 0..*).
- `Vehicle` → `Ride` (history · 1 / 0..*).
- `Reservation` → `Ride` (optional for direct pickup · 0..1 / 0..1).
- `ParkingSpace` → `Vehicle` (occupancy · 0..1 / 0..1).
- `Reservation` → `ParkingSpace` (pickup hold · 0..1 / 0..1).
