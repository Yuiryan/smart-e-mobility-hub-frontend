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

- `VALIDATION_ERROR`: Lỗi dữ liệu đầu vào không hợp lệ (ví dụ: bán kính tìm kiếm $\le 0$).
- `AUTH_INVALID_CREDENTIALS`: Sai email hoặc mật khẩu.
- `AUTH_TOKEN_EXPIRED`: Phiên đăng nhập hết hạn (> 30 phút).
- `ACCESS_DENIED`: Không đủ quyền hạn truy cập tài nguyên.
- `RESOURCE_NOT_FOUND`: Không tìm thấy Hub, Xe hoặc Đơn đặt.
- `VEHICLE_ALREADY_RESERVED`: Xe đã được người khác đặt trước.
- `VEHICLE_NOT_RENTABLE`: Xe không đủ điều kiện cho thuê (pin yếu, hỏng hóc, hoặc mất telemetry).
- `ACTIVE_RIDE_EXISTS`: Sinh viên đang có chuyến đi chưa hoàn tất, không thể đặt xe mới.
- `IOT_COMMAND_FAILED`: Thiết bị IoT từ chối mở/khóa xe.
- `IOT_COMMAND_TIMEOUT`: Hết thời gian chờ phản hồi từ thiết bị IoT.
- `OPTIMISTIC_LOCK_CONFLICT`: Trạng thái thực thể đã bị cập nhật bởi tiến trình khác.

---

## 3. Phân Hệ Xác Thực & Tài Khoản (UC-00)

### 3.1. Đăng ký tài khoản Sinh viên

- **Endpoint:** `POST /api/auth/register`
- **Quyền:** Public
- **Request Body:**
  ```json
  {
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
    "password": "SecurePassword123"
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
  _(Lưu ý: Bắt buộc có `origin` hoặc `destination`; backend không tự suy đoán vị trí. Bán kính $0 < radiusKm \le 50$. Chỉ trả về xe `SHARED`, `AVAILABLE`, pin $\ge 20\%$, telemetry `FRESH`)._
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
- Hạn giữ xe mặc định là `createdAt + 10 phút`, độc lập với khung giờ thuê.
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
    "expiresAt": "2026-11-21T09:40:00Z",
    "createdAt": "2026-11-21T09:30:00Z"
  }
  ```
  _(Lưu ý: `pickupCode` chỉ được sinh và hiển thị 1 lần duy nhất trong response này)._
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
  _(Lưu ý: Pin còn 18.5% < 20% nên vehicleStatus tự động chuyển thành `LOW_BATTERY`)._

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
  _(Các actions hợp lệ: `FORCE_LOCK`, `FORCE_UNLOCK`, `SET_MAINTENANCE`, `RESTORE_SERVICE`)._
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
