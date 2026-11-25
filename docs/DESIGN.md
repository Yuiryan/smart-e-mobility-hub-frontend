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

## Đợt đối chiếu component ngày 24/11/2026

Nguồn bổ sung: HTML/CSS welcome sáng/tối, CSS đăng nhập và ba ảnh nhóm màn hình do chủ thiết kế cung cấp. Đã áp dụng Be Vietnam Pro (font tự lưu trữ, giấy phép OFL kèm theo), logo PNG gốc từ HTML xuất Figma, nền #f5f8f9 / #111d22, màu chính #007f89 / #3ed4de, khung mobile 390px, nút cao 52px bo góc 16px, input cao 50px bo góc 14px và khoảng lề 24px.

`src/styles/figma.css` chứa các quy tắc đối chiếu mới. `src/shared/design.js` chứa thành phần tiêu đề, icon nét và thẻ thành công. Icon nét và bản đồ minh họa được dựng bằng SVG, chưa phải toàn bộ asset gốc Figma. Các màn ngoài ảnh mẫu sử dụng cùng hệ thống thành phần, cần tiếp tục so sánh với frame gốc trước khi xác nhận khớp từng pixel.

Thanh giờ/Wi-Fi trong khung mobile là trang trí theo thiết kế, không đọc trạng thái thiết bị. Điều khiển demo đặt ngoài khung giao diện. Chưa thêm kết nối API trong đợt này.

## Giao diện PC vận hành — 24/11/2026

Nguồn: bộ ảnh PC UC00, UC08–UC15, HTML/CSS welcome/login và nhóm giám sát do người dùng gửi. `operator-design.js` dựng sidebar, auth desktop và thiết lập; `operator-figma.css` định dạng riêng để không thay layout mobile.

- Khung desktop tối đa 1440px, sidebar 224px, main padding 32px; nền, font, nút, input theo thông số xuất Figma. Màn auth dùng intro 500px, form 520px, gap 64px ở kích thước đủ rộng.
- Tách tài nguyên Hub, kết quả điều phối và kết quả mô phỏng thành trạng thái riêng. Đề xuất mô phỏng mở form điều phối điền sẵn, chưa tự tạo lệnh.
- Bảng, thẻ số liệu và thông báo đọc trạng thái demo. Không hard-code số liệu/ACK thành công theo ảnh. Bảng trống vẫn giữ tiêu đề và thông báo chưa có dữ liệu.
- Việt/Anh và light/dark dùng bộ tùy chọn hiện có. Dark suy ra từ palette mobile theo yêu cầu.
- Bản đồ là SVG minh họa, không phải asset Figma. Mô phỏng giữ bộ tham số mà engine demo hiện hỗ trợ (lượt xe đến, nhu cầu xe đi, số giờ), chưa giả lập mô hình tăng phần trăm nhu cầu và hỏng cổng sạc trong ảnh.

Đã kiểm tra render DOM, các thao tác chọn tài nguyên/đếm xe/mở đề xuất và hồi quy bằng test tự động. Chưa đối chiếu ảnh chụp trình duyệt theo pixel, nên chưa xác nhận pixel-perfect. Khung 1440px có lề ngoài 24px mỗi bên; dùng viewport ít nhất 1488px để kiểm tra kích thước khung đầy đủ. Không thay hợp đồng API auth.
