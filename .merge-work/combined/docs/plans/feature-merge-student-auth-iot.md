# Tích hợp M1 Student và System Auth IoT

## Current state
Thư mục đích đã có các tệp từ hai ZIP và module operator. Các điểm nối còn thiếu: StudentAccess tra token thô trong khi auth lưu hash, chưa có StudentDevicePort chạy thật, route hủy đặt xe bị denyAll. Maven Wrapper lỗi khi thư mục cache không phải symlink trên Windows.

## Goal
Giữ các tính năng đã có và nối auth, UC01–07, IoT vào cùng datastore, coordinator và tiến trình.

## Non-goals
Không thay dữ liệu đang dùng, thông tin SMTP hay viết lại module operator. Không khẳng định đầy đủ UC00–15 chỉ từ hai nhánh nguồn.

## Design decisions
Giữ bản tích hợp hiện có làm nền để bảo toàn thay đổi cục bộ. Token hiện hành tra SHA-256 cùng quy ước auth; duy trì cách đọc session cũ của SessionService. Adapter lấy accountId từ durable student claim trước khi gửi lệnh. Poll chỉ đọc kết quả, không phát lệnh mới. Thành công nghiệp vụ chỉ sau ACK. Giữ DTO role/fullName của nhánh auth và schema hợp nhất hiện tại.

## Files affected
- src/main/java/vn/edu/bku/smart_emobility_backend/service/StudentAccess.java
- src/main/java/vn/edu/bku/smart_emobility_backend/iot/StudentIoTAdapter.java
- src/main/java/vn/edu/bku/smart_emobility_backend/common/config/SecurityConfiguration.java
- src/test/java/vn/edu/bku/smart_emobility_backend/StudentMergeIntegrationTest.java
- mvnw.cmd, README.md, docs/03-api-contract.md, docs/04-data-schema.md
- docs/plans/feature-merge-student-auth-iot.md

## Implementation
1. Đối chiếu tệp hai ZIP với đích, giữ các phần đã tích hợp.
2. Sửa tra phiên, route hủy, adapter IoT và wrapper Windows.
3. Thêm kiểm tra hash session, expiry và ACK/poll không gửi lại.
4. Chạy toàn bộ test và đóng gói trước khi bàn giao.

## Acceptance criteria
Auth session mới dùng được với StudentAccess; phiên hết hạn bị từ chối. Có bean StudentDevicePort, gửi owner chính xác; polling không gửi thêm lệnh. Không ghi đè data.json hay cấu hình cục bộ. Toàn bộ test và package thành công.

## Verification
`.\mvnw.cmd test` và `.\mvnw.cmd clean package -DskipTests=false`.
