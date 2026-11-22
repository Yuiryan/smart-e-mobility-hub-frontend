# Figma → Frontend

## Nguồn chuẩn

[Final — Figma của nhóm](https://www.figma.com/design/exswez5Rtk8mrmEgxqbNBS/Final?node-id=0-1).

Đã đọc được hai page công khai trong Figma:

- `Mobile · Sinh viên`: Welcome, Account access, Find and pick up, Parking and returns, Charging and preferences, Feedback states, Notifications and time slots; các biến thể location off / enable location prompt, account settings / change password.
- `PC · Đơn vị vận hành`: Welcome, Account access, Monitoring, Dispatch and incidents, Charging and simulation, Feedback states, Resource selection, Alternative flows UC-09 / UC-10 / UC-14.
- Có biến thể tiếng Việt/English và Light/Dark.

Đây là các nhóm frame đã quan sát, không phải danh sách mọi frame đã kiểm tra chi tiết. UI starter chỉ sử dụng hướng bố cục và màu teal/light/dark nhìn thấy; font, hex color, khoảng cách, kích thước và asset chưa trích xuất từ Inspect. Không đánh dấu màn hình là hoàn thành Figma chỉ vì route đã tồn tại.

## Cách triển khai từng màn hình

1. Chọn frame gốc và copy link chứa node-id vào issue/PR.
2. Ghi lại typography, color, spacing, border radius, component states qua Inspect. Nếu cần quyền Inspect, chủ thiết kế cung cấp quyền hoặc xuất thông số.
3. Cập nhật `src/styles/tokens.css`; dùng token chung thay vì rải hex code.
4. Xuất SVG cho icon/logo, PNG/WebP cho ảnh; lưu asset đã được nhóm cho phép vào `src/assets/` hoặc `public/`. Không tự thay logo/font bằng tài nguyên không rõ giấy phép.
5. Tạo component dùng chung: button, input, modal, card, table, status badge, loading/empty/error.
6. Triển khai layout theo khu vực sinh viên/vận hành và UI state của frame.
7. Chụp ảnh so sánh cùng viewport với Figma, kiểm tra vi/en, light/dark, text dài, zoom 200% và keyboard.

## Các trường hợp cần dự trù

- Từ chối GPS/camera phải có thông báo và luồng thay thế theo thiết kế; không xin quyền ngay khi mở trang.
- QR phải phân biệt mã không hợp lệ, hết hạn, sai xe, sai sinh viên và lệnh mở khóa chưa có ACK.
- Data table vận hành cần xử lý loading, empty, stale và request lỗi.
- Hộp xác nhận giao dịch phải phản ánh điều kiện/giá trị backend, không bịa kết quả.

## Checklist chốt design system

- [ ] Font và quyền sử dụng font
- [ ] Màu light/dark chính xác
- [ ] Spacing/radius/shadow và breakpoint
- [ ] Asset/logo/icons chính thức
- [ ] Các trạng thái component
- [ ] Liên kết frame cụ thể cho từng UC trong FEATURES.md

## Bản triển khai JavaScript thuần

Đã tham chiếu frame Home vi/light `276:4459` và nhóm Charging/preferences en/light `114:10172` trong Figma Final. Dùng teal, nền cyan nhẹ, thẻ bo góc, điều hướng dưới, vi/en và light/dark. Màu/font/spacing là xấp xỉ, chưa có giá trị Inspect chính thức; sơ đồ Hub và biểu tượng xe là minh họa, không phải asset được trích từ thiết kế.
