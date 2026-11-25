# M3 — Mô phỏng thiết bị từ chối và mất ACK
## Current state
Simulator có ACK tức thời và trễ hữu hạn.
## Goal
Cho demo lỗi thiết bị có thể tái hiện bằng cấu hình.
## Non-goals
Operation persistence, restart reconciliation và nghiệp vụ nhận/trả xe được thực hiện tiếp theo.
## Design decisions
app.iot.simulator.command-mode mặc định NORMAL. REJECT trả FAILED và không tác động actuator. DROP_ACK thực thi nhưng không giao phản hồi, hết timeout giữ AWAITING_RECONCILIATION. Chế độ gắn với receipt khi nhận lệnh. Giữ constructor cũ cho caller hiện tại. Đây là cấu hình nội bộ simulator, không thay enum contract.
## Files affected
iot/SimulatorAdapter.java, iot/CommandFailureTest.java và plan này.
## Implementation steps
Chọn hành vi lúc nhận lệnh; giữ kết quả thực thi và chính sách giao ACK trong receipt; kiểm thử tác động và retry.
## Acceptance criteria
REJECT không mở khóa. DROP_ACK không báo ACK thành công dù thiết bị đã thực thi. Retry không lặp tác động.
## Verification
Người dùng chạy CommandFailureTest, toàn bộ suite và package. Agent test-compile.
