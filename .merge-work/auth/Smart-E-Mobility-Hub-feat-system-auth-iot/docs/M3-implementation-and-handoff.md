# M3 — Lịch sử hiện thực và bàn giao kỹ thuật

## Trạng thái tại lúc rà soát

Nhánh `feat/system-auth-iot`, HEAD `0300420`. Nhánh tracking cùng commit. Phần operation ownership đang nằm trong working tree và cần được commit/push để có mặt trên GitHub. Báo cáo Surefire của đợt chạy lúc khoảng 03:54 ghi nhận 185 ca, 0 failures, 0 errors, 0 skipped. Chưa xác nhận package mới từ hiện trạng hoặc chạy test sau khi ghép M1/M2. Rà soát M1 `58c5985` và M2 `d9f47a9` theo các ref tracking sẵn có tại máy.

## Các mốc từ đầu M3

| Mốc | Thay đổi |
|---|---|
| `c390651` đến `160e5b9` | DTO, model Account, repository qua interface, đăng ký Student, BCrypt, chuẩn hóa lỗi |
| `9b4b9f0` đến `1f2eacd` | DTO đăng nhập, token ngẫu nhiên, tra/xóa phiên, cấp phiên thay thế, gia hạn idle và chặn enum dạng số |
| `3af4263`, `8fd8caf` | Hợp đồng UC-00, snapshot/rollback, Clock, Bearer filter, role, logout và CORS |
| `029ce57`, `34c84b4` | Đổi mật khẩu, gửi mã email, xác minh và reset |
| `d859165` đến `39db52f` | Cache telemetry, tick, lệnh khóa/mở với ACK gắn đúng lệnh |
| `800e916`, `0f6a33e` | ACK trễ, timeout, từ chối hoặc mất ACK có thể tái hiện |
| `656018e`, `a2c6b33` | Cảm biến trạm/cổng, journal, durable receipt, giữ/giải phóng cổng và đối soát |
| `b6bc4cb`, `0300420` | UC-13 lập lịch và UC-14 What-if |
| Working tree | Owner operation, polling HTTP, HTTP 403/404, ngưỡng chung và test tích hợp |

## Xác thực và phân quyền

