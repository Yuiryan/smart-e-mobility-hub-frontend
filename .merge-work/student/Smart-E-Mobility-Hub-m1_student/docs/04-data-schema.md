# 04 — Data Schema & Persistence Strategy

Tài liệu này định nghĩa chi tiết mô hình dữ liệu (Entity Schema), các trường dữ liệu, quan hệ giữa các thực thể, cơ chế lưu trữ snapshot nguyên tử vào file `data/data.json`, và chiến lược di chuyển/tương thích schema (Schema Migration Strategy).

---

## 1. Nguồn Sự Thật & Chiến Lược Lưu Trữ (Source of Truth & Persistence)

- **Single Source of Truth khi Runtime:** Bộ nhớ RAM (`InMemoryDataStore`), tổ chức theo các `ConcurrentHashMap<String, T>` tương ứng với từng thực thể.
- **Durable Snapshot File:** File `data/data.json` đóng vai trò là bản lưu trạng thái bền vững giữa các lần khởi động ứng dụng.
- **Cơ chế Ghi Nguyên tử (Atomic Write):**
  - Mọi thao tác ghi dữ liệu xuống đĩa đều thông qua `StateCoordinator` và `JsonStore`.
  - Dữ liệu được ghi ra file tạm `data/data.json.tmp`, sau đó đổi tên bằng thao tác `ATOMIC_MOVE` của hệ điều hành. Đảm bảo nếu hệ thống bị ngắt nguồn đột ngột, file gốc không bao giờ bị hỏng (corrupted).
- **Kiểm soát Đồng thời (Optimistic Concurrency Control):**
  - StateCoordinator kiểm tra phiên bản snapshot khi ghi. Trong mô hình UC01–UC07, Vehicle còn có thuộc tính version riêng.
  - Khi cập nhật, phương thức `StateCoordinator.commit(expectedVersion, mutation)` sẽ so sánh `version`. Nếu `currentVersion != expectedVersion`, giao dịch bị từ chối và ném ra ngoại lệ `OptimisticLockException`.

---

## 2. Chi Tiết Thực Thể (Entity Data Schemas)

### 2.1. Account (Tài khoản người dùng)
| Tên trường | Kiểu dữ liệu | Bắt buộc | Mô tả & Ràng buộc |
|---|---|:---:|---|
| `accountId` | `String` | Có | Khóa chính (UUID hoặc định dạng `acc_xxx`). |
| `email` | `String` | Có | Email duy nhất (Unique), định dạng `@bku.edu.vn` hoặc email hợp lệ. |
| `passwordHash` | `String` | Có | Bản băm mật khẩu (BCrypt hoặc SHA-256 có muối). |
| `studentId` | `String` | Không | Mã số sinh viên (chỉ bắt buộc khi `role == STUDENT`). |
| `fullName` | `String` | Có | Họ và tên người dùng. |
| `role` | `AccountRole` | Có | Enum: `STUDENT`, `OPERATOR`. |
| `status` | `AccountStatus` | Có | Enum: `ACTIVE`, `SUSPENDED`, `UNVERIFIED`. |
| `createdAt` | `Instant` | Có | Thời điểm tạo tài khoản (ISO-8601 UTC). |
| `updatedAt` | `Instant` | Có | Thời điểm cập nhật thông tin gần nhất. |

### 2.2. Session (Phiên đăng nhập)
| Tên trường | Kiểu dữ liệu | Bắt buộc | Mô tả & Ràng buộc |
|---|---|:---:|---|
| `sessionToken` | `String` | Có | Khóa chính, chuỗi token ngẫu nhiên bảo mật (Opaque token). |
| `accountId` | `String` | Có | Khóa ngoại tham chiếu đến `Account.accountId`. |
| `role` | `AccountRole` | Có | Enum vai trò để phân quyền nhanh không cần lookup bảng Account. |
| `createdAt` | `Instant` | Có | Thời điểm đăng nhập. |
| `expiresAt` | `Instant` | Có | Thời điểm hết hạn phiên (mặc định sau 30 phút). |

### Hồ sơ Student

- `-studentId: ID`
- `-accountId: ID`
- `-name: String`
- `-accountStatus: AccountStatus`
- `-verifiedAt: DateTime?`

accountId liên kết với Account. Hai điều kiện có lượt đặt hoặc chuyến đang hoạt động được suy ra từ Reservation và Ride.

### 2.3. MobilityHub (Trạm di chuyển thông minh)

- `-hubId: ID`
- `-name: String`
- `-address: String`
- `-location: GeoPoint`
- `-serviceRadiusKm: double`
- `-parkingCapacity: int`
- `-chargingCapacityKW: double`
- `-expectedIncoming: int`
- `-expectedOutgoing: int`

