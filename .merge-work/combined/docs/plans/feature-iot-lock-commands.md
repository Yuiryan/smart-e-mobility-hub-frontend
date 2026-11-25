# M3 — Lệnh khóa và mở khóa tức thời

## Current state
SimulatorAdapter nhận mẫu xe; gateway hiện chỉ đọc telemetry. UC-05/06 cần ACK tương quan với đích lệnh.

## Goal
Gateway.unlock(vehicleId, commandId), lockAtSpace(vehicleId, spaceId, commandId) và getResult(commandId) cho service gọi nội bộ.

## Non-goals
Không xử lý Ride, quyền nhận/trả xe, HTTP, ACK trễ hay lưu operation nghiệp vụ. Kết quả thiết bị chỉ tồn tại trong vòng đời simulator.

## Design decisions
Frame có thể cung cấp spaceId mô phỏng cảm biến vị trí. Chỉ khóa khi spaceId đo được khớp yêu cầu. Mẫu thiếu, timestamp quá hạn hoặc cảm biến khóa chưa rõ cho kết quả AWAITING_RECONCILIATION. Sai ô đã biết trả FAILED. ACK mang commandId, đích, thời điểm, pin quan sát nếu có. Lệnh không làm mới timestamp của pin/GPS. Sau ACK, trạng thái cơ khí do actuator giả lập giữ; mất tín hiệu khóa vẫn được biểu diễn bằng null. Retry cùng ID và payload trả lại kết quả ban đầu; payload khác bị từ chối.

## Files affected
iot/{DeviceCommand,DeviceResponse,IoTAdapter,IoTGateway,SimulatorAdapter,VehicleReading,SimulatorProperties}.java, common/domain/CommandStatus.java, iot/DeviceCommandTest.java và plan này.

## Implementation steps
Bổ sung input vị trí mô phỏng, mô hình lệnh và phản hồi, đồng bộ nhận mẫu và thực thi lệnh, nối gateway và thêm test.

## Acceptance criteria
ACK đúng xe và ô, không thành công khi thiếu bằng chứng vị trí, retry không lặp tác động; giữ nguyên tuổi telemetry.

## Verification
Người dùng chạy DeviceCommandTest, IoTSimulatorTest, VehicleTelemetryTest, toàn bộ test và clean package.
