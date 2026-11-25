# Smart E-Mobility Hub — Backend

Hệ thống điều phối xe điện thông minh (Smart E-Mobility Hub) tại khuôn viên Đô thị Đại học Quốc gia TP.HCM (VNU-HCM). Dự án được xây dựng bằng **Java 21 LTS** và **Spring Boot 3.3.x**, áp dụng phương thức **Documentation-Driven Development (Phát triển theo định hướng tài liệu)**.

---

## 1. Yêu Cầu Môi Trường (Prerequisites)

- **JDK 21** trở lên (Khuyến nghị Eclipse Temurin hoặc OpenJDK 21).
- **Git**.
- Không cần cài đặt Maven riêng (Dự án đã tích hợp sẵn Maven Wrapper `mvnw` / `mvnw.cmd`).

---

## 2. Khởi Chạy Ứng Dụng (Run)

### Trên Windows (PowerShell):
```powershell
.\mvnw.cmd spring-boot:run
```

### Trên macOS / Linux:
```bash
./mvnw spring-boot:run
```

Ứng dụng khởi động tại cổng mặc định: `http://localhost:8080`.
- Toàn bộ trạng thái mạng lưới được lưu trữ an toàn trong RAM (`InMemoryDataStore`) và đồng bộ snapshot nguyên tử xuống đĩa tại `data/data.json` qua `StateCoordinator`.

### Dữ liệu mẫu và khôi phục dữ liệu cũ

BE đã có sẵn luồng đọc/ghi JSON: lúc khởi động, `DataStoreBootstrap` đọc file cấu hình `app.data-file=./data/data.json` vào RAM. Khi service gọi `StateCoordinator.commit(expectedVersion, change)`, backend tăng version và lưu snapshot xuống file; nếu lưu thất bại thì hoàn tác RAM. Lần khởi động sau đọc lại snapshot đã lưu, không tự reset về dữ liệu mẫu. Chạy lệnh từ thư mục gốc repository để dùng đúng đường dẫn tương đối; khi chạy ở nơi khác, truyền `--app.data-file=<đường dẫn tuyệt đối>` cho ứng dụng.

- [data/data.json](data/data.json): dữ liệu làm việc, được cập nhật khi có commit.
- [data/data.example.json](data/data.example.json): bản mẫu cố định, không được tự động nạp đè lên dữ liệu làm việc.
- Mẫu gồm 3 hub (Bách Khoa cơ sở Dĩ An, KTX A, KTX B), 12 xe (10 xe chia sẻ, 2 xe cá nhân), 24 ô đỗ (12 có xe, 12 trống), 6 cổng sạc (5 khả dụng, 1 ngừng phục vụ), 3 sinh viên và 2 operator.
- Các danh sách phiên đăng nhập, đặt xe, đặt chỗ, chuyến đi, yêu cầu sạc và lịch sử nghiệp vụ khởi đầu rỗng; service sẽ ghi các bản ghi tương ứng vào snapshot. Hub là trạm; `chargingPorts` là các cổng sạc thuộc hub, không có thực thể trạm sạc riêng trong model hiện tại.

Các tài khoản sau hoàn toàn là dữ liệu giả:

| Account ID | Email | Vai trò | MSSV |
|---|---|---|---|
| `acc_sv_001` | `sv01@example.com` | STUDENT | `2210001` |
| `acc_sv_002` | `sv02@example.com` | STUDENT | `2210002` |
| `acc_sv_003` | `sv03@example.com` | STUDENT | `2210003` |
| `acc_op_001` | `operator01@example.com` | OPERATOR | — |
| `acc_op_002` | `operator02@example.com` | OPERATOR | — |

Mật khẩu mẫu là `Demo123!`; file chỉ chứa hash theo quy ước `sha256$salt$hex`, trong đó `hex = SHA-256(UTF-8(salt + ":" + password))`. **Checkout hiện tại chưa triển khai UC-00 đăng ký/đăng nhập**, nên các tài khoản đã nạp vào kho nhưng chưa đăng nhập qua API được. Thành viên làm UC-00 cần thống nhất định dạng hash hoặc chuyển đổi hash mẫu theo cơ chế xác thực được chọn. Không có token đăng nhập cố định trong mẫu.

