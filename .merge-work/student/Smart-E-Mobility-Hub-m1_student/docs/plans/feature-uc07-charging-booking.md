# UC-07 — Đăng ký và ước tính thời gian sạc


## Mục tiêu và luồng

Ước tính thời lượng sạc đến pin mục tiêu. Khi xác nhận booking, kiểm tra xe, pin mục tiêu, cổng và khung giờ; kiểm tra công suất Hub, giữ khoảng sạc rồi xác nhận request chỉ sau khi lưu thành công. Kiểm tra tính khả dụng bao gồm ô đỗ, cổng, lịch đặt và trạng thái kết nối IoT. Hủy phải kiểm tra đúng chủ/trạng thái, giải phóng allocation và cập nhật request cùng giao dịch.

## Ranh giới triển khai

`IdentityService`, `ChargingIoTGateway`, `StateCoordinator` và `AllocationService` được mô tả là các dependency dùng chung. Tài liệu UC-07 chỉ ghi cách `ChargingRequestService` gọi chúng; không định nghĩa lại implementation của các class này.

## Mô hình lớp và phương thức

Nguồn thiết kế: `UC07/UC07.drawio` và `UC07/UC07_Method_Description.docx`. Dấu `?` biểu thị giá trị tùy chọn. Phạm vi của mỗi lớp bên dưới là phần xuất hiện trong UC07.

### ChargingBookingView «boundary»

- `+submitRequest(vehicleId: ID, hubId: ID, startTime: DateTime, endTime: DateTime, targetBatteryPct: double): void` — Gửi yêu cầu sạc gồm xe, Mobility Hub và khoảng thời gian sinh viên chọn. Khung giờ không khả dụng cần hiển thị mờ và không chọn được.
- `+showResult(result: BookingResult): void` — Hiển thị kết quả đăng ký, từ chối, lỗi kết nối hoặc gợi ý khung giờ/phương án khác.
- `+cancelBooking(requestId: ID): void` — Gửi yêu cầu hủy lịch sạc theo mã yêu cầu.

### ChargingController «controller»

- `+bookCharging(vehicleId: ID, hubId: ID, startTime: DateTime, endTime: DateTime, targetBatteryPct: double): BookingResult` — Tiếp nhận yêu cầu bookCharging qua REST và trả kết quả cho trình duyệt.
- `+estimateChargingTime(vehicleId: ID, portId: ID, targetBatteryPct: double): Integer` — Tiếp nhận yêu cầu estimateChargingTime qua REST và trả kết quả cho trình duyệt.
- `+cancelRequest(requestId: ID): Boolean` — Tiếp nhận yêu cầu cancelRequest qua REST và trả kết quả cho trình duyệt.

### ChargingRequestService «service»

- `+checkIdentity(): Boolean` — Kiểm tra người dùng hiện tại đã đăng nhập và có vai trò Student hay chưa. true nếu người dùng được phép đăng ký sạc.
- `+checkAvailability(hubId: ID, startTime: DateTime, endTime: DateTime): Boolean` — Kiểm tra chỗ đỗ, cổng sạc, lịch đã đặt và trạng thái kết nối IoT trong khoảng thời gian yêu cầu. true nếu tài nguyên phù hợp còn khả dụng; lỗi IoT cần được phân biệt với hết tài nguyên.
- `+assignCharger(vehicleId: ID, hubId: ID, startTime: DateTime, endTime: DateTime): ChargingPort` — Chọn cổng phù hợp dựa trên trạng thái cổng, thời gian, mức pin và nhu cầu sạc của xe. Trả về ChargingPort được phân bổ; nếu không có cổng thì trả kết quả không phân bổ/gợi ý khác.
- `+estimateChargingTime(vehicleId: ID, portId: ID, targetBatteryPct: double): Integer` — Tính lượng điện còn thiếu từ pin hiện tại đến pin mục tiêu. Ước tính thời lượng từ dung lượng pin và công suất có thể cấp. Báo thiếu dữ liệu nếu chưa thể ước tính.
- `+bookCharging(vehicleId: ID, hubId: ID, startTime: DateTime, endTime: DateTime, targetBatteryPct: double): BookingResult` — Đối chiếu xe, pin mục tiêu, cổng và khung giờ. Kiểm tra công suất tại Hub, giữ khoảng sạc rồi xác nhận yêu cầu sau khi việc ghi dữ liệu thành công.
- `+cancelRequest(requestId: ID): Boolean` — Kiểm tra chủ yêu cầu và trạng thái còn được hủy. Giải phóng phân bổ tương ứng và cập nhật yêu cầu sạc trong cùng giao dịch.

### VehicleDataStore «interface»

- `+findById(vehicleId: ID): Optional<Vehicle>` — Tra cứu VehicleDataStore theo định danh bản ghi.

### ParkingSpaceDataStore «interface»

- `+findById(spaceId: ID): Optional<ParkingSpace>` — Tra cứu ParkingSpaceDataStore theo định danh bản ghi.
- `+findByHub(hubId: ID): List<ParkingSpace>` — Tra cứu ParkingSpaceDataStore theo Hub chứa tài nguyên.
- `+save(space: ParkingSpace): void` — Lưu đối tượng đã được bên gọi kiểm tra theo định danh của nó.