location dùng cho tìm kiếm. parkingCapacity và chargingCapacityKW lần lượt giới hạn chỗ đỗ và công suất.

### 2.4. Vehicle (Phương tiện xe điện)

- `-vehicleId: ID`
- `-hubId: ID?`
- `-parkingSpaceId: ID?`
- `-vehicleType: VehicleType`
- `-batteryPct: double?`
- `-batteryCapacityKWh: double`
- `-maxChargingPowerKW: double`
- `-status: VehicleStatus`
- `-telemetryAt: DateTime?`
- `-version: long`
- `-ownership: VehicleOwnership`
- `-ownerAccountId: ID?`

batteryPct và telemetryAt có thể thiếu. Xe đang di chuyển có thể rời Hub và ô đỗ.

### 2.5. Telemetry (Dữ liệu đo xa thời gian thực)
| Tên trường | Kiểu dữ liệu | Bắt buộc | Mô tả & Ràng buộc |
|---|---|:---:|---|
| `quality` | `DataQuality` | Có | Enum: `FRESH` ($\le 10$p), `STALE` ($> 10$p), `MISSING`. |
| `latitude` | `Double` | Không | Tọa độ vĩ độ GPS từ cảm biến thiết bị. |
| `longitude` | `Double` | Không | Tọa độ kinh độ GPS từ cảm biến thiết bị. |
| `batteryPct` | `Double` | Không | Dung lượng pin báo từ BMS (Battery Management System). |
| `lockStatus` | `String` | Có | Trạng thái khóa cơ: `LOCKED`, `UNLOCKED`. |
| `observedAt` | `Instant` | Có | Thời điểm cảm biến ghi nhận dữ liệu tại thiết bị. |

### 2.6. ParkingSpace (Ô đỗ xe tại Trạm)

- `-spaceId: ID`
- `-hubId: ID`
- `-vehicleType: VehicleType`
- `-status: ParkingSpaceStatus`
- `-occupiedVehicleId: ID?`
- `-vehicleReservationId: ID?`
- `-parkingReservationId: ID?`

Hai khóa vehicleReservationId và parkingReservationId phân biệt giữ xe để nhận với giữ ô đỗ. Chỉ giải phóng lượt giữ đúng chủ.

### 2.7. Reservation (Đơn đặt trước xe chia sẻ)

- `-reservationId: ID`
- `-studentId: ID`
- `-vehicleId: ID`
- `-startAt: DateTime`
- `-endAt: DateTime`
- `-holdUntil: DateTime`
- `-status: ReservationStatus`
- `-pickupCodeHash: String?`

studentId tham chiếu Student. holdUntil là hạn nhận xe. Khoảng sử dụng là [startAt, endAt). Mã cấp một lần được lưu dưới dạng hash và bị thu hồi khi lượt giữ kết thúc.

### 2.8. ParkingReservation (Đơn đặt trước chỗ đỗ xe)

- `-reservationId: ID`
- `-studentId: ID`
- `-spaceId: ID`
- `-startAt: DateTime`
- `-endAt: DateTime`
- `-holdDeadline: DateTime`
- `-status: ParkingReservationStatus`

spaceId xác định ô và qua ô xác định Hub. holdDeadline là hạn nhận ô theo giờ hẹn.

### 2.9. Ride (Chuyến đi di chuyển)

- `-rideId: ID`
- `-studentId: ID`
- `-vehicleId: ID`
- `-reservationId: ID?`
- `-requestId: ID`
- `-startedAt: DateTime`
- `-endedAt: DateTime?`
- `-status: RideStatus`

reservationId chỉ có ở nhánh đặt trước. requestId nhận diện lần nhận xe để tạo chuyến đúng một lần.

### 2.10. Đăng ký và sử dụng cổng sạc

**ChargingPort**

- `-portId: ID`
- `-hubId: ID`
- `-powerKW: double`
- `-status: PortStatus`

**ChargingRequest**

- `-requestId: ID`
- `-studentAccountId: ID`
- `-vehicleId: ID`
- `-hubId: ID`
- `-portId: ID?`
- `-startAt: DateTime`
- `-endAt: DateTime`
- `-targetBatteryPct: double`
- `-estimatedMinutes: int?`
- `-status: RequestStatus`

**ChargingAllocation**

- `-allocationId: ID`
- `-requestId: ID?`
- `-scheduleId: ID?`
- `-vehicleId: ID`
- `-portId: ID`
- `-startAt: DateTime`
- `-endAt: DateTime`
- `-status: AllocationStatus`

**ChargingSession**