- `AccountController` dùng Spring Web `@RestController`, `@RequestMapping`, `@PostMapping`, `@GetMapping` để khai báo route và để Spring chuyển JSON thành DTO. `ResponseEntity` dùng khi cần trả HTTP 201, 202 hoặc 204 và các header như `Cache-Control`. Jakarta Validation `@Valid`, `@NotBlank`, `@Email`, `@NotNull` kiểm tra hình dạng request ở biên HTTP. `AuthService` kiểm tra dữ liệu nghiệp vụ như tài khoản trùng, vai trò và điều kiện mật khẩu.
- `reg()` chuẩn hóa email và cố định role STUDENT. Kiểm tra trùng và ghi Account đặt trong cùng `StateCoordinator.commit` để hai lần đăng ký đồng thời không dùng chung email. `AccountDataStore` là interface để service có thể dùng repository hiện tại và thay thế trong test.
- `PasswordEncoder` dùng BCrypt để lưu hash có salt. `PasswordPolicy` giới hạn tối thiểu 8 ký tự và tối đa 72 byte UTF-8 theo BCrypt. Phân biệt ký tự với byte vì một ký tự Unicode có thể dùng nhiều byte.
- `login()` lấy version trước khi đọc Account, kiểm tra hash, role và status, rồi thu hồi phiên cũ và cấp phiên mới trong cùng commit. `SecureRandom` sinh 32 byte, `Base64` biểu diễn URL-safe, `MessageDigest` SHA-256 và `HexFormat` tạo khóa tra phiên. Token entropy cao có thể dùng SHA-256 khi lưu. Mật khẩu và mã email ít entropy hơn dùng BCrypt.
- `SessionService.authenticate()` kiểm tra token, account và hạn idle, gia hạn bằng `Clock`/`Instant`/`Duration`. Inject Clock giúp test đúng biên hết hạn. `logout()` kiểm tra session thuộc account rồi xóa qua coordinator. Spring Security `SecurityFilterChain` khai báo route và role. `OncePerRequestFilter` xác thực Bearer rồi đặt `CurrentAccount` vào context để controller nhận bằng `@AuthenticationPrincipal`. `CurrentAccount.accountId()` là nguồn danh tính phía server.
- `GlobalExceptionHandler` ánh xạ lỗi MVC và lỗi service về envelope. `SecurityErrorWriter` xử lý lỗi phát sinh ở filter. `ApiErrors` dùng chung cấu trúc để HTTP security và controller thống nhất. Jackson `@JsonProperty` và `@JsonAlias` giữ tên lưu trữ chính và đọc được tên cũ. Cấu hình Jackson từ chối số thứ tự enum để client phải gửi giá trị rõ nghĩa.
- `changePassword()` xác minh phiên, mật khẩu hiện tại và xác nhận mật khẩu mới. `sendRecovery()` sinh mã 6 chữ số, lưu hash trước khi gọi `RecoveryMailSender`. `JavaMailSender` và `SimpleMailMessage` cô lập SMTP khỏi service để test có thể dùng mock. SMTP lỗi trả 503, mã đã ghi còn đến khi hết hạn hoặc bị thay thế.
- `verifyCode()` tăng số lần sai trong commit rồi mới trả lỗi để rollback không xóa bộ đếm. Mỗi mã mặc định 10 phút, tối đa 5 lần sai và cấp một token khôi phục cùng hạn. `updatePass()` kiểm tra hash, account và ID lần phát mã trước khi đổi mật khẩu, đánh dấu mã đã dùng và thu hồi phiên. Đổi mật khẩu khi đang đăng nhập cũng thu hồi mã và token khôi phục.

## Lưu trữ và mô phỏng IoT

- `InMemoryDataStore` giữ entity theo kiểu và ID. `StoreSnapshot` dùng record và `List.copyOf` để giữ ảnh chụp. `StateCoordinator.commit(expectedVersion, change)` kiểm tra version, áp dụng thay đổi và ghi snapshot. Khi ghi thất bại, khôi phục RAM và version. `JsonStore` ghi file tạm rồi chuyển vào đích bằng `Files.move`, ưu tiên `ATOMIC_MOVE`. `DataStoreBootstrap` nạp file khi startup.
- `SimulatorProperties` dùng Spring Boot `@ConfigurationProperties` để bind danh sách xe, trạm và frame; `@ConstructorBinding` làm rõ constructor khi giữ overload Java cũ. `SchedulingConfigurer` và `FixedDelayTask` chỉ đăng ký tick khi có cấu hình, mặc định 4 giây. `IoTSimulator.tick(Instant)` có thể gọi trực tiếp với thời gian cố định trong test, giữ thứ tự frame và bỏ tick cũ/trùng.
- `VehicleReading` kiểm tra miền tọa độ, pin, timestamp. `SimulatorAdapter.emitTelemetry()` chỉ nhận mẫu mới hơn. `IoTGateway.getVehicleTelemetry()` tính chất lượng lúc đọc vì tuổi dữ liệu thay đổi ngay cả khi không có tick. `StationSensors.readOccupiedSpaces()`, `readPort()`, `readHubStatus()` giữ timestamp và giá trị có thật. Dữ liệu thiếu trả MISSING, quá 10 phút trả STALE. Trạng thái nghiệp vụ xe và độ mới của cảm biến là hai thuộc tính riêng.
- `DeviceCommand` chứa commandId, loại thao tác, đích và các trường theo loại. `lockAtSpace()` kiểm tra ô thiết bị báo trước khi ACK. `forceLock()` phục vụ thao tác tại vị trí hiện tại. `reservePort()` dùng khoảng nửa mở để hai slot tiếp giáp hợp lệ. `releasePort()` dùng reservationId và đúng cổng để tránh hủy nhầm. Retry cùng commandId giữ payload và tác động ban đầu.
- `JournaledIoTAdapter` là `@Primary` để các gateway Spring nhận adapter có persistence. Adapter ghi intent PENDING trước khi gọi simulator. Simulator ghi `DeviceReceipt` bằng coordinator, trạng thái khóa và các lượt giữ được dựng lại theo sequence. `getResult()` quan sát thời gian giao ACK, `reconcileResult()` đọc bằng chứng thực thi. `ApplicationReadyEvent` chạy đối soát sau khi datastore được nạp. Intent thiếu receipt vẫn chờ đối soát.
- Nhóm cuối dùng `accountId` trong operation và kiểm tra quyền trước khi poll. `GET /api/operations/{id}` nhận caller qua `CurrentAccount`, cho owner hoặc OPERATOR đọc. Bản ghi cũ thiếu owner dành cho Operator. `SUCCEEDED` ở đây thể hiện thành công thiết bị. Kết quả tạo Ride/hoàn tất nghiệp vụ thuộc service phối hợp tương ứng.

