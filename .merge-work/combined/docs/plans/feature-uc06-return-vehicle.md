# UC-06 — Trả phương tiện


## Mục tiêu và luồng

Đối chiếu chuyến đang mở, xe và ô đỗ. Chỉ hoàn tất Ride khi nhận ACK khóa hợp lệ có đúng xe/ô và có pin cuối chuyến. Nếu hết chỗ, sai ô, hoặc mất kết nối thì Ride vẫn mở. Sau xác nhận hợp lệ, cập nhật xe, chỗ, lượt giữ liên quan và Ride đồng bộ. Trạng thái xe: có báo sự cố → `MAINTENANCE`; không có sự cố và pin <20% → `LOW_BATTERY`; pin từ 20% → `AVAILABLE`.

## Ranh giới triển khai

`IoTGateway`, `IdentityService` và `StateCoordinator` là shared dependencies. Return service gọi contract đã thống nhất; tài liệu này không định nghĩa lại implementation của các class đó.

## Mô hình lớp và phương thức

Nguồn thiết kế: `UC06/UC06.drawio` và `UC06/UC06_Method_Description.docx`. Dấu `?` biểu thị giá trị tùy chọn. Phạm vi của mỗi lớp bên dưới là phần xuất hiện trong UC06.

### VehicleReturnView «boundary»

- `+submitReturn(request: ReturnRequest): void` — Gửi yêu cầu trả xe từ giao diện sinh viên.
- `+showReturnResult(result: ReturnResult): void` — Hiển thị kết quả xử lý trả phương tiện.

### ReturnController «controller»

- `+returnVehicle(request: ReturnRequest): ReturnResult` — Tiếp nhận yêu cầu returnVehicle qua REST và trả kết quả cho trình duyệt.

### VehicleReturnService «service»

- `+returnVehicle(request: ReturnRequest): ReturnResult` — Kiểm tra điều kiện trả xe và điều phối yêu cầu khóa. Kiểm tra chuyến đang mở và ô tiếp nhận, kể cả ô giữ đúng cho sinh viên. Nếu đủ điều kiện, yêu cầu khóa. Khi hết chỗ hoặc mất kết nối, giữ chuyến đang mở và trả lý do.
- `+completeReturn(request: ReturnRequest, confirmation: LockConfirmation): ReturnResult` — Kết thúc chuyến sau khi nhận xác nhận khóa hợp lệ. Khi khóa thành công và có pin cuối, ghi đồng bộ trạng thái xe, chỗ đỗ, lượt giữ liên quan và lịch sử chuyến. Xác nhận trùng không tạo lịch sử thứ hai.
- `+determineVehicleStatus(issueReported: Boolean, batteryLevel: Percentage): VehicleStatus` — Xác định trạng thái xe để ghi khi kết thúc chuyến. Có sự cố trả Đang bảo trì. Nếu không có sự cố, dưới 20% trả Pin yếu, từ 20% trả Khả dụng. Việc ghi trạng thái do completeReturn thực hiện.

### VehicleDataStore «interface»

- `+findById(vehicleId: ID): Optional<Vehicle>` — Tra cứu VehicleDataStore theo định danh bản ghi.
- `+saveVehicle(vehicle: Vehicle): void` — Ghi bản ghi Vehicle theo khóa định danh trong kho dữ liệu.

### ParkingSpaceDataStore «interface»

- `+findById(spaceId: ID): Optional<ParkingSpace>` — Tra cứu ParkingSpaceDataStore theo định danh bản ghi.
- `+save(space: ParkingSpace): void` — Lưu đối tượng đã được bên gọi kiểm tra theo định danh của nó.

### ParkingReservationDataStore «interface»

- `+findById(reservationId: ID): Optional<ParkingReservation>` — Tra cứu ParkingReservationDataStore theo định danh bản ghi.
- `+findActiveByStudent(studentId: ID): Optional<ParkingReservation>` — Tra cứu ParkingReservationDataStore theo sinh viên và trạng thái đang hoạt động.
- `+save(reservation: ParkingReservation): void` — Lưu đối tượng đã được bên gọi kiểm tra theo định danh của nó.

### RideDataStore «interface»