- `-sessionId: ID`
- `-allocationId: ID`
- `-actualStartAt: DateTime`
- `-actualEndAt: DateTime?`

ChargingRequest ghi nhu cầu. ChargingAllocation giữ cổng trong khoảng đã xác nhận. ChargingSession ghi thời gian sử dụng thực tế của allocation.

**Nhật ký nhận và trả xe (`studentOperations`)**

Mỗi bản ghi lưu id của lệnh, studentId, vehicleId, spaceId, rideId, reservationId nếu có, loại thao tác, hash của yêu cầu, thời điểm và kết quả. Khi khởi động lại, backend tra kết quả lệnh đang chờ qua adapter. FE dùng lại cùng Idempotency-Key. Yêu cầu sạc giữ allocation ở trạng thái RESERVED trong khi chờ thiết bị xác nhận đăng ký hoặc hủy. Backend dùng requestId để đối soát với cổng sạc.

### 2.11. DispatchOrder (Lệnh điều phối luân chuyển xe)
- `dispatchOrderId`, `operatorId`, `sourceHubId`, `destinationHubId`, `vehicleIds` (`List<String>`), `status` (`PENDING`, `IN_TRANSIT`, `COMPLETED`, `CANCELLED`), `createdAt`, `completedAt`.

### 2.12. Incident & SystemAlert (Sự cố & Cảnh báo mạng lưới)
- `Incident`: `incidentId`, `reporterId`, `targetType` (`VEHICLE`, `CHARGING_PORT`, `SPACE`), `targetId`, `description`, `severity` (`WARNING`, `CRITICAL`), `status` (`OPEN`, `RESOLVED`), `reportedAt`, `resolvedAt`.
- `SystemAlert`: `alertId`, `hubId`, `severity` (`NORMAL`, `WARNING`, `CRITICAL`, `MISSING_DATA`), `type` (`HIGH_OCCUPANCY`, `LOW_VEHICLES`, `STALE_TELEMETRY`), `message`, `createdAt`, `acknowledged`.

---

## 3. Cấu Trúc File Snapshot `data/data.json`

Cấu trúc snapshot **đang được code đọc/ghi** qua `StoreSnapshot` như sau. Hai file `data/data.json` (dữ liệu làm việc) và `data/data.example.json` (bản mẫu để copy thủ công) dùng đúng cấu trúc này:

```json
{
  "version": 0,
  "accounts": [],
  "students": [],
  "sessions": [],
  "hubs": [],
  "vehicles": [],
  "parkingSpaces": [],
  "chargingPorts": [],
  "reservations": [],
  "parkingReservations": [],
  "rides": [],
  "chargingRequests": [],
  "chargingAllocations": [],
  "chargingSessions": [],
  "studentOperations": [],
  "dispatchOrders": [],
  "incidents": []
}
```

### 3.1. Tên trường thực tế cần dùng trong file

Một số mô tả ở mục 2 là model mục tiêu. Khi soạn snapshot cho checkout hiện tại, dùng chính xác tên trong các Java record dưới đây; không dùng tên field của API thay cho field persistence:

| Collection | Các trường JSON hiện hành |
|---|---|
| `accounts` | `id`, `email`, `passwordHash`, `role`, `active`, `createdAt` |
| `students` | `studentId`, `accountId`, `name`, `accountStatus`, `verifiedAt` |
| `sessions` | `id` (token), `accountId`, `createdAt`, `expiresAt` |
| `hubs` | `hubId`, `name`, `address`, `location` (`latitude`, `longitude`), `serviceRadiusKm`, `parkingCapacity`, `chargingCapacityKw`, `expectedIncoming`, `expectedOutgoing` |
| `vehicles` | `vehicleId`, `hubId`, `parkingSpaceId`, `vehicleType`, `batteryPct`, `batteryCapacityKwh`, `maxChargingPowerKw`, `status`, `telemetryAt`, `version`, `ownership`, `ownerAccountId` |
| `parkingSpaces` | `id`, `hubId`, `status`, `vehicleType`, `occupiedVehicleId`, `parkingReservationId`, `vehicleReservationId` |
| `chargingPorts` | `id`, `hubId`, `name`, `powerKw`, `status` |

