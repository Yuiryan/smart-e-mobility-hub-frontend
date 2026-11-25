# M3 — Quyền operation và tích hợp cuối
## Current state
UC-13/14 đã có endpoint Operator. GET /api/operations/{id} mới có route security. DeviceOperation chưa lưu người sở hữu.
## Goal
Cung cấp HTTP polling operation có kiểm tra quyền, owner được gắn từ service đã xác thực. Chuẩn hóa các ngưỡng dùng chung M3 và kiểm thử tích hợp.
## Non-goals
Các controller/service Student và Operator nghiệp vụ khác sẽ được kiểm tra khi ghép nhánh M1/M2.
## Design decisions
- DeviceOperation thêm accountId nullable và updatedAt. Snapshot cũ chưa có owner chỉ Operator được đọc.
- Gateway có overload accountId cho caller nghiệp vụ. Journal xác minh account hoạt động, FORCE_LOCK yêu cầu Operator. Retry phải giữ cùng owner và payload, cấm đổi owner của operation đã tồn tại.
- GET /api/operations/{id} chỉ owner hoặc Operator được truy cập. Danh tính lấy từ CurrentAccount. Kiểm tra quyền trước khi poll adapter.
- Status HTTP operation là PENDING, SUCCEEDED, FAILED, AWAITING_RECONCILIATION. Result của operation thiết bị chứa ID đích, không chứa token/hash hoặc thông tin xác thực. Error của lệnh FAILED là IOT_COMMAND_FAILED, chờ đối soát là IOT_RECONCILIATION_REQUIRED.
- Poll chỉ cập nhật trạng thái journal đã được adapter xác nhận. ACK thiết bị không tự tạo Ride hoặc hoàn tất ChargingRequest.
- Giữ overload cũ dùng cho job nội bộ. Operation không owner vẫn là tài nguyên nội bộ chỉ Operator thấy.
- Các rule pin 20%, freshness 10 phút, quá tải 85%, thiếu xe <=2 được khai báo tại MobilityRules và tái sử dụng trong M3.
## Files affected
- iot/{DeviceOperation,IoTAdapter,IoTGateway,ChargingIoTGateway,JournaledIoTAdapter,VehicleReading,StationSensors}.java.
- common/domain/MobilityRules.java, service/{OperationService,ChargingScheduler}.java, simulation/SimulationEngine.java.
- controller/OperationController.java, dto/OperationResponseDTO.java, common/exception/ResourceNotFoundException.java, common/web/GlobalExceptionHandler.java.
- src/test/.../OperationAccessTest.java.
- docs/03-api-contract.md, docs/04-data-schema.md và plan này.
## Implementation steps
Thêm owner metadata, bảo vệ retry, nối polling HTTP, tái sử dụng rule chung, kiểm thử quyền và persistence.
## Acceptance criteria
Student chỉ poll operation thuộc mình, Operator poll được tài nguyên nội bộ. Student không force lock. Unauthorized request không poll thiết bị hoặc sửa journal. Metadata owner giữ qua restart.
## Verification
Agent test-compile. Người dùng chạy toàn bộ test và package trước commit cuối.
## Điểm tích hợp cần kiểm chứng khi ghép nhánh
M1 truyền accountId từ phiên vào overload gateway và kiểm tra ownership reservation/ride/charging request trong service của mình. M2 kiểm tra đề xuất UC-14 với trạng thái hiện tại trước tạo DispatchOrder. Các nhánh đó hiện chưa có trong repo M3 để kiểm thử end-to-end.

## Trạng thái triển khai
Đã thêm endpoint polling, owner metadata và 14 test tích hợp. Student bị chặn khi dùng reservationId cổng sạc đã gắn tài khoản khác. Test và package do người dùng thực hiện.

## Kiểm tra xung đột lịch ở điểm bàn giao

SimulatorAdapter kiểm tra allocation ACTIVE/RESERVED trong cùng vùng đồng bộ khi ghi biên nhận giữ cổng. ChargingScheduler đọc biên nhận giữ cổng từ cùng snapshot dùng để xác nhận lịch. Một lượt giữ chưa có allocation tương ứng được tính theo công suất định mức cổng. Allocation và receipt cùng request, cổng và thời gian chỉ được tính một lần. Dữ liệu thiếu về cổng hoặc công suất chặn xác nhận.

Bổ sung phạm vi SimulatorAdapter.java, DeviceReceipt.java, ChargingSchedulerTest.java, GatewayStationTest.java và tài liệu bàn giao. Kiểm tra hai chiều, biên tiếp giáp và stale version khi receipt thay đổi.
