# Kiểm thử frontend

`npm.cmd run check`: format, lint, unit tests và build. Tests kiểm tra phân quyền demo, dữ liệu cũ/pin thấp, đặt trùng, hết hạn giữ xe/phiên, reset một lần, QR credential theo chủ sở hữu, pending/ACK/failed/timeout, trả xe và lượt đi tiếp theo, sạc/đỗ trùng, escape HTML, vi/en và HTTP errors.

Đã thử trên trình duyệt: đăng nhập → chi tiết E-024 → đặt xe → QR → nhập mã demo → ACK → chuyến đi → trả xe → ACK → chỗ trả đã bị chiếm; đặt chỗ khác và đăng ký sạc hiện trong lịch sử. Đã kiểm tra chuyển English/dark, bố cục viewport nhỏ.

Camera thật/GPS thật chưa được cấp quyền để kiểm thử; không khẳng định đã đọc camera trên thiết bị người dùng. Cần nhóm thử quyền cho phép/từ chối, ảnh QR mờ, camera không có thiết bị, mất mạng, backend trả 401/403/409 và đối soát timeout sau khi tích hợp API. Demo không kiểm chứng chống tranh chấp giữa nhiều trình duyệt hoặc bảo mật server.

## Tích hợp UC01

Bổ sung kiểm thử DTO POST, origin/destination, giới hạn đầu vào, response sai định dạng, kết quả STALE rỗng, escape dữ liệu server và submit form gọi API. Đã chạy backend m1_student 58c5985 trên cổng 8080, gọi qua Vite và thao tác tìm H1 trong trình duyệt; HTTP 200 và UI báo đúng STALE/0 xe. Không chỉnh data.json hay nguồn backend. Chưa kiểm thử GPS thật hoặc các endpoint chưa được backend hiện thực.