## UC-13 và UC-14

- `ChargingScheduler.buildProposal()` lấy snapshot, đọc SoC mới, ưu tiên mức pin dưới 20%, sau đó xét thời điểm dùng kế tiếp, pin và ID. Công suất dựa trên các giới hạn trạm, xe và cổng. Thời lượng từ năng lượng/công suất, làm tròn lên phút. `TreeSet` xét thời điểm sớm nhất và các biên kết thúc lịch, `Comparator` giúp thứ tự ổn định. Đây là thuật toán tham lam theo các tiêu chí đã ghi trong feature plan.
- `editSlot()` dùng revision để phát hiện sửa chồng và trả vi phạm trên draft. `validateProposal()` kiểm tra từng slot và đỉnh công suất trên các đoạn giao nhau. `confirmSchedule()` kiểm tra lại dữ liệu/version rồi ghi schedule, allocations và request status cùng commit. Xác nhận lại cùng proposal trả lịch đã có.
- `SimulationEngine.runSimulation()` dùng snapshot riêng và chỉ đọc telemetry. Dữ liệu tuyến từ Ride hoàn tất là đầu vào baseline, lịch sử thiếu tuyến/quãng đường hiện trong missingData. `PriorityQueue` xử lý các lượt trả theo timestamp. Cấu hình mặc định lookback 24 giờ, bước 15 phút và 1% pin/km. Mức tiêu hao là giả định mô hình. Engine tổng hợp peak occupancy, số lượt thuê/trả thất bại và đề xuất điều phối. Đề xuất chuyển sang luồng dispatch M2 để kiểm tra trước khi áp dụng.

## Ma trận phụ thuộc và điều kiện nghiệm thu

| Bên gọi | M3 đã cung cấp | Việc còn cần khi ghép |
|---|---|---|
| M1 UC-01/02 | Telemetry xe, freshness, lọc theo role/public route tương ứng | HubSearchService hiện đọc Vehicle.telemetryAt. Cần thống nhất nguồn dữ liệu với cache gateway và giữ kiểm tra SHARED. Bổ sung đường đi cho chi tiết xe khi ghép service |
| M1 UC-03/04 | CurrentAccount, role STUDENT cho route, coordinator | Controller/service kiểm tra owner reservation và chỗ đỗ |
| M1 UC-05/06 | unlock, lockAtSpace, ACK/retry/reconcile, poll có owner | Truyền accountId từ principal, gắn idempotency với request, tạo/kết thúc Ride một lần sau ACK và trả kết quả nghiệp vụ theo contract |
| M1 UC-07 | readHubStatus/readPort, reservePort/releasePort, receipt và ownership | Gắn reservationId với charging request, nối commit/hủy sau ACK, thống nhất lịch giữ simulator và allocation, cung cấp window/nextUsageAt cho UC-13 |
| M2 UC-08/10 | Cảm biến và quality, MobilityRules | Nối số đo vào report/alerts, giữ MISSING/STALE khi thiếu tín hiệu |
| M2 UC-09/12 | RBAC Operator, forceLock/unlock, journal thiết bị | Nối can thiệp, audit và xác nhận trạng thái đúng tài nguyên qua service M2 |
| M2 UC-11 | recommendedDispatch từ UC-14 | Kiểm tra nguồn/đích, sức chứa và xe hiện có trước tạo dispatch. Bàn giao dữ liệu tuyến/quãng đường từ Ride để có baseline thật |
| UC-13/14 | Controller, service, schema và kiểm tra Operator | Kiểm thử với dữ liệu hợp nhất và xác nhận hợp đồng chung |

