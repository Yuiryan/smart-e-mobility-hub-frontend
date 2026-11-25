# M3 — Phát telemetry xe định kỳ
## Current state
IoTGateway đọc từ SimulatorAdapter. Adapter nhận mẫu nhưng chưa có producer.
## Goal
IoTSimulator.tick(Instant) phát một mẫu mỗi xe vào adapter trong tiến trình Spring.
## Non-goals
Cảm biến cổng sạc, command/ACK, workflow Student và ghi Vehicle thuộc phạm vi khác.
## Design decisions
Spring khởi tạo simulator từ app.iot.simulator.devices. Mỗi xe có danh sách frames lặp tuần tự. Chỉ các giá trị được cấu hình mới được phát. Thiếu cảm biến giữ null. Không cấu hình xe thì cache tiếp tục trống. Scheduler fixed delay 4 giây mặc định, cho phép 3–5 giây. Mọi frame cùng tick dùng một observedAt. Tick trùng hoặc cũ không tiến kịch bản.
## Files affected
iot/IoTSimulator.java, iot/SimulatorConfiguration.java, iot/SimulatorProperties.java, iot/IoTSimulatorTest.java và plan này.
## Implementation steps
Bind cấu hình, kiểm tra miền giá trị khi startup, nối scheduler với tick, tái sử dụng SimulatorAdapter và gateway.
## Acceptance criteria
Nhiều xe tiến độc lập, quay vòng theo thứ tự; frame thiếu dữ liệu trả MISSING. Tick lỗi thời không đổi frame. Không ghi datastore.
## Verification
Người dùng chạy IoTSimulatorTest, toàn bộ test và clean package.
