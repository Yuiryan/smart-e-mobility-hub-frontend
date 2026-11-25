# M3 — Hoàn thiện gateway thiết bị và cổng sạc

## Current state
Gateway hiện xử lý LOCK/UNLOCK; journal đã lưu intent và kết quả nhưng simulator mất receipt khi restart. Cảm biến StationSensors có frame trạm và cổng.

## Goal
Cung cấp ChargingIoTGateway cho readHubStatus, reservePort và releasePort. Retry, timeout và đối soát dùng chung journal. Lưu receipt simulator để dựng lại trạng thái khóa và lịch giữ cổng sau restart.

## Non-goals
Phân quyền tài nguyên HTTP operation và việc cập nhật Ride, ChargingRequest, ChargingAllocation do các service tích hợp sở hữu.

## Design decisions
- Mở rộng DeviceCommand với hubId, portId, reservationId, startAt, endAt tùy loại. Constructor LOCK/UNLOCK cũ tiếp tục dùng được. FORCE_LOCK phục vụ can thiệp Operator qua gateway nội bộ.
- reservePort nhận commandId và reservationId để retry có tương quan. Khoảng giữ là [startAt,endAt). Hai khoảng tiếp giáp được phép. startAt phải từ thời điểm nhận trở đi. releasePort chỉ giải phóng đúng reservationId trên đúng cổng.
- Kiểm tra cảm biến cổng FRESH và AVAILABLE trước giữ mới. Release dùng receipt lịch đã giữ. Nguồn sự thật lịch thiết bị là chuỗi receipt ACK theo sequence.
- Receipt simulator lưu trong snapshot, ghi qua StateCoordinator trước công bố tác động trong RAM. Mỗi receipt lưu result, acceptedAt, availableAt, timeout, dropAcknowledgement, sequence. Snapshot cũ thiếu mảng đọc thành rỗng.
- Query ACK bình thường giữ semantics delay/drop. Phép reconcile rõ ràng đọc biên nhận thực thi của simulator. Startup query biên nhận để giải quyết operation chưa terminal. Nếu chỉ tồn tại intent, vẫn chờ đối soát và không tự gửi lại.
- Mẫu pin/GPS giữ nguyên observedAt khi thực thi khóa. Các status sensor dùng enum hiện tại của repo. Đồng bộ enum nghiệp vụ sạc thuộc nhóm UC-13.
- Cảm biến chỉ là cache RAM. Receipt thiết bị và reservation vẫn sống qua restart. readHubStatus cung cấp mẫu frame trạm cùng chất lượng, thiếu trường vẫn MISSING.
- Các test regression được bổ sung dưới dạng source. Người dùng thực thi test và commit theo yêu cầu phiên làm việc.

## Files affected
- iot/DeviceCommand.java, DeviceReceipt.java, SimulatorAdapter.java, IoTAdapter.java, IoTGateway.java, JournaledIoTAdapter.java, ChargingIoTGateway.java, StationSensors.java.
- common/store/StoreSnapshot.java, InMemoryDataStore.java.
- src/test/.../iot/ChargingIoTGatewayTest.java, SimulatorRestartTest.java.
- docs/03-api-contract.md, docs/04-data-schema.md và plan này.

## Implementation steps
1. Mô hình lệnh và receipt tương thích dữ liệu cũ.
2. Lưu receipt nguyên tử, truy lại trạng thái actuator và lịch giữ.
3. Nối gateway sạc, đối soát thủ công và startup.
4. Bổ sung test khoảng thời gian, lỗi ghi, retry và restart.

## Acceptance criteria
- Không gửi lặp cùng commandId hoặc cho phép ID đó trỏ payload khác.
- Giữ cổng trùng giờ bị từ chối; release đúng reservation mới có tác động.
- Mất ACK vẫn giữ PENDING/timeout cho tới khi tra cứu receipt để đối soát.
- Sau restart biết kết quả thực thi, giữ trạng thái khóa và lịch giữ cổng.
- Nếu ghi receipt thất bại, không có tác động actuator.
- Tất cả thay đổi thuộc gateway thiết bị và simulator của M3.

## Verification
Biên dịch với mvnw -o test-compile. Người dùng chạy test nhóm IoT, toàn bộ test, clean package trước một commit hoàn thiện IoT.

## Trạng thái triển khai
Code và test source đã bổ sung cho gateway sạc, receipt thiết bị, phục hồi actuator và đối soát journal. Có test concurrent giữ cổng và lỗi persistence ở từng giai đoạn. Thực thi test và commit do người dùng thực hiện.
