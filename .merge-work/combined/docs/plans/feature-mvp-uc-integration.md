# Tích hợp các use case và snapshot MVP

## Current state

Nhánh demo đã ghép Student, M2/Operator và feat/system-auth-iot. UC Student còn dùng cách tra phiên cũ và thiếu adapter runtime. Đặt sạc Student giữ allocation trước khi gửi IoT, còn simulator xem allocation đó như xung đột. Registry của Operator có dữ liệu nghiệp vụ ngoài snapshot.

## Goal

Gọi API hiện có bằng phiên thật, xử lý với simulator trong tiến trình, cập nhật trạng thái qua StateCoordinator và đọc được trạng thái đó sau khi khởi động lại.

## Non-goals

Giữ hợp đồng HTTP hiện có. UC-15 thuộc trình duyệt. UC-14 tính trên bản sao, trường hợp áp dụng phương án đi qua UC-11/13.

## Design decisions

- Dùng hướng dẫn UML và báo cáo Phase 2 để xác nhận luồng. Khi biểu mẫu trong báo cáo khác DTO đã publish, ghi lại chênh lệch để nhóm duyệt contract cùng FE.
- Token mới chỉ tra theo SHA-256. Tương thích phiên cũ có điều kiện và không chấp nhận khóa hash của phiên như bearer token. Hồ sơ Student dùng quan hệ accountId. Dữ liệu đăng ký mới được ghi cùng một commit.
- Nối các port Student với gateway có journal. Simulator MVP có thể đọc xe, vị trí và cổng từ dữ liệu demo. Timestamp pin giữ nguyên từ nguồn đã quan sát. Thiếu hoặc cũ dữ liệu được xử lý như chưa xác nhận.
- Xác nhận sạc chỉ được dùng lại chính allocation tạm giữ khớp request, cổng và khoảng thời gian. Mọi allocation khác vẫn được kiểm tra xung đột.
- Nạp JSON một lần, dùng chung snapshot runtime, ghi nguyên tử trước khi xác nhận mutation nghiệp vụ. Mở rộng snapshot tương thích ngược cho metadata nghiệp vụ và audit Operator. Callback thiết bị trong tiến trình và projection tính lại có vòng đời riêng.

## Files affected

- `service/{StudentAccess,AuthService,StudentRideCommands,StudentDevicePort,ChargingRequestService}.java`, `common/config/SecurityConfiguration.java`.
- `iot/{StudentIoTAdapter,SimulatorAdapter,IoTGateway,DeviceCommand}.java`.
- `common/store/{OperatorStateRegistry,StoreSnapshot,InMemoryDataStore}.java`, `service/operator/{AuditLogService,OperationOrchestrator}.java`.
- `docs/03-api-contract.md`, `docs/04-data-schema.md`, kế hoạch này.
- `src/test/java/vn/edu/bku/smart_emobility_backend/MvpSnapshotHttpTest.java`.

## Implementation steps

1. Tạo test HTTP với file JSON tạm cho đăng ký, đăng nhập, đặt/hủy, nhận/trả xe và sạc. Kiểm tra snapshot sau thao tác và đọc lại sau restart.
2. Sửa tích hợp Auth/Student và nối simulator. Kiểm tra ACK đúng, ownership và chống xung đột trên cùng snapshot.
3. Lưu metadata và audit cần phục hồi của Operator cùng snapshot, kiểm tra tương thích file cũ.
4. Đối chiếu tài liệu và chạy toàn bộ test/build.

## Acceptance criteria

- Tài khoản đăng ký qua UC-00 thực hiện được UC Student có phân quyền.
- ACK nhận/trả đúng xe và vị trí mới được chuyển Ride. Pin thiếu/cũ giữ dữ liệu không xác nhận.
- Yêu cầu sạc Student được xác nhận khi đúng allocation tạm giữ, không ghi đè lịch cổng khác.
- Sau khởi động lại dữ liệu đã commit tiếp tục đọc được từ API. UC-14 không cập nhật thực thể nghiệp vụ live.

## Verification

Chạy MvpSnapshotHttpTest trước và sau bản sửa, sau đó chạy `.\mvnw.cmd test` và `.\mvnw.cmd clean package -DskipTests=false` với file thử nghiệm riêng.
