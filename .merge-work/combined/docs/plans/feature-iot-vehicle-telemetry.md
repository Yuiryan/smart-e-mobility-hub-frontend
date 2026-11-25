# M3 — Đọc telemetry của xe qua IoTGateway

## Current state
M3 có Vehicle và Clock. UC-02 cần gateway theo A01_7.pdf trang PDF 33. Chưa có adapter telemetry.

## Goal
Cho service đọc mẫu mới nhất theo vehicleId cùng chất lượng tại thời điểm đọc.

## Non-goals
Tick định kỳ, command/ACK, API UC-01/02 và thay đổi trạng thái Vehicle thuộc các nhóm tiếp theo.

## Design decisions
SimulatorAdapter giữ cache tạm theo xe như mục 5 của docs/02-architecture.md. Cache chỉ giữ mẫu mới nhất theo observedAt. Mẫu đến trễ hoặc trùng timestamp giữ nguyên bản đã nhận. Đây là quan sát thiết bị trong RAM, không phải mutation Vehicle. Khởi động lại trả MISSING tới khi có mẫu mới.
Từ chối timestamp tương lai, số không hữu hạn, tọa độ và pin ngoài phạm vi. Mẫu thiếu GPS, pin, khóa hoặc trạng thái xe được trả lại với MISSING để consumer biết trường nào thiếu. Mẫu đầy đủ đúng 10 phút vẫn FRESH. Phân loại khi đọc bằng Clock.

## Files affected
- common/domain/DataQuality.java
- iot/VehicleReading.java, VehicleTelemetry.java, IoTAdapter.java, IoTGateway.java, SimulatorAdapter.java
- src/test/.../VehicleTelemetryTest.java và plan này

## Implementation steps
1. Mô hình hóa mẫu bất biến, chất lượng, kiểm tra miền giá trị.
2. Cập nhật cache nguyên tử theo vehicleId; gateway đọc qua interface.
3. Kiểm thử biên thời gian, dữ liệu thiếu, giá trị sai và thứ tự nhận.

## Acceptance criteria
Mẫu không trở thành FRESH nếu thiếu dữ liệu; không ghi đè trạng thái nghiệp vụ xe. Mẫu cũ không thay mẫu mới.

## Verification
Người dùng chạy VehicleTelemetryTest, toàn bộ mvnw test và clean package.
