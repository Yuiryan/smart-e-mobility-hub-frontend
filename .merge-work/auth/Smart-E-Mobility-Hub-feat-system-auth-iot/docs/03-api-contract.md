# 03 — REST/JSON API Contract

Tài liệu này là **Hợp đồng giao tiếp chính thức (Official API Contract)** giữa Frontend (Mobile App Sinh viên & Web Console Operator) và Backend. Mọi sự thay đổi về endpoint, request body, response body, error code, hoặc HTTP headers đều phải được cập nhật tại đây trước khi viết code.

---

## 1. Quy Ước Chung (Conventions)

- **Base URL:** `http://localhost:8080` (Môi trường phát triển cục bộ).
- **Mã hóa & Định dạng:** Toàn bộ payload gửi đi và nhận về sử dụng chuẩn `application/json; charset=utf-8`.
- **Định dạng Thời gian:** Chuẩn ISO-8601 UTC Instant: `YYYY-MM-DDTHH:mm:ssZ` (Ví dụ: `2026-11-21T09:30:00Z`).
- **Khoảng cách & Tỷ lệ:** Khoảng cách dùng đơn vị `km` (số thực); Phần trăm pin và chiếm dụng dùng thang số từ `0.0` đến `100.0`.
- **Mã trạng thái HTTP:**
  - `200 OK`: Truy vấn thành công hoặc thực thi đồng bộ hoàn tất.
  - `201 Created`: Tạo mới tài nguyên thành công (kèm URI hoặc đối tượng trả về).
  - `202 Accepted`: Yêu cầu đã được tiếp nhận và đang xử lý bất đồng bộ (chờ IoT ACK).
  - `204 No Content`: Thao tác thành công, không có body trả về (ví dụ: Logout).
  - `400 Bad Request`: Định dạng request không hợp lệ (sai JSON, ngoài dải số).
  - `401 Unauthorized`: Chưa đăng nhập hoặc token phiên hết hạn.
  - `403 Forbidden`: Đã đăng nhập nhưng không có quyền hạn (ví dụ: Sinh viên gọi API Operator).
  - `404 Not Found`: Không tìm thấy tài nguyên theo ID.
  - `409 Conflict`: Xung đột trạng thái (ví dụ: xe đã có người khác đặt, đụng version).
  - `422 Unprocessable Entity`: Dữ liệu đúng cú pháp nhưng vi phạm quy tắc nghiệp vụ (ví dụ: xe pin < 20%).
  - `503 Service Unavailable`: Thiết bị IoT hoặc simulator không phản hồi.

---

## 2. Chuẩn Phản Hồi Lỗi (Common Error Schema)

Tất cả các response lỗi (mã $4xx$, $5xx$) đều **BẮT BUỘC** trả về cấu trúc thống nhất sau:

```json
{
  "error": {
    "code": "VEHICLE_NOT_RENTABLE",
    "message": "Vehicle battery is below 20% or status is not AVAILABLE",
    "details": [
      {
        "field": "batteryPct",
        "issue": "Battery is 18.5%, minimum required is 20.0%"
      }
    ],
    "requestId": "c44c4abc-6570-4d8a-b157-04a09e22118a",
    "timestamp": "2026-11-21T09:30:00Z"
  }
}
```

### Bảng Mã Lỗi Chuẩn (Standard Error Codes):
- RESOURCE_ALREADY_EXISTS: HTTP 409, email đã được đăng ký.
- `VALIDATION_ERROR`: Lỗi dữ liệu đầu vào không hợp lệ (ví dụ: bán kính tìm kiếm $\le 0$).
- `AUTH_INVALID_CREDENTIALS`: Sai email hoặc mật khẩu.
- `AUTH_TOKEN_EXPIRED`: Phiên đăng nhập hết hạn khi thời điểm hiện tại lớn hơn hoặc bằng expiresAt. Hạn idle mặc định 30 phút.
- `ACCESS_DENIED`: Không đủ quyền hạn truy cập tài nguyên.
- `RESOURCE_NOT_FOUND`: Không tìm thấy Hub, Xe hoặc Đơn đặt.
- `VEHICLE_ALREADY_RESERVED`: Xe đã được người khác đặt trước.
- `VEHICLE_NOT_RENTABLE`: Xe không đủ điều kiện cho thuê (pin yếu, hỏng hóc, hoặc mất telemetry).
- `ACTIVE_RIDE_EXISTS`: Sinh viên đang có chuyến đi chưa hoàn tất, không thể đặt xe mới.
- `IOT_COMMAND_FAILED`: Thiết bị IoT từ chối mở/khóa xe.
- `IOT_COMMAND_TIMEOUT`: Hết thời gian chờ phản hồi từ thiết bị IoT.
- `OPTIMISTIC_LOCK_CONFLICT`: Trạng thái thực thể đã bị cập nhật bởi tiến trình khác.
- `METHOD_NOT_ALLOWED`: HTTP 405, endpoint không hỗ trợ HTTP method được gửi. Header `Allow` liệt kê method được hỗ trợ.
- `UNSUPPORTED_MEDIA_TYPE`: HTTP 415, Content-Type của request không được hỗ trợ.
- `AUTH_INVALID_TOKEN`: HTTP 401, token thiếu, sai định dạng hoặc không tương ứng với phiên hợp lệ.
- `INTERNAL_ERROR`: HTTP 500, lỗi server ngoài dự kiến. Response dùng thông báo cố định.
- `HTTP_ERROR`: Lỗi HTTP khác do framework xác định. HTTP status và các header liên quan được giữ từ exception.
- RECOVERY_DELIVERY_FAILED: HTTP 503, hệ thống gặp lỗi khi gửi email khôi phục.
---

