# M3 — UC-13 đề xuất và xác nhận lịch sạc
## Current state
Gateway sạc và coordinator đã có. Vehicle, ChargingRequest và Allocation còn thiếu dữ liệu lập lịch theo PDF trang 154–163. Chưa có controller UC-13.
## Goal
Tạo, đọc, chỉnh sửa và xác nhận proposal với validation, persistence và quyền Operator.
## Non-goals
Quyền sở hữu yêu cầu Student và khởi động phiên sạc thiết bị thuộc service tích hợp. Xác nhận ở UC-13 ghi lịch và allocation.
## Design decisions
- Thêm capacity/maxPower nullable cho Vehicle, startAt/endAt/nextUsageAt nullable cho ChargingRequest, vehicleId/scheduleId nullable cho Allocation. Constructor cũ giữ nguyên. Dữ liệu thiếu được đánh dấu trong queue.
- Đồng bộ ChargingPortStatus SCHEDULED/MAINTENANCE, đọc alias JSON RESERVED/OUT_OF_SERVICE. Static alias Java cho caller cũ.
- SoC lấy từ telemetry FRESH. Ưu tiên nhóm pin dưới 20%, rồi nextUsageAt, rồi pin và requestId. Đây là thứ tự xác định theo dữ liệu đã có, không tự gán trọng số.
- Tính năng lượng từ capacity và chênh lệch target; thời lượng ceil tới phút. Cấp công suất min(port,vehicle,hub). Tìm khoảng sớm nhất từ các biên kết thúc lịch hiện tại. Lịch [start,end), slot tối thiểu một phút, start/end căn phút.
- Proposal có revision để chống chỉnh sửa mất dữ liệu. Confirm kiểm tra lại input hiện tại và version snapshot. Ghi schedule, allocation, request status và proposal đã xác nhận trong cùng commit. Retry confirm cùng proposal trả cùng schedule.
- Không có dữ liệu cần thiết thì valid=false và ghi queue reason. Xe thiếu request được báo trong queue, không suy đoán window.
- API POST /api/operator/charging-schedules tạo proposal; GET /{id}; PUT /{id}/slots/{slotId} nhận revision,startAt,endAt; POST /{id}/confirm nhận revision. Chỉ Operator hoạt động.
## Files affected
- common/domain/{Vehicle,ChargingRequest,ChargingAllocation,ChargingPortStatus,ChargingSlot,ScheduleProposal,ChargingSchedule}.java.
- service/ChargingScheduler.java, controller/ScheduleController.java, dto/ScheduleRequests.java, common/exception/SchedulingException.java, common/web/GlobalExceptionHandler.java.
- common/store/{StoreSnapshot,InMemoryDataStore}.java.
- src/test/.../ChargingSchedulerTest.java, ChargingSchedulerHttpTest.java.
- docs/03-api-contract.md, docs/04-data-schema.md và plan này.
## Implementation steps
Mở rộng model tương thích, tạo scheduler, nối HTTP, bổ sung test nghiệp vụ/HTTP/persistence.
## Acceptance criteria
Ưu tiên low battery, không trùng cổng/xe và không vượt công suất ở mọi thời điểm. Edit validation không đổi lịch hiệu lực. Confirm atomic, retry idempotent; missing telemetry không thành slot.
## Verification
Agent test-compile. Người dùng chạy toàn bộ test và package, tạo commit UC-13.

Slot tự sinh bắt đầu sớm nhất sau một phút để có thời gian xác nhận. Đây là mặc định triển khai của UC-13.

## Trạng thái kiểm chứng
Đã bổ sung 12 test service và 3 test HTTP. Source và test đã compile. Thực thi test và commit do người dùng thực hiện.
