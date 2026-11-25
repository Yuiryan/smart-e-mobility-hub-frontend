# UC-04 — Đặt trước vị trí đỗ xe


## Mục tiêu và luồng

Tìm các ô phù hợp cho toàn bộ khoảng thời gian yêu cầu. Việc tìm kiếm chỉ trả ứng viên, chưa giữ chỗ. Khi sinh viên chọn ô, backend phải kiểm tra lại khả năng cấp chỗ trong giao dịch ghi; xung đột thì từ chối, không cấp lượt giữ. Hủy hoặc hết hạn phải thu hồi lượt và giải phóng đúng ô; nếu xe đã vào bãi thì giữ trạng thái đang sử dụng.

## Ranh giới triển khai

`IdentityService` và `StateCoordinator` là thành phần dùng chung; tài liệu này mô tả điểm gọi, không định nghĩa lại implementation của chúng.

## Mô hình lớp và phương thức

Nguồn thiết kế: `UC04/UC04.drawio` và `UC04/UC04_Method_Description.docx`. Dấu `?` biểu thị giá trị tùy chọn. Phạm vi của mỗi lớp bên dưới là phần xuất hiện trong UC04.

### ParkingReservationView «boundary»

- `+submitRequest(request: ParkingRequest): void` — Gửi thông tin tìm chỗ đỗ sau khi sinh viên hoàn thành biểu mẫu.
- `+selectSpace(spaceId: ID): void` — Tiếp nhận lựa chọn của sinh viên từ danh sách chỗ phù hợp.
- `+showReservationCode(reservation: ParkingReservation): void` — Hiển thị thông tin đến nhận chỗ.

### ParkingController «controller»

- `+findAvailableSpaces(request: ParkingRequest): List<ParkingSpace>` — Tiếp nhận yêu cầu findAvailableSpaces qua REST và trả kết quả cho trình duyệt.
- `+reserveSpace(request: ParkingRequest, spaceId: ID): ParkingReservation` — Tiếp nhận yêu cầu reserveSpace qua REST và trả kết quả cho trình duyệt.
- `+cancelReservation(reservationId: ID): void` — Tiếp nhận yêu cầu cancelReservation qua REST và trả kết quả cho trình duyệt.

### ParkingReservationService «service»

- `+findAvailableSpaces(studentId: ID, request: ParkingRequest): List<ParkingSpace>` — Tìm các vị trí phù hợp trong toàn bộ khoảng thời gian yêu cầu. Trả các ứng viên. Nếu hết chỗ, thông báo và gợi ý Hub lân cận. Lỗi giờ hoặc có lượt giữ khác được trả về để sinh viên xử lý.
- `+reserveSpace(studentId: ID, request: ParkingRequest, spaceId: ID): ParkingReservation` — Cấp một lượt giữ cho vị trí đã chọn. Kiểm tra lại lượt giữ của sinh viên và khả năng cấp chỗ trong cùng giao dịch ghi. Thành công chuyển chỗ sang Đã đặt. Xung đột thì từ chối và không cấp thêm mã.
- `+cancelReservation(studentId: ID, reservationId: ID): void` — Hủy lượt giữ trước khi sinh viên đưa xe vào bãi. Thu hồi mã và giải phóng đúng vị trí nếu đủ điều kiện. Gửi lại yêu cầu hủy không làm thay đổi lượt của người khác.
- `+expireReservation(reservationId: ID, now: DateTime): void` — Thu hồi lượt giữ đã quá hạn. Nếu quá hạn, lượt còn hiệu lực và xe chưa vào bãi, thu hồi mã, giải phóng chỗ và gửi thông báo. Nếu xe đã vào bãi, giữ trạng thái Đang sử dụng.

### HubDataStore «interface»

- `+findById(hubId: ID): Optional<MobilityHub>` — Tra cứu HubDataStore theo định danh bản ghi.
- `+save(hub: MobilityHub): void` — Lưu đối tượng đã được bên gọi kiểm tra theo định danh của nó.

### ParkingSpaceDataStore «interface»

- `+findById(spaceId: ID): Optional<ParkingSpace>` — Tra cứu ParkingSpaceDataStore theo định danh bản ghi.
- `+findByHub(hubId: ID): List<ParkingSpace>` — Tra cứu ParkingSpaceDataStore theo Hub chứa tài nguyên.
- `+save(space: ParkingSpace): void` — Lưu đối tượng đã được bên gọi kiểm tra theo định danh của nó.

### ParkingReservationDataStore «interface»

- `+findById(reservationId: ID): Optional<ParkingReservation>` — Tra cứu ParkingReservationDataStore theo định danh bản ghi.
- `+findActiveByStudent(studentId: ID): Optional<ParkingReservation>` — Tra cứu ParkingReservationDataStore theo sinh viên và trạng thái đang hoạt động.
- `+findOverlapping(spaceId: ID, startAt: DateTime, endAt: DateTime): List<ParkingReservation>` — So sánh thời điểm bắt đầu và kết thúc với các khoảng đã giữ của cùng tài nguyên. Hai khoảng chạm biên không giao nhau.
- `+save(reservation: ParkingReservation): void` — Lưu đối tượng đã được bên gọi kiểm tra theo định danh của nó.

### MobilityHub «entity»

Thuộc tính: `-hubId: ID`, `-name: String`, `-address: String`, `-location: GeoPoint`, `-serviceRadiusKm: double`, `-parkingCapacity: int`, `-chargingCapacityKW: double`, `-expectedIncoming: int`, `-expectedOutgoing: int`.

### ParkingSpace «entity»

Thuộc tính: `-spaceId: ID`, `-hubId: ID`, `-vehicleType: VehicleType`, `-status: ParkingSpaceStatus`, `-occupiedVehicleId: ID?`, `-vehicleReservationId: ID?`, `-parkingReservationId: ID?`.

### ParkingReservation «entity»

Thuộc tính: `-reservationId: ID`, `-studentId: ID`, `-spaceId: ID`, `-startAt: DateTime`, `-endAt: DateTime`, `-holdDeadline: DateTime`, `-status: ParkingReservationStatus`.

- `+isActiveAt(now: DateTime): Boolean` — Đối chiếu trạng thái và hạn hiệu lực với thời điểm truyền vào.

### IdentityService «service»

- `+authenticate(studentId: ID): AuthenticationResult` — Đối chiếu thông tin phiên với tài khoản đang hoạt động và kiểm tra vai trò mà thao tác yêu cầu.
- `+verifyPermission(subjectId: ID, action: String): Boolean` — Đối chiếu quyền của tác nhân đã xác thực với hành động và tài nguyên đích.

### StateCoordinator «service»

- `+commit(expectedVersions: Map<ID,long>, changes: ChangeSet, events: List<Event>): CommitResult` — Kiểm tra các phiên bản kỳ vọng trong vùng đồng bộ. Ghi toàn bộ thay đổi liên quan và chỉ công bố trạng thái mới khi lưu thành công.

### ParkingRequest «DTO»

Thuộc tính: `+hubId: ID`, `+vehicleType: VehicleType`, `+startTime: DateTime`, `+endTime: DateTime`.

### Quan hệ với thực thể

- `HubDataStore` → `MobilityHub` (stores / reads).
- `ParkingSpaceDataStore` → `ParkingSpace` (stores / reads).
- `ParkingReservationDataStore` → `ParkingReservation` (stores / reads).
- `MobilityHub` → `ParkingSpace` (contains · 1 / 0..*).
- `ParkingSpace` → `ParkingReservation` (history · 1 / 0..*).