## 3. Phân Hệ Xác Thực & Tài Khoản (UC-00)

### 3.1. Đăng ký tài khoản Sinh viên
- **Endpoint:** `POST /api/auth/register`
- **Quyền:** Public
- **Request Body:**
  ```json
  {
    "fullName": "Nguyen Van A",
    "studentId": "2212345",
    "email": "student@bku.edu.vn",
    "password": "SecurePassword123"
  }
  ```
- **Response `201 Created`:**
  ```json
  {
    "accountId": "acc_001",
    "studentId": "2212345",
    "email": "student@bku.edu.vn",
    "role": "STUDENT",
    "status": "ACTIVE",
    "createdAt": "2026-11-21T09:30:00Z"
  }
  ```

### 3.2. Đăng nhập hệ thống
- **Endpoint:** `POST /api/auth/login`
- **Quyền:** Public
- **Request Body:**
  ```json
  {
    "email": "student@bku.edu.vn",
    "password": "SecurePassword123",
    "role": "STUDENT"
  }
  ```
- **Response `200 OK`:**
  ```json
  {
    "sessionToken": "tok_9b2e4f8a1c7d",
    "tokenType": "Bearer",
    "expiresAt": "2026-11-21T10:00:00Z",
    "user": {
      "accountId": "acc_001",
      "studentId": "2212345",
      "email": "student@bku.edu.vn",
      "role": "STUDENT"
    }
  }
  ```

### 3.3. Đăng xuất
- **Endpoint:** `POST /api/auth/logout`
- **Quyền:** Authenticated (`Authorization: Bearer <sessionToken>`)
- **Response:** `204 No Content`

---


### 3.4. Danh tính đang đăng nhập

- **Endpoint:** GET /api/auth/me.
- **Quyền:** Authenticated, Authorization: Bearer <sessionToken>.
- **Response 200 OK**, header Cache-Control: no-store.

~~~json
{
  "accountId": "acc_001",
  "studentId": "2212345",
  "email": "student@bku.edu.vn",
  "role": "STUDENT"
}
~~~

Danh tính lấy từ Account được xác thực ở backend. Query accountId/studentId không chọn tài khoản khác.
Mỗi request có Bearer hợp lệ gia hạn idle expiry qua StateCoordinator.
Response không chứa tokenHash hoặc passwordHash.

### 3.5. Quy tắc HTTP security và CORS

