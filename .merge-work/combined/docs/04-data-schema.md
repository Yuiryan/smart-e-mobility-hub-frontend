# 04 — Data Schema & Persistence Strategy

Tài liệu này định nghĩa chi tiết mô hình dữ liệu (Entity Schema), các trường dữ liệu, quan hệ giữa các thực thể, cơ chế lưu trữ snapshot nguyên tử vào file `data/data.json`, và chiến lược di chuyển/tương thích schema (Schema Migration Strategy).

---

## 1. Nguồn Sự Thật & Chiến Lược Lưu Trữ (Source of Truth & Persistence)

- **Single Source of Truth khi Runtime:** Bộ nhớ RAM (`InMemoryDataStore`), tổ chức theo các `ConcurrentHashMap<String, T>` tương ứng với từng thực thể.
- **Durable Snapshot File:** File `data/data.json` đóng vai trò là bản lưu trạng thái bền vững giữa các lần khởi động ứng dụng.
- **Cơ chế Ghi Nguyên tử (Atomic Write):**
  - Mọi thao tác ghi dữ liệu xuống đĩa đều thông qua `StateCoordinator` và `JsonStore`.
  - Dữ liệu được ghi ra file tạm `data/data.json.tmp`, sau đó đổi tên bằng thao tác `ATOMIC_MOVE` của hệ điều hành. Khi hệ thống file hỗ trợ, dùng ATOMIC_MOVE. Nếu không hỗ trợ, code dùng REPLACE_EXISTING; trường hợp fallback có bảo đảm yếu hơn khi bị ngắt giữa thao tác.
- **Kiểm soát Đồng thời (Optimistic Concurrency Control):**
  - StateCoordinator kiểm tra phiên bản snapshot khi ghi. Trong mô hình UC01–UC07, Vehicle còn có thuộc tính version riêng.
  - Khi cập nhật, phương thức `StateCoordinator.commit(expectedVersion, mutation)` sẽ so sánh `version`. Nếu `currentVersion != expectedVersion`, giao dịch trả false và không thực thi mutation. Service ánh xạ kết quả này thành lỗi xung đột phù hợp.

---

## 2. Chi Tiết Thực Thể (Entity Data Schemas)

### 2.1. Account (Tài khoản người dùng)
| Tên trường | Kiểu dữ liệu | Bắt buộc | Mô tả & Ràng buộc |
|---|---|:---:|---|
| accountId | String | Có | Khóa chính, Java dùng id(); chấp nhận alias id khi đọc snapshot cũ. |
| email | String | Có | Email hợp lệ, duy nhất, được chuẩn hóa khi đăng ký. |
| passwordHash | String | Có | Hash BCrypt. |
| studentId | String | Theo role | Bắt buộc đối với STUDENT. |
| fullName | String | Có | Họ tên người dùng. |
| role | AccountRole | Có | STUDENT hoặc OPERATOR. |
| status | AccountStatus | Có | ACTIVE, SUSPENDED hoặc UNVERIFIED. |
| createdAt | Instant | Có | Thời điểm tạo tài khoản. |
| updatedAt | Instant | Có | Khi tạo bằng createdAt; dữ liệu cũ thiếu giá trị này dùng createdAt làm mốc thay thế. |

### 2.2. Session (Phiên đăng nhập)

| Tên trường | Kiểu dữ liệu | Bắt buộc | Mô tả & Ràng buộc |
|---|---|:---:|---|
| tokenHash | String | Có | SHA-256 dạng hex của opaque token. Java dùng id(); chấp nhận alias id của snapshot cũ. |
| accountId | String | Có | Tham chiếu Account.accountId. |
| createdAt | Instant | Có | Thời điểm tạo session, được giữ khi gia hạn. |
| expiresAt | Instant | Có | Hạn idle. Xác thực thành công gia hạn đến thời điểm hiện tại cộng session-timeout, mặc định 30 phút. |