- `+findById(rideId: ID): Optional<Ride>` — Tra cứu RideDataStore theo định danh bản ghi.
- `+findActiveByStudent(studentId: ID): Optional<Ride>` — Tra cứu RideDataStore theo sinh viên và trạng thái đang hoạt động.
- `+save(ride: Ride): void` — Lưu đối tượng đã được bên gọi kiểm tra theo định danh của nó.

### Vehicle «entity»

Thuộc tính: `-vehicleId: ID`, `-hubId: ID?`, `-parkingSpaceId: ID?`, `-vehicleType: VehicleType`, `-batteryPct: double?`, `-batteryCapacityKWh: double`, `-maxChargingPowerKW: double`, `-status: VehicleStatus`, `-telemetryAt: DateTime?`, `-version: long`, `-ownership: VehicleOwnership`, `-ownerAccountId: ID?`.

### ParkingSpace «entity»

Thuộc tính: `-spaceId: ID`, `-hubId: ID`, `-vehicleType: VehicleType`, `-status: ParkingSpaceStatus`, `-occupiedVehicleId: ID?`, `-vehicleReservationId: ID?`, `-parkingReservationId: ID?`.

- `+occupy(vehicleId: ID): void` — Gắn xe vào đúng ô tiếp nhận đã kiểm tra và đánh dấu vị trí đang được sử dụng.

### ParkingReservation «entity»

Thuộc tính: `-reservationId: ID`, `-studentId: ID`, `-spaceId: ID`, `-startAt: DateTime`, `-endAt: DateTime`, `-holdDeadline: DateTime`, `-status: ParkingReservationStatus`.

- `+isActiveAt(now: DateTime): Boolean` — Đối chiếu trạng thái và hạn hiệu lực với thời điểm truyền vào.

### Ride «entity»

Thuộc tính: `-rideId: ID`, `-studentId: ID`, `-vehicleId: ID`, `-reservationId: ID?`, `-requestId: ID`, `-startedAt: DateTime`, `-endedAt: DateTime?`, `-status: RideStatus`.

- `+complete(now: DateTime): void` — Ghi thời điểm kết thúc và chuyển chuyến đang mở sang trạng thái hoàn tất.

### IdentityService «service»

- `+authenticate(studentId: ID): AuthenticationResult` — Đối chiếu thông tin phiên với tài khoản đang hoạt động và kiểm tra vai trò mà thao tác yêu cầu.
- `+verifyPermission(subjectId: ID, action: String): Boolean` — Đối chiếu quyền của tác nhân đã xác thực với hành động và tài nguyên đích.

### IoTGateway «interface»

- `+lockAtSpace(vehicleId: ID, spaceId: ID): LockConfirmation` — Kiểm tra vị trí thực tế trước khi khóa xe.

### StateCoordinator «service»

- `+commit(expectedVersions: Map<ID,long>, changes: ChangeSet, events: List<Event>): CommitResult` — Kiểm tra các phiên bản kỳ vọng trong vùng đồng bộ. Ghi toàn bộ thay đổi liên quan và chỉ công bố trạng thái mới khi lưu thành công.

### ReturnRequest «DTO»

Thuộc tính: `+studentId: ID`, `+rideId: ID`, `+spaceId: ID`, `+issueReported: Boolean`, `+requestId: ID`.

### LockConfirmation «DTO»

Thuộc tính: `+vehicleId: ID`, `+spaceId: ID`, `+success: Boolean`, `+batteryLevel: Percentage?`, `+commandId: ID`.

### ReturnResult «DTO»

Thuộc tính: `+success: Boolean`, `+reason: String?`, `+duration: Duration?`.

### Quan hệ với thực thể

- `VehicleDataStore` → `Vehicle` (stores / reads).
- `ParkingSpaceDataStore` → `ParkingSpace` (stores / reads).
- `ParkingReservationDataStore` → `ParkingReservation` (stores / reads).
- `RideDataStore` → `Ride` (stores / reads).
- `ParkingSpace` → `ParkingReservation` (history · 1 / 0..*).
- `Vehicle` → `Ride` (history · 1 / 0..*).
- `ParkingSpace` → `Vehicle` (occupancy · 0..1 / 0..1).