### Điểm chưa đủ cho PR bàn giao hoàn chỉnh

1. Commit/push nhóm operation ownership. Ref GitHub hiện chỉ chứa tới UC13/UC14.
2. Thống nhất model Vehicle và hợp đồng dữ liệu. M1 dùng vehicleId, Double batteryPct, batteryCapacityKwh, telemetryAt, ownership, parkingSpaceId và version. M3 dùng id, int batteryPct và batteryCapacityKWh. Mô phỏng merge trên ref đã lưu cho thấy xung đột Vehicle và API contract khi ghép M1. Cần thống nhất schema đọc dữ liệu cũ, ownership và hai lớp trạng thái.
3. Thống nhất polling thiết bị với polling nghiệp vụ. Mẫu API pickup có rideId; OperationService chỉ trả kết quả thiết bị. Cần xác nhận việc M1 cập nhật Ride sau ACK và cách mapping trạng thái/result HTTP.
4. UC-13 hiện kiểm tra cổng và allocation từ snapshot nghiệp vụ. Lệnh giữ cổng dùng receipt simulator riêng. Khi ghép M1 cần có một cách thống nhất để nhận diện xung đột giữa hai lịch, cùng test đặt đồng thời.
5. Kiểm thử ghép nhánh cho luồng Student/Operator với dữ liệu demo, nguồn telemetry, dữ liệu lịch sử và test/package. Các ref tracking M1/M2 hiện có HubSearchController/HubSearchService, nên chưa thể xác nhận các luồng tiêu thụ còn lại chạy xuyên suốt.

Tài liệu tham chiếu trong repo gồm [kiến trúc](02-architecture.md), [API contract](03-api-contract.md), [schema](04-data-schema.md), [UC-13](plans/feature-uc13-charging-scheduler.md), [UC-14](plans/feature-uc14-what-if.md) và [tích hợp M3](plans/feature-m3-integration.md).


## Cập nhật trước PR

Xung đột cùng cổng giữa UC-07 và UC-13 đã được xử lý hai chiều. Simulator đọc allocation ACTIVE/RESERVED trong lúc ghi receipt. Scheduler đọc receipt từ snapshot và kiểm tra lại version khi xác nhận. Một lượt giữ chưa có allocation được tính theo công suất định mức cổng. Hai test mới kiểm tra xác nhận proposal sau khi có lượt giữ và việc gateway từ chối trùng allocation nhưng cho phép tiếp giáp. test-compile đã thành công; chạy lại test và package trước khi push.

Khi ghép, thống nhất các trường Vehicle theo schema dùng chung: vehicleId/id, batteryCapacityKwh/batteryCapacityKWh, batteryPct nullable, ownership, parkingSpaceId, telemetryAt và version. M1 cần quyết định cách dùng gateway trong HubSearchService và giữ kiểm tra SHARED. Luồng nhận/trả xe cần dùng accountId từ CurrentAccount, lưu liên hệ requestId–commandId–Ride và chốt thời điểm trả kết quả nghiệp vụ. M2 nối forceLock/unlock vào service can thiệp, kiểm tra lại proposal trước khi tạo dispatch. Bảng phụ thuộc ở trên liệt kê các vị trí bàn giao tương ứng.