Role và status được đọc từ Account hiện hành khi xác thực. Session hết hạn khi now >= expiresAt.
Raw sessionToken được trả trong response login. Snapshot lưu tokenHash.
Alias id chỉ hỗ trợ snapshot cũ của code đã lưu hash. Snapshot dùng raw sessionToken cần quy tắc chuyển đổi riêng nếu có dữ liệu thực tế.

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
  "incidents": [],
  "recoveryCodes": [],
  "recoverySessions": [],
  "deviceOperations": [],
  "deviceReceipts": [],
  "scheduleProposals": [],
  "chargingSchedules": []
}
```

### 3.1. Tên trường thực tế cần dùng trong file

Một số mô tả ở mục 2 là model mục tiêu. Khi soạn snapshot cho checkout hiện tại, dùng chính xác tên trong các Java record dưới đây; không dùng tên field của API thay cho field persistence:

| Collection | Các trường JSON hiện hành |
|---|---|
| `accounts` | `accountId`, `fullName`, `studentId`, `email`, `passwordHash`, `role`, `status`, `createdAt`, `updatedAt` |
| `students` | `studentId`, `accountId`, `name`, `accountStatus`, `verifiedAt` |
| `sessions` | `tokenHash`, `accountId`, `createdAt`, `expiresAt` |
| `hubs` | `hubId`, `name`, `address`, `location` (`latitude`, `longitude`), `serviceRadiusKm`, `parkingCapacity`, `chargingCapacityKw`, `expectedIncoming`, `expectedOutgoing` |
| `vehicles` | `vehicleId`, `hubId`, `parkingSpaceId`, `vehicleType`, `batteryPct`, `batteryCapacityKwh`, `maxChargingPowerKw`, `status`, `telemetryAt`, `version`, `ownership`, `ownerAccountId` |
| `parkingSpaces` | `id`, `hubId`, `status`, `vehicleType`, `occupiedVehicleId`, `parkingReservationId`, `vehicleReservationId` |
| `chargingPorts` | `id`, `hubId`, `name`, `powerKw`, `status` |

- `Account` ghi `accountId` và `Session` ghi `tokenHash`; cả hai vẫn đọc alias `id` từ snapshot cũ. `Student.accountId` và `Vehicle.ownerAccountId` tham chiếu `Account.accountId`.
- `ParkingSpace.id` và `ChargingPort.id` không phải `spaceId`/`portId` trong JSON persistence. `Vehicle.parkingSpaceId` trỏ đến ô có `occupiedVehicleId` trỏ ngược lại xe đó; cả hai phải cùng hub và loại xe.
- Enum cổng sạc hiện hành trong code là `AVAILABLE`, `RESERVED`, `CHARGING`, `OUT_OF_SERVICE`. Mẫu dùng `OUT_OF_SERVICE` cho cổng ngừng phục vụ. Các tên `SCHEDULED`/`MAINTENANCE` ở tài liệu model mục tiêu chưa phải giá trị enum này.
- `StoreSnapshot` chưa có `schemaVersion`, `savedAt`, `alerts` hoặc collection telemetry riêng. Telemetry hiện nằm ở `Vehicle.batteryPct` và `telemetryAt`; không thêm trường chưa được hỗ trợ vào mẫu rồi kỳ vọng chúng được lưu giữ.
- Mọi collection hiện được hỗ trợ đều được khai báo trong mẫu, bao gồm các collection nghiệp vụ còn rỗng. Các trường collection bị thiếu trong snapshot cũ được `StoreSnapshot` chuyển thành danh sách rỗng. `version` thiếu được đọc là 0. Số đo xe bị thiếu vẫn là `null`.
- `sessions` hiện có trong snapshot và được restore; hạn phiên vẫn được kiểm tra khi truy cập. Mẫu để `sessions: []`, không tạo token lâu dài để bỏ qua đăng nhập.
- Mật khẩu tài khoản do UC-00 quản lý bằng BCrypt; snapshot không lưu mật khẩu thô. Các fixture cũ dùng định dạng hash khác cần được chuyển đổi trước khi dùng cho đăng nhập.

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

## HTTP identity và logout

SecurityContext chỉ tồn tại theo request. CurrentAccount chứa danh tính và session hash nội bộ, không thêm field vào snapshot.
GET /api/auth/me trả DTO riêng gồm accountId, studentId, email và role.
SessionService.logout kiểm tra accountId sở hữu session rồi xóa qua StateCoordinator. Mọi lỗi mutation khôi phục snapshot RAM/version.
Filter xác thực và gia hạn trước khi endpoint logout thu hồi session trong commit tiếp theo.

## Đổi mật khẩu khi đã đăng nhập

Khi đổi mật khẩu, giữ accountId, role, status và createdAt.
Cập nhật passwordHash và updatedAt, đồng thời xóa mọi Session của Account
trong cùng StateCoordinator.commit. Lỗi ghi khôi phục trạng thái trước mutation.

## Dữ liệu khôi phục mật khẩu

Snapshot có thêm recoveryCodes và recoverySessions. Snapshot cũ thiếu hai mảng đọc thành danh sách rỗng.
Snapshot và restore cùng bao gồm hai bảng để StateCoordinator rollback toàn bộ mutation.

### RecoveryCode

| Field JSON | Kiểu Java | Ý nghĩa |
|---|---|---|
| tokenId | String | ID lần phát hành mã; accessor Java id(). |
| accountId | String | Tài khoản cần khôi phục. |
| hashCode | String | Hash của mã gửi email; accessor Java codeHash(). |
| expiresAt | Instant | Thời hạn mã. |
| isUsed | boolean | Quyền khôi phục đã được sử dụng; accessor Java used(). |
| failedAttempts | int | Số lần xác minh sai, được nghiệp vụ khôi phục cập nhật. |

Java dùng codeHash vì hashCode() đã là method trả int của Object.
Alias id được chấp nhận cho tokenId khi đọc JSON.

### RecoverySession

| Field JSON | Kiểu Java | Ý nghĩa |
|---|---|---|
| tokenHash | String | Hash của recovery token; accessor Java id(). |
| accountId | String | Tài khoản đã được xác minh. |
| recoveryCodeId | String | Tham chiếu tokenId của đúng lần phát hành mã. |
| createdAt | Instant | Thời điểm cấp quyền khôi phục. |
| expiresAt | Instant | Thời hạn quyền khôi phục. |

Alias id được chấp nhận cho tokenHash khi đọc JSON.
Service chịu trách nhiệm mã hiện hành, thời hạn, giới hạn thử và thu hồi grant trong cùng commit.
AccountDataStore cung cấp CRUD; các hàm save/delete được gọi trong mutation của StateCoordinator.

## Phát hành mã khôi phục

AuthService.sendRecovery tạo hash BCrypt cho mã 6 chữ số và expiresAt theo thời hạn cấu hình.
Trong cùng coordinator commit, code trước đó và recovery session của Account được thu hồi trước khi lưu code mới.
Mail sender được gọi sau commit. SMTP lỗi giữ code đã persist; lần yêu cầu tiếp theo thay code đó.
Raw code chỉ đi tới mail sender. SMTP_HOST/PORT/USERNAME/PASSWORD/AUTH/STARTTLS/FROM được lấy từ môi trường.

### Vòng đời recovery

Verify sai ghi tăng failedAttempts. Verify đúng lưu RecoverySession với tokenHash, accountId, recoveryCodeId và thời hạn của mã. Mỗi mã chỉ cấp một grant. Reset thành công cập nhật Account, đặt isUsed=true, xóa mọi Session và RecoverySession của tài khoản trong cùng StateCoordinator.commit. Đổi mật khẩu khi đăng nhập cũng xóa mã và grant hiện hành.

## Lệnh thiết bị và cảm biến trạm

Snapshot deviceOperations lưu createdAt và response (command với commandId/type/vehicleId/spaceId, status, observedAt, batteryLevel, reason). Snapshot cũ thiếu mảng dùng danh sách rỗng. Số đo trạm/cổng chỉ nằm trong cache simulator.

## Biên nhận simulator bền vững

Snapshot thêm deviceReceipts. Mỗi phần tử có result (DeviceResponse), acceptedAt, availableAt, timeout (Duration ISO-8601), dropAcknowledgement và sequence (long). id Java lấy từ result.command.commandId. Mảng thiếu ở snapshot cũ được khởi tạo rỗng.

DeviceCommand bổ sung hubId, portId, reservationId, startAt và endAt cho RESERVE_PORT/RELEASE_PORT. Dữ liệu LOCK/UNLOCK cũ có các trường này là null. FORCE_LOCK chỉ cần vehicleId. Receipt được ghi qua StateCoordinator, sequence theo version commit. Tác động thiết bị được tái dựng từ receipt ACK theo sequence. Bản ghi RESERVE_PORT tạo lịch giữ, RELEASE_PORT giải phóng đúng reservationId. Lịch đã kết thúc không còn được coi là active.

Biên nhận thiết bị là bằng chứng thực thi, độc lập với DeviceOperation theo dõi phản hồi backend. Đọc cảm biến tiếp tục giữ observedAt từ frame, không tự làm mới tuổi pin/GPS khi gửi lệnh.

## Mô hình lập lịch UC-13

Vehicle thêm batteryCapacityKWh và maxChargingPowerKw (Double nullable). Thiếu thông số không tự gán dung lượng/công suất. ChargingRequest thêm startAt, endAt, nextUsageAt (Instant nullable); dữ liệu cũ thiếu giữ null và báo cần cập nhật khi lập lịch.

ChargingAllocation thêm vehicleId và scheduleId (String nullable). Allocation cũ có thể tra vehicleId qua chargingRequestId. ChargingPortStatus canonical gồm AVAILABLE, SCHEDULED, CHARGING, MAINTENANCE. JSON cũ RESERVED và OUT_OF_SERVICE được đọc qua alias tương ứng.

Snapshot thêm scheduleProposals và chargingSchedules, thiếu ở snapshot cũ đọc thành mảng rỗng. Proposal là draft có revision và chưa chiếm tài nguyên. Schedule xác nhận tham chiếu proposalId, operatorId, allocationIds. Confirm ghi schedule, allocation RESERVED, request SCHEDULED và proposal.confirmedScheduleId trong cùng StateCoordinator.commit.

## Dữ liệu đầu vào UC-14

Ride bổ sung originHubId, destinationHubId (String nullable), distanceKm (Double nullable). Snapshot cũ giữ null cho các trường thiếu. UC-14 loại các mẫu lịch sử thiếu thông tin khỏi baseline và báo missingData.

Scenario, SimulationState và SimulationResult chỉ tồn tại trong bộ nhớ mỗi lần gọi. SimulationState có một InMemoryDataStore riêng phục hồi từ snapshot, cùng projection hub/vehicle riêng để thay đổi occupancy và pin. Dữ liệu live và device journal không bị ghi bởi engine.

## Ownership operation

DeviceOperation bổ sung accountId (String nullable) và updatedAt (Instant). Record cũ thiếu owner giữ null và chỉ Operator đọc. updatedAt thiếu lấy createdAt. Metadata được giữ nguyên khi journal nhận ACK hoặc đối soát và cùng snapshot rollback/persistence.

MobilityRules là nguồn chung của các ngưỡng M3 về pin tối thiểu 20%, freshness 10 phút, occupancy quá tải 85% và ngưỡng thiếu xe 2.


### Đọc lịch dùng chung

UC-13 dựng lịch giữ từ deviceReceipts đã ACK theo sequence của cùng snapshot chứa chargingAllocations. Khi UC-07 tạo allocation từ lượt giữ, dùng chargingRequestId bằng reservationId của lệnh, cùng portId và cùng thời gian để scheduler nhận diện một lượt giữ đã có allocation. Các thao tác thêm receipt và xác nhận lịch tăng chung version datastore.

## Kho dữ liệu tích hợp Student/Auth/IoT

Giữ schema hợp nhất hiện tại và dữ liệu đang dùng. Session mới lưu tokenHash; StudentAccess băm raw Bearer token trước khi tra. StudentOperation tham chiếu studentId -> Student.accountId để gắn owner cho DeviceOperation. Các bản ghi dùng chung StateCoordinator/StoreSnapshot. Đợt tích hợp không ghi đè data.json hoặc data.example.json.
