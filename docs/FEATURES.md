# Use cases và kế hoạch triển khai

Phân công kế thừa PROJECT_INFO của Submission 3, cần nhóm xác nhận nếu Submission 4 đổi người. Mỗi UC vẫn là một hạng mục riêng, kể cả UC11/14/15.

| UC   | Chức năng            | Route starter                  | Phụ trách | Trạng thái                       |
| ---- | -------------------- | ------------------------------ | --------- | -------------------------------- |
| UC00 | Tài khoản            | `/account`                     | Quân      | Scaffold                         |
| UC01 | Tìm Hub              | `/student/hubs`                | Phúc      | Ví dụ mock + adapter API đề xuất |
| UC02 | Chi tiết xe          | `/student/vehicles/:vehicleId` | Sơn       | Scaffold                         |
| UC03 | Đặt xe               | `/student/reservations`        | Phúc      | Scaffold                         |
| UC04 | Đặt chỗ đỗ           | `/student/parking`             | Sơn       | Scaffold                         |
| UC05 | Nhận xe QR           | `/student/pickup`              | Phúc      | Scaffold                         |
| UC06 | Trả xe               | `/student/return`              | Sơn       | Scaffold                         |
| UC07 | Đăng ký sạc          | `/student/charging`            | Quân      | Scaffold                         |
| UC08 | Tổng quan mạng lưới  | `/operator/overview`           | Quân      | Scaffold                         |
| UC09 | Can thiệp tài nguyên | `/operator/interventions`      | Thịnh     | Scaffold                         |
| UC10 | Cảnh báo quá tải     | `/operator/alerts`             | Thịnh     | Scaffold                         |
| UC11 | Điều phối xe         | `/operator/dispatch`           | Bảo       | Scaffold                         |
| UC12 | Quản lý sự cố        | `/operator/incidents`          | Quân      | Scaffold                         |
| UC13 | Lập lịch sạc         | `/operator/charging`           | Thịnh     | Scaffold                         |
| UC14 | Mô phỏng What-if     | `/operator/simulation`         | Bảo       | Scaffold                         |
| UC15 | Cài đặt giao diện    | `/settings`                    | Bảo       | Preferences starter              |

Tên thành viên: Phúc = Nguyễn Hoàng Phúc; Sơn = Nguyễn Võ Hoàng Sơn; Quân = Ngô Minh Quân; Thịnh = Võ Trí Minh Trường Thịnh; Bảo = Thái Lê Gia Bảo. Không tự gán GitHub username hoặc tự mời người vào repo.

## Ưu tiên đề xuất

1. Cả nhóm chốt tokens/components, API contract, auth và layout.
2. Phúc: UC01 → UC03 → UC05; Sơn: UC02 → UC04 → UC06; Quân: UC00 trước, sau đó UC07/08/12.
3. Thịnh: UC09/10/13 sau khi có hợp đồng command/snapshot; Bảo: UC15 rồi UC11/14.
4. Kiểm thử nối luồng đặt–nhận–trả, đặt chỗ/sạc, theo dõi và can thiệp.

## Definition of Done cho mỗi UC

- Link frame Figma cụ thể, kiểm tra Việt/Anh và sáng/tối.
- Luồng chính và nhánh lỗi theo Use Case Description được duyệt.
- Trạng thái loading, empty, error, pending, success nếu phù hợp.
- Backend contract được xác nhận và mock phân biệt rõ.
- Quyền truy cập backend được kiểm tra; không dùng UI guard thay server authorization.
- Test phần logic/UI có rủi ro, `npm run check` thành công.
- PR có ảnh, hướng dẫn thử và giới hạn còn lại; reviewer xác nhận.

## Luồng kiểm tra tích hợp tối thiểu

Đăng nhập đúng/sai/quyền; tìm Hub và dữ liệu rỗng/cũ; đặt cùng tài nguyên bị 409; QR hết hạn/sai; lệnh ACK trễ/mất; trả xe có sự cố/pin thấp; đăng ký sạc trùng lịch; dashboard mất dữ liệu; can thiệp bị từ chối; điều phối không đủ chỗ; proposal không tự áp dụng vào thực tế; đổi ngôn ngữ/theme giữ đúng trạng thái giao diện.

Những tiêu chí này là backlog kiểm thử, không phải kết quả kiểm thử đã thực thi.