- Register/login là public và bỏ qua Authorization cũ để người dùng có thể đăng nhập lại.
- GET /api/auth/me và POST /api/auth/logout yêu cầu Bearer hợp lệ.
- POST /api/search/hubs và GET /api/vehicles/* cho phép public theo contract khi các controller này được tích hợp.
- /api/operator/** yêu cầu OPERATOR. Các POST Student được liệt kê trong contract yêu cầu STUDENT.
- GET /api/operations/* yêu cầu đăng nhập. Service sở hữu operation phải kiểm tra owner khi endpoint được tích hợp.
- Các route khác bị từ chối mặc định. Anonymous nhận 401; tài khoản đã xác thực nhận 403.
- Role/status được đọc từ Account hiện hành trong mỗi request. Header gửi role/accountId không cấp quyền.
- Lỗi từ filter dùng error envelope chung, Cache-Control: no-store và WWW-Authenticate: Bearer cho 401.
- HTTP security không dùng JSESSIONID, form login hoặc Basic auth. Token được gửi bằng Authorization.
- Logout thu hồi session của caller và persist trước khi trả 204. Token đã thu hồi dùng lại trả 401 AUTH_INVALID_TOKEN.
- Logout gặp stale version trả 409 OPTIMISTIC_LOCK_CONFLICT. Lỗi persistence trả 500 INTERNAL_ERROR và rollback lần thu hồi.
- Xác thực trước logout gia hạn session trong một commit riêng. Nếu thu hồi thất bại, session vẫn tồn tại và có thể đã được gia hạn.
- CORS allowlist cấu hình bằng APP_CORS_ALLOWED_ORIGINS, danh sách phân cách dấu phẩy. Mặc định phát triển http://localhost:5173.
- Browser được gửi Authorization, Content-Type và Idempotency-Key. Hỗ trợ GET/POST/PUT/PATCH/DELETE/OPTIONS; quyền của endpoint vẫn theo route.
- Preflight hợp lệ không cần token. Origin/method/header ngoài allowlist nhận 403 ACCESS_DENIED. CORS không bật credentials/cookie.
- Header Location và WWW-Authenticate được expose cho frontend.
- Origin frontend thực tế cần đặt trong môi trường triển khai. Các rule CORS đã có HTTP integration test; browser integration theo môi trường của FE.

### Đổi mật khẩu khi đã đăng nhập

POST /api/auth/password/change

Quyền Authenticated, gửi Authorization Bearer token.

Body gồm currentPassword, newPassword, confirmPassword.
newPassword và confirmPassword phải khớp.
Mật khẩu mới tối thiểu 8 ký tự và tối đa 72 byte UTF-8.

Thành công trả 204 No Content và thu hồi mọi session của tài khoản.
Người dùng đăng nhập lại bằng mật khẩu mới.

Lỗi
- 400 VALIDATION_ERROR cho mật khẩu mới hoặc xác nhận không hợp lệ.
- 401 AUTH_INVALID_CREDENTIALS cho mật khẩu hiện tại sai.
- 401 AUTH_INVALID_TOKEN hoặc AUTH_TOKEN_EXPIRED cho lỗi phiên.
- 403 ACCESS_DENIED cho tài khoản không hoạt động.
- 409 OPTIMISTIC_LOCK_CONFLICT khi version thay đổi.
- 500 INTERNAL_ERROR khi persistence thất bại.


### Tiến trình khôi phục mật khẩu

Luồng tiếp theo của UC-00 gồm yêu cầu gửi mã, xác minh mã và đặt lại mật khẩu bằng quyền khôi phục phía server.
RecoveryCode lưu hash mã email. RecoverySession lưu hash token khôi phục, gắn với Account và đúng lần phát hành RecoveryCode.
Mô hình lưu trữ phục vụ các API khôi phục sắp triển khai được mô tả trong 04-data-schema.md.
Payload/status cụ thể sẽ được bổ sung trước khi nối các endpoint khôi phục.

### Yêu cầu khôi phục mật khẩu

- Endpoint POST /api/auth/password/recovery/request, quyền Public.
- Body gồm email hợp lệ.
- Trả 202 Accepted, body rỗng và Cache-Control: no-store.
- Email chưa đăng ký cũng nhận 202 với body rỗng.
- Mã mặc định gồm 6 chữ số, thời hạn mặc định 10 phút theo app.auth.recovery.code-timeout.
- Mã mới thay code và recovery session trước đó trong cùng coordinator commit.
- Response không trả mã hoặc hash.
- Lỗi persistence trả 500 INTERNAL_ERROR; version conflict trả 409 OPTIMISTIC_LOCK_CONFLICT.
- SMTP thất bại trả 503 RECOVERY_DELIVERY_FAILED. Code đã persist vẫn tồn tại; yêu cầu lại sẽ phát mã mới.
- Mail được gửi sau khi persist. Những response lỗi này khác với 202 của yêu cầu hoàn tất.
- SMTP phát triển mặc định localhost:1025; cấu hình bằng SMTP_HOST/PORT/USERNAME/PASSWORD/AUTH/STARTTLS/FROM.
- Frontend có thể gửi yêu cầu này khi token đăng nhập cũ đã hết hạn.


## 4. Phân Hệ Sinh Viên (Student Flow: UC-01 đến UC-07)

### UC-01: Tìm kiếm Hub và Xe khả dụng
- **Endpoint:** `POST /api/search/hubs`
- **Quyền:** Public / Authenticated
- **Request Body:**
  ```json
  {
    "origin": { "latitude": 10.7725, "longitude": 106.6598 },
    "destination": null,
    "radiusKm": 3.0
  }
  ```
  *(Lưu ý: Bắt buộc có `origin` hoặc `destination`; backend không tự suy đoán vị trí. Bán kính $0 < radiusKm \le 50$. Chỉ trả về xe `SHARED`, `AVAILABLE`, pin $\ge 20\%$, telemetry `FRESH`).*
- **Response `200 OK`:**
  ```json
  {
    "hubs": [
      {
        "hubId": "hub_01",
        "hubName": "BKU Ly Thuong Kiet Hub",
        "location": { "latitude": 10.7725, "longitude": 106.6598 },
        "distanceKm": 0.42,
        "availableVehicleCount": 2,
        "availableSpaceCount": 8,
        "telemetryStatus": "FRESH",
        "vehicles": [
          {
            "vehicleId": "bike_101",
            "type": "E_BIKE",
            "batteryPct": 85.5,
            "status": "AVAILABLE"
          }
        ]
      }
    ]
  }
  ```

### UC-02: Xem chi tiết Xe & Trạm
- **Endpoint:** `GET /api/vehicles/{vehicleId}`
- **Quyền:** Public / Authenticated
- **Response `200 OK`:**
  ```json
  {
    "vehicleId": "bike_101",
    "status": "AVAILABLE",
    "batteryLevel": 85.5,
    "updatedAt": "2026-11-21T09:29:50Z",
    "reason": null,
    "missingData": null
  }
  ```

### UC-03: Đặt trước Xe chia sẻ
- **Endpoint:** `POST /api/reservations`
- **Quyền:** `STUDENT`
- **Headers:** `Authorization: Bearer <sessionToken>`
- **Request Body:**
  ```json
  {
    "vehicleId": "bike_101",
    "startsAt": "2026-11-21T10:00:00Z",
    "endsAt": "2026-11-21T11:30:00Z"
  }
  ```
- `startsAt` and `endsAt` are required ISO-8601 UTC instants describing the student's requested rental start and planned return time. `endsAt` must be strictly later than `startsAt`. These are the requested rental window; they do not set the actual ride timestamps or the reservation hold expiry.
- A reservation hold still expires at `createdAt + 15 minutes`, independently of the requested rental window.
- **Response `201 Created`:**
  ```json
  {
    "reservationId": "res_001",
    "vehicleId": "bike_101",
    "hubId": "hub_01",
    "status": "ACTIVE",
    "pickupCode": "482910",
    "startsAt": "2026-11-21T10:00:00Z",
    "endsAt": "2026-11-21T11:30:00Z",
    "expiresAt": "2026-11-21T09:45:00Z",
    "createdAt": "2026-11-21T09:30:00Z"
  }
  ```
  *(Lưu ý: `pickupCode` chỉ được sinh và hiển thị 1 lần duy nhất trong response này).*
- **Hủy đặt xe:** `POST /api/reservations/{reservationId}/cancel` (cùng header xác thực) → `200 OK`.

### UC-04: Đặt trước Chỗ đỗ xe tại Hub
- **Endpoint:** `POST /api/parking-reservations`
- **Quyền:** `STUDENT`
- **Request Body:**
  ```json
  {
    "hubId": "hub_01",
    "spaceId": "space_05",
    "startsAt": "2026-11-21T10:00:00Z",
    "endsAt": "2026-11-21T10:30:00Z"
  }
  ```
- **Response `201 Created`:**
  ```json
  {
    "parkingReservationId": "pres_001",
    "hubId": "hub_01",
    "spaceId": "space_05",
    "status": "ACTIVE",
    "startsAt": "2026-11-21T10:00:00Z",
    "endsAt": "2026-11-21T10:30:00Z",
    "expiresAt": "2026-11-21T10:15:00Z"
  }
  ```

### UC-05: Mở khóa & Nhận xe (Bắt đầu chuyến đi)
- **Endpoint:** `POST /api/pickups`
- **Headers:** `Idempotency-Key: <UUID>` (Bắt buộc để chống trùng lặp khi retry).
- **Quyền:** `STUDENT`
- **Request Body (Một trong hai cách: có reservationId hoặc thuê trực tiếp):**
  ```json
  {
    "vehicleId": "bike_101",
    "pickupCode": "482910",
    "reservationId": "res_001"
  }
  ```
- **Trường hợp 1 — IoT Simulator ACK ngay lập tức (`201 Created`):**
  ```json
  {
    "rideId": "ride_001",
    "vehicleId": "bike_101",
    "status": "ACTIVE",
    "startHubId": "hub_01",
    "startedAt": "2026-11-21T09:30:05Z",
    "initialBatteryPct": 85.5
  }
  ```
- **Trường hợp 2 — IoT Simulator có độ trễ (`202 Accepted`):**
  ```json
  {
    "operationId": "op_unlock_987",
    "status": "PENDING",
    "statusUrl": "/api/operations/op_unlock_987",
    "retryAfterSeconds": 2
  }
  ```

### UC-06: Trả xe & Kết thúc chuyến đi
- **Endpoint:** `POST /api/rides/{rideId}/return`
- **Headers:** `Idempotency-Key: <UUID>`
- **Quyền:** `STUDENT`
- **Request Body:**
  ```json
  {
    "hubId": "hub_02",
    "spaceId": "space_12"
  }
  ```
- **Response `200 OK` (Sau khi IoT xác nhận khóa thành công):**
  ```json
  {
    "rideId": "ride_001",
    "vehicleId": "bike_101",
    "status": "COMPLETED",
    "endHubId": "hub_02",
    "endedAt": "2026-11-21T09:55:00Z",
    "initialBatteryPct": 85.5,
    "finalBatteryPct": 18.5,
    "vehicleStatus": "LOW_BATTERY"
  }
  ```
  *(Lưu ý: Pin còn 18.5% < 20% nên vehicleStatus tự động chuyển thành `LOW_BATTERY`).*

### UC-07: Đăng ký Sạc thông minh cho xe cá nhân
- **Endpoint:** `POST /api/charging-requests`
- **Quyền:** `STUDENT`
- **Request Body:**
  ```json
  {
    "vehicleId": "my_ebike_99",
    "hubId": "hub_01",
    "targetBatteryPct": 80.0,
    "requestedStartAt": "2026-11-21T10:00:00Z"
  }
  ```
- **Response `201 Created`:**
  ```json
  {
    "chargingRequestId": "chg_001",
    "hubId": "hub_01",
    "portId": "port_03",
    "status": "SCHEDULED",
    "targetBatteryPct": 80.0,
    "scheduledStartAt": "2026-11-21T10:00:00Z",
    "scheduledEndAt": "2026-11-21T10:45:00Z",
    "estimatedDurationMinutes": 45
  }
  ```

---

## 5. Phân Hệ Vận Hành (Operator Flow: UC-08 đến UC-14)

Tất cả các endpoint dưới đây đều yêu cầu quyền hạn **`OPERATOR`**.

### UC-08: Giám sát toàn diện mạng lưới (Network Report)
- **Endpoint:** `GET /api/operator/network-report`
- **Response `200 OK`:**
  ```json
  {
    "generatedAt": "2026-11-21T09:30:00Z",
    "totalHubs": 5,
    "totalVehicles": 120,
    "activeRides": 14,
    "lowBatteryVehicles": 8,
    "maintenanceVehicles": 2,
    "telemetryQuality": {
      "freshCount": 110,
      "staleCount": 8,
      "missingCount": 2
    },
    "hubs": [
      {
        "hubId": "hub_01",
        "hubName": "BKU Ly Thuong Kiet",
        "totalSpaces": 20,
        "occupiedSpaces": 18,
        "occupancyPct": 90.0,
        "availableVehicles": 2,
        "alertSeverity": "CRITICAL"
      }
    ]
  }
  ```

### UC-09: Can thiệp điều khiển từ xa (Operator Remote Intervention)
- **Endpoint:** `POST /api/operator/interventions`
- **Request Body:**
  ```json
  {
    "resourceType": "VEHICLE",
    "resourceId": "bike_101",
    "action": "FORCE_LOCK",
    "reason": "Suspected out-of-boundary violation"
  }
  ```
  *(Các actions hợp lệ: `FORCE_LOCK`, `FORCE_UNLOCK`, `SET_MAINTENANCE`, `RESTORE_SERVICE`).*
- **Response `200 OK`:**
  ```json
  {
    "interventionId": "itv_001",
    "resourceId": "bike_101",
    "action": "FORCE_LOCK",
    "status": "APPLIED",
    "executedAt": "2026-11-21T09:30:00Z"
  }
  ```

### UC-10: Cảnh báo Quá tải & Thiếu hụt (Alerts)
- **Endpoint:** `GET /api/operator/alerts`
- **Query Params:** `?severity=CRITICAL&status=ACTIVE`
- **Response `200 OK`:**
  ```json
  {
    "alerts": [
      {
        "alertId": "alt_001",
        "hubId": "hub_01",
        "severity": "CRITICAL",
        "type": "HIGH_OCCUPANCY",
        "message": "Hub occupancy reached 90.0% (threshold: 85.0%)",
        "createdAt": "2026-11-21T09:25:00Z",
        "acknowledged": false
      }
    ]
  }
  ```

### UC-11: Điều phối luân chuyển xe (Dispatch Orders)
- **Endpoint:** `POST /api/operator/dispatch-orders`
- **Request Body:**
  ```json
  {
    "sourceHubId": "hub_01",
    "destinationHubId": "hub_02",
    "vehicleIds": ["bike_101", "bike_102", "bike_103"]
  }
  ```
- **Response `201 Created`:**
  ```json
  {
    "dispatchOrderId": "dsp_001",
    "sourceHubId": "hub_01",
    "destinationHubId": "hub_02",
    "vehicleIds": ["bike_101", "bike_102", "bike_103"],
    "status": "IN_TRANSIT",
    "createdAt": "2026-11-21T09:30:00Z"
  }
  ```
- **Xác nhận đến trạm đích:** `POST /api/operator/dispatch-orders/{id}/arrival` → Chuyển xe về `AVAILABLE`.
- **Hủy lệnh:** `POST /api/operator/dispatch-orders/{id}/cancel`.

### UC-12: Quản lý & Xử lý Sự cố (Incidents)
- **Ghi nhận sự cố:** `POST /api/operator/incidents`
  - Body: `{ "targetType": "VEHICLE", "targetId": "bike_101", "description": "Flat front tire", "severity": "WARNING" }`
  - Tự động chuyển xe/cổng sang `MAINTENANCE`.
- **Giải quyết sự cố:** `POST /api/operator/incidents/{id}/resolve`
  - Khôi phục trạng thái về `AVAILABLE` (nếu pin $\ge 20\%$) hoặc `LOW_BATTERY` (nếu pin $< 20\%$).

### UC-13: Bộ lập lịch sạc thông minh (Smart Charging Scheduler)
- **Endpoint:** `POST /api/operator/charging-schedules`
- **Request Body:** `{ "hubId": "hub_01" }`
- **Response `200 OK`:** Trả về danh sách phân bổ cổng sạc tối ưu, ưu tiên các xe có `batteryPct < 20.0%` trước để kịp thời đưa vào phục vụ.

### UC-14: Mô phỏng Kịch bản Giả lập (What-if Simulation)
- **Endpoint:** `POST /api/operator/simulations`
- **Request Body:**
  ```json
  {
    "scenarioName": "Peak Rush Hour Influx",
    "parameters": {
      "simulatedDurationHours": 2,
      "rentalMultiplier": 2.5,
      "hubOutageId": null
    }
  }
  ```
- **Response `200 OK`:**
  ```json
  {
    "simulationId": "sim_001",
    "status": "COMPLETED",
    "projectedOverloadHubs": ["hub_01", "hub_03"],
    "recommendedDispatch": [
      {
        "fromHub": "hub_04",
        "toHub": "hub_01",
        "vehicleCount": 5
      }
    ],
    "disclaimer": "Simulated in isolated in-memory snapshot. Live system state untouched."
  }
  ```

---

## 6. Tài Nguyên Thao Tác Bất Đồng Bộ (`/api/operations/{id}`)

Khi một yêu cầu trả về mã `202 Accepted`, Client dùng URL được chỉ định để kiểm tra trạng thái cuối cùng:
- **Endpoint:** `GET /api/operations/{operationId}`
- **Response `200 OK`:**
  ```json
  {
    "operationId": "op_unlock_987",
    "type": "UNLOCK_VEHICLE",
    "status": "SUCCEEDED",
    "createdAt": "2026-11-21T09:30:00Z",
    "updatedAt": "2026-11-21T09:30:02Z",
    "result": {
      "rideId": "ride_001",
      "vehicleId": "bike_101"
    },
    "error": null
  }
  ```

### Hoàn tất khôi phục mật khẩu (Public)

| POST | Request JSON | Thành công |
|---|---|---|
| /api/auth/password/recovery/verify | email, code (chuỗi 6 chữ số) | 200, JSON recoveryToken và expiresAt (UTC) |
| /api/auth/password/recovery/reset | recoveryToken, newPassword, confirmPassword | 204, body rỗng |

Response thành công có Cache-Control: no-store. FE giữ recoveryToken để gửi trong body reset. Nếu mất token sau verify, yêu cầu mã email mới. Mỗi mã chỉ cấp một token. Nhập sai đủ 5 lần phải yêu cầu mã mới. Token hết hạn cùng mã. Mật khẩu mới và xác nhận áp dụng cùng chính sách với API đổi mật khẩu. Sai định dạng trả 400 VALIDATION_ERROR. Thông tin recovery sai, hết hạn, bị khóa hoặc đã dùng trả 400 INVALID_RECOVERY_CREDENTIAL. Xung đột version trả 409 OPTIMISTIC_LOCK_CONFLICT.

## Lệnh thiết bị và cảm biến trạm

Gateway nội bộ giữ commandId khi retry qua restart. Operation chưa có ACK xác thực tiếp tục chờ đối soát. Service có thể đọc số ô đang dùng và trạng thái/công suất đo của cổng qua StationSensors.

## Gateway IoT dùng chung cho M3

Gateway sạc nội bộ cung cấp readHubStatus(hubId), readPort(hubId,portId), reservePort(hubId,portId,reservationId,startAt,endAt,commandId), releasePort(hubId,portId,reservationId,commandId), getResult(commandId) và reconcile(commandId). Kết quả là DeviceResponse mang đầy đủ command, status, observedAt, batteryLevel và reason.

Giữ cổng mới cần cảm biến FRESH và AVAILABLE. Khoảng [startAt,endAt) được phép tiếp giáp, không được chồng lấn. startAt trước thời điểm nhận bị từ chối. reservationId dùng duy nhất cho một lần đặt và cùng đích/khoảng thời gian. Hủy chỉ áp dụng đúng reservationId đã ACK trên cùng hub/cổng. Retry dùng lại commandId và payload.

IoTGateway.forceLock(vehicleId,commandId) hỗ trợ thiết bị khóa tại vị trí hiện tại cho UC-09/12. Service nghiệp vụ chịu trách nhiệm xác thực quyền can thiệp.

reconcile là truy vấn biên nhận thực thi của thiết bị và có thể giải quyết ACK mất hoặc đến trễ. Query getResult tiếp tục mô phỏng chính sách giao ACK. Khi khởi động lại, backend đối soát operation chưa terminal bằng biên nhận. Intent thiếu biên nhận tiếp tục AWAITING_RECONCILIATION. Các gateway này được service gọi trong backend.

## UC-13 — Proposal, chỉnh sửa và xác nhận

Chỉ OPERATOR hoạt động được gọi. Mọi response thành công HTTP 200.

| Method/path | Body | Response |
|---|---|---|
| POST /api/operator/charging-schedules | hubId | ScheduleProposal |
| GET /api/operator/charging-schedules/{id} | | ScheduleProposal |
| PUT /api/operator/charging-schedules/{id}/slots/{slotId} | revision (bắt buộc), startAt, endAt | ScheduleProposal với revision mới, valid và violations |
| POST /api/operator/charging-schedules/{id}/confirm | revision (bắt buộc) | ChargingSchedule |

ScheduleProposal gồm id, hubId, createdAt, revision, slots, queue, violations, valid, confirmedScheduleId. Slot gồm id, requestId, vehicleId, portId, startAt, endAt, powerKw. Queue gồm requestId (có thể null), vehicleId, reason.

ChargingSchedule gồm id, proposalId, hubId, operatorId, confirmedAt, allocationIds. Confirm lại proposal đã xác nhận trả cùng schedule. Edit proposal đã xác nhận trả 409 SCHEDULE_ALREADY_CONFIRMED. Thiếu quyền trả 403 ACCESS_DENIED, không có tài nguyên trả 404 RESOURCE_NOT_FOUND. revision/version sai trả 409 OPTIMISTIC_LOCK_CONFLICT. Proposal không hợp lệ trả 409 SCHEDULE_CONFLICT khi confirm. Sai thời gian/format trả 400 VALIDATION_ERROR. Lỗi persistence trả 500 INTERNAL_ERROR.

Slot tự sinh căn phút và bắt đầu sau ít nhất một phút. Ưu tiên xe pin dưới 20%, sau đó thời điểm sử dụng kế tiếp, pin tăng dần và requestId. Dữ liệu thiếu nằm trong queue và violations. Chỉnh sửa slot lưu draft để người vận hành sửa tiếp; allocation chỉ được ghi khi confirm. Tổng công suất tính trên mọi khoảng giao nhau, xét thời gian nửa mở [startAt,endAt).

## UC-14 — Chi tiết mô phỏng What-if

POST /api/operator/simulations yêu cầu OPERATOR hoạt động, body giữ scenarioName và parameters {simulatedDurationHours,rentalMultiplier,hubOutageId}. scenarioName dài 1–120 ký tự không trắng. duration hữu hạn >0 và <=168 giờ, multiplier hữu hạn từ 0 tới100. hubOutageId null hoặc hub tồn tại và đủ dữ liệu. Cấu hình app.simulation.lookback mặc định PT24H, step PT15M, consumption-percent-per-km 1.0 và max-generated-trips 100000. Đây là tham số mô hình triển khai.

Response 200 giữ simulationId,status,projectedOverloadHubs,recommendedDispatch,disclaimer. Bổ sung sourceVersion,generatedAt,failedBookingsCount,failedReturnsCount,congestionMetrics (peak occupancy % theo hub),missingData. Mỗi recommendedDispatch có proposalId,fromHub,toHub,vehicleCount.

Nhu cầu được suy từ tuyến Ride hoàn tất trong lookback, nhân rentalMultiplier; thời gian và quãng đường lấy trung bình theo tuyến. Thiếu tuyến/quãng đường được ghi missingData. Khi không có tuyến hợp lệ, kết quả có MISSING_DEMAND_BASELINE; các chỉ số chỉ phản ánh trạng thái ban đầu và chuyến đang chạy đủ dữ liệu. Thuê thất bại do thiếu xe hoặc outage làm tăng failedBookingsCount. Trả thất bại do đầy chỗ hoặc outage tăng failedReturnsCount; xe đó không được thêm vào danh sách khả dụng.

Sai input trả 400 VALIDATION_ERROR, vượt giới hạn lượt dự báo trả 400 SIMULATION_TOO_LARGE, outage hub không tồn tại/thiếu dữ liệu trả 404 RESOURCE_NOT_FOUND. Đề xuất điều phối cần được M2 kiểm tra lại trước khi áp dụng thực tế.

## Poll operation thiết bị và ownership

GET /api/operations/{id} dùng account từ Bearer session. Owner hoặc Operator được đọc, người khác trả 403 ACCESS_DENIED; không tồn tại trả 404 RESOURCE_NOT_FOUND. Response 200 có Cache-Control: no-store và operationId,type,status,createdAt,updatedAt,result,error.

Các type thiết bị gồm UNLOCK_VEHICLE, LOCK_VEHICLE, FORCE_LOCK_VEHICLE, RESERVE_CHARGING_PORT, RELEASE_CHARGING_PORT. ACK thiết bị ánh xạ SUCCEEDED. Kết quả thành công có vehicleId/spaceId hoặc hubId/portId/reservationId tùy lệnh. PENDING có result,error null. FAILED có error.code IOT_COMMAND_FAILED. AWAITING_RECONCILIATION có error.code IOT_RECONCILIATION_REQUIRED. Các kết quả này mô tả lệnh thiết bị. rideId và kết quả nghiệp vụ nhận/trả xe do service M1 xác nhận sau ACK.

Service gọi overload gateway có accountId lấy từ CurrentAccount. API không nhận owner từ query/body. Retry giữ cùng accountId và command payload. Operation cũ thiếu accountId chỉ Operator đọc được. FORCE_LOCK có owner yêu cầu account Operator hoạt động.

Điểm bàn giao UC-13 là ScheduleProposal/ChargingSchedule; UC-14 là recommendedDispatch để service dispatch của M2 kiểm tra lại trước áp dụng. Kiểm tra quyền sở hữu reservation, ride và charging request diễn ra tại service M1 khi tích hợp nhánh.

Student bị từ chối khi giữ/giải phóng reservationId đã có operation RESERVE_PORT của tài khoản khác ở trạng thái chưa FAILED. Operator có thể xử lý can thiệp theo service nghiệp vụ.


### Kiểm tra lịch UC-07 và UC-13

Giữ cổng bị từ chối nếu trùng một allocation ACTIVE hoặc RESERVED trên cùng cổng. Kiểm tra dùng khoảng nửa mở. UC-13 tính cả lượt giữ cổng thiết bị còn hiệu lực khi đề xuất và xác nhận. Lượt giữ chưa có allocation tương ứng chiếm công suất định mức của cổng trong thời gian giữ.
