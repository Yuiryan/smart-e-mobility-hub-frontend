# 06 — Test Plan & Quality Assurance

Tài liệu này định hình chiến lược kiểm thử chất lượng toàn diện (Test Strategy), ma trận các ca kiểm thử chi tiết theo từng Use Case (dựa trên Submission #3 Mục F), các ca kiểm thử biên (Boundary), xử lý dữ liệu thiếu (Missing Data), kiểm thử đồng thời (Concurrency), và Tiêu chuẩn Hoàn thành (Definition of Done - DoD) của dự án **Smart E-Mobility Hub**.

---

## 1. Chiến Lược Kiểm Thử (Testing Pyramid Strategy)

Hệ thống áp dụng mô hình Kim tự tháp kiểm thử 3 tầng:

```
          / \
         /   \       End-to-End & Concurrency Tests (HT.1 - HT.6)
        / E2E \      - Kiểm thử đa luồng, tranh chấp xe, khôi phục đĩa
       /───────\
      /         \     Integration / Component Tests (Mock MVC & Store)
     /Integrat'n \    - Kiểm thử REST Endpoints, Serialization JSON, Filter
    /─────────────\
   /   Unit Tests  \  Domain & Service Unit Tests
  /─────────────────\ - Business rules thuần túy, tính pin, Haversine, timeout
```

- **Unit Tests:** Kiểm thử các quy tắc nghiệp vụ độc lập, logic máy trạng thái, kiểm tra ngưỡng pin $\ge 20\%$, tính toán khoảng cách Haversine. Không phụ thuộc vào Spring context.
- **Integration Tests:** Dùng `@WebMvcTest` hoặc `@SpringBootTest` kết hợp `InMemoryDataStore` để kiểm tra đúng HTTP Status Code, format JSON Response, ánh xạ Exception sang Error DTO.
- **Concurrency & Non-Interactive Tests (HT.1 – HT.6):** Sử dụng `ExecutorService`, `CountDownLatch` để kiểm tra khả năng chịu tải đồng thời, tính nguyên tử của `StateCoordinator`, và kiểm tra toàn vẹn file `data/data.json`.

---

## 2. Tiêu Chuẩn Hoàn Thành (Definition of Done - DoD)

Một tính năng (Feature / PR) chỉ được coi là hoàn thành khi thỏa mãn **100%** các tiêu chí sau:

1. **Tuân thủ AGENTS.md:** Đã có Feature Plan trong `docs/plans/` được duyệt; không tự ý đổi schema/API ngoài scope.
2. **Biên dịch & Đóng gói:** Lệnh `.\mvnw.cmd clean package` hoàn thành thành công mà không có cảnh báo nghiêm trọng.
3. **Độ bao phủ kiểm thử (Test Coverage):**
   - 100% các Use Case flow chính (Happy Path) có test tự động.
   - 100% các ngưỡng biên (Boundary conditions) có unit test kiểm chứng.
   - 100% các trường hợp dữ liệu rác/sai (Negative cases) trả về đúng mã lỗi chuẩn (`400`, `404`, `409`, `422`).
4. **Không nuốt lỗi:** Không có khối `try-catch` rỗng hoặc trả về `null` không giải thích.
5. **Đồng bộ tài liệu:** Đã cập nhật `docs/03-api-contract.md` và `docs/04-data-schema.md` nếu có thay đổi liên quan.

---

## 3. Ma Trận Test Case Chi Tiết Theo Use Case

Dưới đây là ma trận test case kế thừa và chuẩn hóa từ **Submission #3 - Section F**:

### 3.1. Phân hệ Xác thực & Tài khoản (UC-00)
| Mã Test | Tên Test Case | Đầu vào (Input) | Kết quả kỳ vọng (Expected) | Loại test |
|---|---|---|---|:---:|
| `TC-UC00-01` | Đăng ký sinh viên hợp lệ | `studentId="2212345"`, `email="a@bku.edu.vn"`, pass mạnh | `201 Created`, Account status `ACTIVE`, mật khẩu được hash. | Happy |
| `TC-UC00-02` | Đăng ký email trùng lặp | Email đã tồn tại trong hệ thống | `409 Conflict`, code `RESOURCE_ALREADY_EXISTS`. | Negative |
| `TC-UC00-03` | Đăng nhập đúng thông tin | Email và mật khẩu chính xác | `200 OK`, trả về `sessionToken`, hạn 30 phút. | Happy |
| `TC-UC00-04` | Đăng nhập sai mật khẩu | Mật khẩu không khớp | `401 Unauthorized`, code `AUTH_INVALID_CREDENTIALS`. | Negative |
| `TC-UC00-05` | Truy cập với token hết hạn | Token đã quá 30 phút | `401 Unauthorized`, code `AUTH_TOKEN_EXPIRED`. | Boundary |
| `TC-UC00-06` | Sinh viên gọi API Operator | Token của sinh viên gọi `/api/operator/*` | `403 Forbidden`, code `ACCESS_DENIED`. | Security |

### 3.2. Phân hệ Sinh viên (UC-01 đến UC-07)
| Mã Test | Tên Test Case | Đầu vào (Input) | Kết quả kỳ vọng (Expected) | Loại test |
|---|---|---|---|:---:|
| `TC-UC01-01` | Tìm Hub trong bán kính hợp lệ | `origin=(10.77, 106.66)`, `radiusKm=3.0` | `200 OK`, danh sách Hub sắp xếp theo khoảng cách tăng dần. | Happy |
| `TC-UC01-02` | Lọc xe pin dưới 20% | Hub có xe pin 19.9% và 20.0% | Chỉ xe pin 20.0% hiển thị; xe 19.9% bị loại khỏi kết quả. | Boundary |
| `TC-UC01-03` | Lọc xe có telemetry STALE | Xe có `observedAt` cách hiện tại 10p01s | Xe bị loại khỏi danh sách cho thuê, telemetry trạm báo `STALE`. | Safety |
| `TC-UC01-04` | Bán kính không hợp lệ | `radiusKm = -1.0` hoặc `radiusKm = 0.0` | `400 Bad Request`, code `VALIDATION_ERROR`. | Negative |
| `TC-UC01-05` | Bán kính vượt ngưỡng tối đa | `radiusKm = 50.1` (max cho phép 50km) | `400 Bad Request`, code `VALIDATION_ERROR`. | Boundary |
| `TC-UC02-01` | Xem chi tiết xe tồn tại | `vehicleId = "bike_101"` | `200 OK`, đầy đủ telemetry chất lượng `FRESH`. | Happy |
| `TC-UC02-02` | Xem chi tiết xe không tồn tại | `vehicleId = "invalid_id"` | `404 Not Found`, code `RESOURCE_NOT_FOUND`. | Negative |
| `TC-UC03-01` | Đặt xe hợp lệ | Xe `AVAILABLE`, pin 80%, sinh viên chưa có đơn | `201 Created`, xe thành `RESERVED`, trả về `pickupCode` 6 số. | Happy |
| `TC-UC03-02` | Đặt xe đang có người khác giữ | Xe đang ở trạng thái `RESERVED` | `409 Conflict`, code `VEHICLE_ALREADY_RESERVED`. | Conflict |
| `TC-UC03-03` | Sinh viên đặt 2 xe cùng lúc | Sinh viên đã có 1 đơn `ACTIVE` | `409 Conflict`, code `ACTIVE_RIDE_EXISTS`. | Rule |
| `TC-UC03-04` | Hết hạn giữ chỗ 15 phút | Đơn tạo lúc $T$, `JobScheduler` chạy lúc $T+15m01s$ | Đơn chuyển `EXPIRED`, xe tự động hoàn về `AVAILABLE`. | Scheduler |
| `TC-UC04-01` | Đặt chỗ đỗ không trùng giờ | Khoảng giờ trống tại ô `space_01` | `201 Created`, lưu `ParkingReservation`. | Happy |
| `TC-UC04-02` | Đặt chỗ đỗ trùng khoảng giờ | Khoảng giờ bị overlap với đơn đã có | `409 Conflict`, từ chối đặt chỗ. | Boundary |
| `TC-UC05-01` | Mở xe thành công bằng mã số | Nhập đúng `pickupCode` 6 số, IoT ACK | `201 Created`, tạo `Ride` mới, xe chuyển `IN_USE`. | Happy |
| `TC-UC05-02` | Nhập sai mã nhận xe | Nhập mã không khớp hash trong DB | `422 Unprocessable Entity`, không mở khóa xe. | Negative |
| `TC-UC05-03` | Mở khóa chưa xác định | Timeout hoặc UNKNOWN | Chờ đối soát theo requestId, chưa tạo Ride. | IoT Failure |
| `TC-UC05-04` | Gửi lại với cùng Idempotency-Key| Request gửi 2 lần cùng UUID và payload | Lần 2 trả lại kết quả lần 1 (`201`), không tạo 2 Ride trùng lặp. | Idempotency |
| `TC-UC06-01` | Trả xe pin $\ge 20\%$ | Trả xe tại hub, pin còn 45% | `200 OK`, `Ride` thành `COMPLETED`, xe thành `AVAILABLE`. | Happy |
| `TC-UC06-02` | Trả xe pin $< 20\%$ | Trả xe tại hub, pin còn 15% | `200 OK`, `Ride` thành `COMPLETED`, xe thành `LOW_BATTERY`. | Business Rule |
| `TC-UC06-03` | Trả xe ngoài phạm vi trạm | Tọa độ GPS của xe cách trạm $> 100$m | `422 Unprocessable Entity`, từ chối kết thúc chuyến đi. | Boundary |
| `TC-UC07-01` | Đăng ký sạc trong giới hạn công suất | Công suất yêu cầu không làm vượt `maxChargingPowerKw` | `201 Created`, cổng sạc chuyển sang `SCHEDULED`. | Happy |
| `TC-UC07-02` | Đăng ký sạc vượt quá công suất trạm | Trạm đã dùng hết 100% công suất tải | `422 Unprocessable Entity`, thông báo trạm quá tải sạc. | Capacity |
| `TC-UC02-03` | Làm mới khi xe vừa được đặt | Sự kiện đặt đúng xe, sau đó mẫu IoT cũ đến | Hiển thị RESERVED của xe đang xem. | Event |
| `TC-UC04-03` | Hai lượt giữ chạm biên | Lượt trước kết thúc khi lượt sau bắt đầu | Hai khoảng không giao nhau. | Boundary |
| `TC-UC05-05` | Nhận xe trực tiếp | QR xe hợp lệ, khung giờ hợp lệ và ACK khớp | Tạo một Ride có reservationId rỗng. | Happy |
| `TC-UC06-04` | Thiếu pin cuối | ACK khóa đúng xe và ô, thiếu batteryLevel | Chờ đối soát, Ride chưa hoàn tất. | Safety |
| `TC-UC06-05` | Báo sự cố khi trả | ACK khớp, pin 45%, issueReported=true | Xe chuyển MAINTENANCE. | Rule |

### 3.3. Phân hệ Vận hành & Hệ thống (UC-08 đến UC-14)
| Mã Test | Tên Test Case | Đầu vào (Input) | Kết quả kỳ vọng (Expected) | Loại test |
|---|---|---|---|:---:|
| `TC-UC08-01` | Báo cáo mạng lưới thời gian thực | Gọi API giám sát toàn mạng | `200 OK`, tính đúng tổng xe, số trạm, tỷ lệ chiếm dụng. | Happy |
| `TC-UC09-01` | Operator cưỡng bức khóa xe | Gửi lệnh `FORCE_LOCK` cho `bike_101` | `200 OK`, trạng thái xe cập nhật, ghi nhận audit log. | Happy |
| `TC-UC09-02` | Operator chuyển xe sang bảo trì | Gửi lệnh `SET_MAINTENANCE` | `200 OK`, xe chuyển `MAINTENANCE`, lập tức rút khỏi tìm kiếm. | Safety |
| `TC-UC10-01` | Kích hoạt cảnh báo quá tải trạm | Trạm có $17/20$ ô bị chiếm ($85.0\%$) | Hệ thống tự phát sinh `SystemAlert` với severity `CRITICAL`. | Threshold |
| `TC-UC10-02` | Kích hoạt cảnh báo thiếu hụt xe | Trạm chỉ còn 2 xe sẵn sàng phục vụ | Phát sinh `SystemAlert` với severity `WARNING`. | Threshold |
| `TC-UC11-01` | Tạo lệnh điều phối xe tải | Chuyển 3 xe từ `hub_01` sang `hub_02` | `201 Created`, 3 xe chuyển sang trạng thái `IN_TRANSIT`. | Flow |
| `TC-UC11-02` | Hoàn tất lệnh điều phối | Xác nhận xe đã đến `hub_02` | Xe chuyển về `AVAILABLE`, cập nhật vị trí mới. | Flow |
| `TC-UC12-01` | Báo cáo sự cố xe hỏng | Ghi nhận hỏng hóc cho `bike_101` | `Incident` được lưu, xe tự động chuyển sang `MAINTENANCE`. | Flow |
| `TC-UC12-02` | Khắc phục xong sự cố | Bấm resolve incident | Xe trở lại `AVAILABLE` (nếu pin $\ge 20\%$) hoặc `LOW_BATTERY` (pin $<20\%$). | Flow |
| `TC-UC13-01` | Lập lịch sạc ưu tiên pin yếu | Danh sách xe cần sạc có xe 10% và 60% | Thuật toán xếp lịch cấp cổng cho xe 10% pin trước. | Optimization |
| `TC-UC14-01` | Tính cách ly của mô phỏng What-if | Chạy kịch bản mô phỏng cúp điện trạm | Mô phỏng trả kết quả dự báo; dữ liệu thật trong RAM và `data.json` **hoàn toàn không đổi**. | Isolation |

---

## 4. Các Ca Kiểm Thử Phi Chức Năng & Đồng Thời (HT.1 đến HT.6)

Các test case mức hệ thống kế thừa từ mục F.3 của Submission #3:

| Mã Test | Tên Test Case | Mục đích kiểm tra | Điều kiện thành công |
|---|---|---|---|
| `HT.1` | **Tranh chấp đồng thời khi đặt cùng 1 xe (Race Condition)** | 10 luồng cùng lúc gửi request đặt xe `bike_101` tại cùng một mili-giây. | **Chính xác 01 request nhận `201 Created`**, 09 request còn lại nhận `409 Conflict`. Không bao giờ có 2 người cùng đặt 1 xe. |
| `HT.2` | **Toàn vẹn khi ghi file snapshot song song (Atomic Persistence)** | Liên tục kích hoạt 50 giao dịch commit trong khi đĩa đang ghi. | File `data/data.json` luôn đọc được hợp lệ, không bị lỗi cú pháp JSON dở dang. |
| `HT.3` | **Khôi phục dữ liệu sau khi khởi động lại (State Recovery)** | Tạo 5 trạm và 20 xe, tắt server, khởi động lại ứng dụng. | Toàn bộ 5 trạm và 20 xe được nạp nguyên vẹn từ `data/data.json` vào RAM. |
| `HT.4` | **Chịu tải tìm kiếm xe (Search Read Concurrency)** | 100 luồng đồng thời gọi `POST /api/search/hubs`. | 100% requests trả về `200 OK` với độ trễ phản hồi $P95 < 50$ms. |
| `HT.5` | **Chống trùng lặp lệnh IoT bằng Idempotency-Key** | Gửi 5 request mở khóa xe cùng 1 mã `Idempotency-Key`. | Thiết bị IoT chỉ nhận đúng 01 lệnh mở khóa cơ; 4 request sau trả lại kết quả đã lưu mà không mở lại. |
| `HT.6` | **Khả năng tự phục hồi khi IoT Simulator chậm trễ (Resilience)** | Giả lập Simulator có độ trễ ngẫu nhiên từ 1 đến 4 giây. | Hệ thống trả về `202 Accepted` với `statusUrl`, client polling lấy kết quả thành công mà không bị nghẽn connection pool. |

---

## 5. Hướng Dẫn Chạy Bộ Test

Để chạy toàn bộ test suite từ dòng lệnh:

```powershell
# Chạy toàn bộ Unit & Integration tests
.\mvnw.cmd test

# Chạy riêng một nhóm test case (ví dụ UC-01)
.\mvnw.cmd test -Dtest=HubSearchControllerTest

# Chạy build đóng gói kèm kiểm tra toàn diện
.\mvnw.cmd clean package
```