Telemetry mẫu mang mốc cố định `2026-11-24T00:00:00Z`. Khi quá 10 phút, các số đo này là `STALE`; `bike_004` có pin và thời điểm đo `null` nên là `MISSING`. Vì vậy FE có thể thấy hub nhưng không có xe đủ điều kiện thuê cho tới khi có telemetry mới hợp lệ từ phần tích hợp IoT. Khởi động lại không tự làm mới thời gian đo. Mẫu có pin 19.9% (`LOW_BATTERY`), 20.0% và 20.1% để nhóm kiểm tra các ca biên sau này.

Để chủ động reset về mẫu, **dừng BE trước**, sao lưu dữ liệu hiện tại rồi mới copy:

```powershell
$backupPath = "data/data.backup-$(Get-Date -Format 'yyyyMMdd-HHmmss-fff').json"
Copy-Item -LiteralPath data/data.json -Destination $backupPath
Copy-Item -LiteralPath data/data.example.json -Destination data/data.json
.\mvnw.cmd spring-boot:run
```

Không reset nếu muốn giữ dữ liệu cũ; chỉ khởi động lại bình thường. File bị thiếu hoặc chỉ có khoảng trắng hiện làm kho khởi động rỗng; JSON sai cú pháp làm khởi động thất bại. Mẫu không được tự động dùng thay thế các trường hợp đó. Không sửa JSON trong lúc BE đang chạy vì lần commit tiếp theo sẽ ghi đè từ RAM.

---

## 3. Chạy Kiểm Thử (Test)

Dự án có bộ kiểm thử tự động toàn diện bao gồm Unit Tests, Integration Tests và Concurrency Tests:

```powershell
# Chạy toàn bộ test suite
.\mvnw.cmd test

# Chạy riêng một lớp test (ví dụ tìm kiếm trạm UC-01)
.\mvnw.cmd test -Dtest=HubSearchControllerTest
```

---

## 4. Đóng Gói Ứng Dụng (Build & Package)

```powershell
# Dọn dẹp và đóng gói thành file JAR thực thi
.\mvnw.cmd clean package

# Chạy file JAR đã build
java -jar target/smart-emobility-backend-0.0.1-SNAPSHOT.jar
```

---

## 5. Quy Trình Phát Triển & Bộ Tài Liệu Ngữ Cảnh (Documentation Suite)

Dự án áp dụng phương thức **Documentation-Driven Development**: Mọi quy tắc nghiệp vụ, kiến trúc, API contract và kế hoạch tính năng đều được văn bản hóa trước khi viết mã nguồn.