### ChargingRequestDataStore «interface»

- `+findById(requestId: ID): Optional<ChargingRequest>` — Tra cứu ChargingRequestDataStore theo định danh bản ghi.
- `+findByHub(hubId: ID): List<ChargingRequest>` — Tra cứu ChargingRequestDataStore theo Hub chứa tài nguyên.
- `+save(request: ChargingRequest): void` — Lưu đối tượng đã được bên gọi kiểm tra theo định danh của nó.

### ChargingPortDataStore «interface»

- `+findById(portId: ID): Optional<ChargingPort>` — Tra cứu ChargingPortDataStore theo định danh bản ghi.
- `+findByHub(hubId: ID): List<ChargingPort>` — Tra cứu ChargingPortDataStore theo Hub chứa tài nguyên.
- `+save(port: ChargingPort): void` — Lưu đối tượng đã được bên gọi kiểm tra theo định danh của nó.

### Vehicle «entity»

Thuộc tính: `-vehicleId: ID`, `-hubId: ID?`, `-parkingSpaceId: ID?`, `-vehicleType: VehicleType`, `-batteryPct: double?`, `-batteryCapacityKWh: double`, `-maxChargingPowerKW: double`, `-status: VehicleStatus`, `-telemetryAt: DateTime?`, `-version: long`, `-ownership: VehicleOwnership`, `-ownerAccountId: ID?`.

### ChargingPort «entity»

Thuộc tính: `-portId: ID`, `-hubId: ID`, `-powerKW: double`, `-status: PortStatus`.

### ChargingRequest «entity»

Thuộc tính: `-requestId: ID`, `-studentAccountId: ID`, `-vehicleId: ID`, `-hubId: ID`, `-portId: ID?`, `-startAt: DateTime`, `-endAt: DateTime`, `-targetBatteryPct: double`, `-estimatedMinutes: int?`, `-status: RequestStatus`.

### ChargingAllocation «entity»

Thuộc tính: `-allocationId: ID`, `-requestId: ID?`, `-scheduleId: ID?`, `-vehicleId: ID`, `-portId: ID`, `-startAt: DateTime`, `-endAt: DateTime`, `-status: AllocationStatus`.

### ChargingSession «entity»

Thuộc tính: `-sessionId: ID`, `-allocationId: ID`, `-actualStartAt: DateTime`, `-actualEndAt: DateTime?`.

### IdentityService «service»

- `+authenticate(studentId: ID): AuthenticationResult` — Đối chiếu thông tin phiên với tài khoản đang hoạt động và kiểm tra vai trò mà thao tác yêu cầu.
- `+verifyPermission(subjectId: ID, action: String): Boolean` — Đối chiếu quyền của tác nhân đã xác thực với hành động và tài nguyên đích.

### ChargingIoTGateway «interface»

- `+readHubStatus(hubId: ID): HubStatus` — Đọc trạng thái thực tế của Mobility Hub và cổng sạc từ hệ thống IoT.
- `+reservePort(portId: ID, startTime: DateTime, endTime: DateTime): Boolean` — Gửi lệnh giữ cổng sạc trong khoảng thời gian chỉ định.
- `+releasePort(portId: ID): Boolean` — Gửi lệnh giải phóng cổng khi hủy yêu cầu sạc.

### StateCoordinator «service»

- `+commit(expectedVersions: Map<ID,long>, changes: ChangeSet, events: List<Event>): CommitResult` — Kiểm tra các phiên bản kỳ vọng trong vùng đồng bộ. Ghi toàn bộ thay đổi liên quan và chỉ công bố trạng thái mới khi lưu thành công.

### AllocationService «service»

- `+reserve(resource: ResourceRef, window: TimeInterval, businessRef: ID): Allocation` — Giữ tài nguyên theo định danh và khoảng sử dụng sau khi kiểm tra lịch đang hiệu lực.
- `+release(allocationId: ID, expectedOwner: ID): void` — Giải phóng đúng phân bổ còn hiệu lực của yêu cầu, giữ các phân bổ khác.
- `+hasOverlap(resource: ResourceRef, window: TimeInterval): Boolean` — So sánh thời điểm bắt đầu và kết thúc với các khoảng đã giữ của cùng tài nguyên. Hai khoảng chạm biên không giao nhau.
- `+checkHubPower(hubId: ID, window: TimeInterval, newKW: double): Boolean` — Tổng hợp công suất các phiên sạc đồng thời và so sánh với giới hạn công suất tại Hub.

### Quan hệ với thực thể

- `VehicleDataStore` → `Vehicle` (stores / reads).
- `ChargingRequestDataStore` → `ChargingRequest` (stores / reads).
- `ChargingPortDataStore` → `ChargingPort` (stores / reads).
- `Vehicle` → `ChargingRequest` (history · 1 / 0..*).
- `ChargingRequest` → `ChargingAllocation` (confirmed allocation · 0..1 / 0..1).
- `ChargingPort` → `ChargingAllocation` (calendar; no overlap · 1 / 0..*).
- `ChargingAllocation` → `ChargingSession` (actual use · 1 / 0..1).