- `Account` hiện dùng `id` và boolean `active`; tên và MSSV nằm trong `Student`. Model hiện chưa có trường tên riêng cho operator. `Student.accountId` và `Vehicle.ownerAccountId` tham chiếu `Account.id`.
- `ParkingSpace.id` và `ChargingPort.id` không phải `spaceId`/`portId` trong JSON persistence. `Vehicle.parkingSpaceId` trỏ đến ô có `occupiedVehicleId` trỏ ngược lại xe đó; cả hai phải cùng hub và loại xe.
- Enum cổng sạc hiện hành trong code là `AVAILABLE`, `RESERVED`, `CHARGING`, `OUT_OF_SERVICE`. Mẫu dùng `OUT_OF_SERVICE` cho cổng ngừng phục vụ. Các tên `SCHEDULED`/`MAINTENANCE` ở tài liệu model mục tiêu chưa phải giá trị enum này.
- `StoreSnapshot` chưa có `schemaVersion`, `savedAt`, `alerts` hoặc collection telemetry riêng. Telemetry hiện nằm ở `Vehicle.batteryPct` và `telemetryAt`; không thêm trường chưa được hỗ trợ vào mẫu rồi kỳ vọng chúng được lưu giữ.
- Mọi collection hiện được hỗ trợ đều được khai báo trong mẫu, bao gồm các collection nghiệp vụ còn rỗng. Các trường collection bị thiếu trong snapshot cũ được `StoreSnapshot` chuyển thành danh sách rỗng. `version` thiếu được đọc là 0. Số đo xe bị thiếu vẫn là `null`.
- `sessions` hiện có trong snapshot và được restore; hạn phiên vẫn được kiểm tra khi truy cập. Mẫu để `sessions: []`, không tạo token lâu dài để bỏ qua đăng nhập.
- Hash tài khoản demo dùng quy ước `sha256$salt$hex` (SHA-256 của UTF-8 `salt:password`) với mật khẩu `Demo123!` và salt khác nhau cho mỗi tài khoản. Đây là quy ước fixture chờ tích hợp UC-00, chưa có bộ xác minh mật khẩu trong checkout hiện tại.

### 3.2. Nạp, lưu và khởi động lại

1. `app.data-file=./data/data.json` được resolve theo thư mục chạy chương trình.
2. `DataStoreBootstrap.run()` gọi `JsonStore.load()` và `InMemoryDataStore.restore()` một lần khi khởi động; không nạp đè từ bản mẫu, không đổi timestamp telemetry.
3. Các thay đổi runtime đi qua `StateCoordinator.commit(expectedVersion, change)`. Version không khớp trả `false` mà không ghi dữ liệu. Commit thành công tăng version và lưu toàn bộ snapshot; lỗi mutation/persistence khôi phục snapshot trước đó trong RAM.
4. `JsonStore` ghi file `.tmp` rồi chuyển sang file đích. Code hiện dùng `ATOMIC_MOVE` nếu filesystem hỗ trợ, và fallback `REPLACE_EXISTING` nếu không hỗ trợ atomic move; fallback không có cùng bảo đảm nguyên tử.
5. File không tồn tại hoặc trống/khoảng trắng hiện trả về không có snapshot; kho mới vẫn rỗng. JSON sai cú pháp khiến startup thất bại, không tự ghi dữ liệu mẫu. Cần backup và khôi phục thủ công nếu file bị mất/hỏng.

Mẫu gồm 3 hub, 12 xe, 24 chỗ đỗ, 6 cổng sạc và 5 tài khoản. Dữ liệu/toạ độ là giả lập. Xe `bike_004` thiếu telemetry; các xe khác có mốc đo cố định và sẽ thành `STALE` theo thời gian. Không tự đổi các số đo này thành `FRESH` để làm đầy kết quả tìm kiếm. Xem [README](../README.md) để chạy, sao lưu và reset mẫu.

---

## 4. Chiến Lược Tương Thích & Di Chuyển Schema (Migration Strategy)

1. **Quy tắc Backward Compatibility (Tương thích ngược):**
   - Mọi Entity POJO trong Java phải được đánh dấu annotation `@JsonIgnoreProperties(ignoreUnknown = true)`.
   - Khi đọc file `data/data.json` từ phiên bản cũ, các trường mới chưa có trong file sẽ nhận giá trị tùy chọn phù hợp. Số đo chưa có phải giữ ở trạng thái thiếu, không gây lỗi văng app khi deserialize.
2. **Quy tắc Thêm Trường Dữ Liệu:**
   - Trường mới phải luôn là `Optional` hoặc có giá trị mặc định hợp lệ. Tuyệt đối không thêm trường kiểu nguyên thủy bắt buộc mà không gán giá trị mặc định.
3. **Quy tắc Đổi Tên hoặc Xóa Trường:**
   - Khi muốn đổi tên một trường, giữ trường cũ và dùng `@JsonProperty` hoặc `@JsonAlias` để hỗ trợ cả hai tên trong ít nhất một chu kỳ phát hành trước khi dọn dẹp hoàn toàn.