### ⚠️ Dành Cho AI Agent & Thành Viên Mới
**BẮT BUỘC ĐỌC ĐẦU TIÊN:** [AGENTS.md](file:///d:/BKU/Smart%20E-Mobility%20Hub/smart-emobility-backend/AGENTS.md) — 13 quy tắc bắt buộc về lập kế hoạch, an toàn dữ liệu, chống duplicate rule và kiểm soát đồng thời.

### Thứ Tự Tài Liệu Tra Cứu:
1. [00-product-context.md](file:///d:/BKU/Smart%20E-Mobility%20Hub/smart-emobility-backend/docs/00-product-context.md) — Bối cảnh sản phẩm, chân dung người dùng (Student, Operator), phạm vi In-scope/Out-of-scope và các quyết định kỹ thuật đã chốt.
2. [01-domain-rules.md](file:///d:/BKU/Smart%20E-Mobility%20Hub/smart-emobility-backend/docs/01-domain-rules.md) — Quy tắc nghiệp vụ chi tiết, máy trạng thái (Vehicle, Reservation, Ride), các ngưỡng số học (pin $\ge 20\%$, quá tải $\ge 85\%$, giữ chỗ 15p), và quy tắc an toàn khi thiếu dữ liệu đo xa.
3. [02-architecture.md](file:///d:/BKU/Smart%20E-Mobility%20Hub/smart-emobility-backend/docs/02-architecture.md) — Kiến trúc hệ thống, phân rã module, quyền sở hữu dữ liệu, luồng kiểm soát đồng thời `StateCoordinator` và sơ đồ Mermaid.
4. [03-api-contract.md](file:///d:/BKU/Smart%20E-Mobility%20Hub/smart-emobility-backend/docs/03-api-contract.md) — Hợp đồng REST/JSON API chính thức cho 16 Use Cases, cấu trúc lỗi chuẩn và cơ chế Idempotency.
5. [04-data-schema.md](file:///d:/BKU/Smart%20E-Mobility%20Hub/smart-emobility-backend/docs/04-data-schema.md) — Chi tiết thực thể, trường dữ liệu, cấu trúc file `data/data.json` và chiến lược tương thích schema.
6. [05-ui-flow.md](file:///d:/BKU/Smart%20E-Mobility%20Hub/smart-emobility-backend/docs/05-ui-flow.md) — Danh mục màn hình Mobile và Web, các trạng thái giao diện (Loading, Empty, Error, Active) và bảng ánh xạ UI-API.
7. [06-test-plan.md](file:///d:/BKU/Smart%20E-Mobility%20Hub/smart-emobility-backend/docs/06-test-plan.md) — Kế hoạch kiểm thử theo Use Case, ca kiểm thử biên, kiểm thử đồng thời race condition (HT.1 - HT.6) và Definition of Done (DoD).
8. [plans/feature-template.md](file:///d:/BKU/Smart%20E-Mobility%20Hub/smart-emobility-backend/docs/plans/feature-template.md) — Mẫu lập kế hoạch cho từng tính năng / PR mới.

---

## 6. Cấu Trúc Thư Mục Dự Án

```text
smart-emobility-backend/
├── AGENTS.md                  # Luật vận hành bắt buộc cho AI Agent & Developer
├── README.md                  # Hướng dẫn kỹ thuật onboarding nhanh
├── pom.xml                    # Cấu hình dự án Maven & Spring Boot
├── data/
│   └── data.json              # File snapshot trạng thái mạng lưới trên đĩa
├── docs/                      # Bộ tài liệu ngữ cảnh chính thức
│   ├── 00-product-context.md
│   ├── 01-domain-rules.md
│   ├── 02-architecture.md
│   ├── 03-api-contract.md
│   ├── 04-data-schema.md
│   ├── 05-ui-flow.md
│   ├── 06-test-plan.md
│   └── plans/
│       ├── feature-template.md
│       └── feature-example.md
└── src/
    ├── main/java/bku/emobility/
    │   ├── common/            # Domain POJO, Enums, StateCoordinator, In-Memory Store
    │   ├── controller/        # REST Controllers & DTOs
    │   ├── service/           # Logic nghiệp vụ các Use Case
    │   ├── iot/               # In-Process IoT Simulator
    │   └── scheduler/         # Background Scheduled Jobs
    └── test/java/bku/emobility/
```

## Tích hợp Student, Auth và IoT

Đã đối chiếu hai ZIP M1 Student và System Auth IoT, giữ module operator và cấu hình cục bộ. StudentAccess tra hash token do Auth cấp; route hủy đặt xe cho STUDENT; StudentIoTAdapter nối nhận/trả xe với journal IoT và lấy owner từ StudentOperation. Poll không gửi thêm lệnh, nghiệp vụ chỉ hoàn tất sau ACK.

Chạy từ thư mục gốc bằng `.\mvnw.cmd spring-boot:run`. Login gửi email/password/role; đăng ký gửi fullName/studentId/email/password. Trả xe M1 yêu cầu issueReported (boolean). Khôi phục mật khẩu cần SMTP hoạt động. Dữ liệu và cấu hình đang dùng được giữ nguyên.

Xem [kế hoạch tích hợp](docs/plans/feature-merge-student-auth-iot.md). Hai ZIP không cung cấp đủ mọi UC vận hành; các phần operator hiện có được giữ lại.
