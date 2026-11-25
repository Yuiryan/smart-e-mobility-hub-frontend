# M3 — ACK có độ trễ và timeout
## Current state
SimulatorAdapter lưu kết quả tức thời theo commandId.
## Goal
Mô phỏng ACK chậm qua send và getResult, cho phép tra cứu lại sau timeout.
## Non-goals
Persistence operation và đối soát sau restart, HTTP và workflow nhận/trả xe được làm ở nhóm sau.
## Design decisions
Lệnh được thực thi một lần khi nhận; kết quả thiết bị được giữ riêng tới thời điểm giao ACK. Độ trễ mặc định PT0S giữ hành vi tức thời. Timeout mặc định PT5S là lựa chọn triển khai. Trước hạn trả PENDING; tại timeout khi ACK chưa đến trả AWAITING_RECONCILIATION; ACK đến vẫn có thể được đọc lại. Nếu ACK và timeout cùng thời điểm thì ưu tiên ACK. Không dùng sleep hoặc thread cho từng lệnh. Clock được đọc khi send/getResult; không cần FE truy cập trực tiếp adapter.
## Files affected
iot/SimulatorAdapter.java, iot/DelayedAcknowledgementTest.java và plan này.
## Implementation steps
Lưu receipt với thời điểm gửi và phản hồi, phân loại theo Clock, kiểm thử các biên và retry.
## Acceptance criteria
Retry không thực thi lại lệnh. ACK trễ vẫn giữ nguyên commandId và kết quả thiết bị. Không đổi Vehicle hoặc Ride.
## Verification
Người dùng chạy DelayedAcknowledgementTest và toàn bộ suite. Agent chỉ test-compile.
