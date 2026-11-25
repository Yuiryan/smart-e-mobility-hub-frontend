# Kiến trúc frontend

HTML + CSS + JavaScript thuần, ES modules. Vite phục vụ phát triển/build; không có React, JSX, TypeScript hay router framework.

```text
index.html → main.js (hash router, events, role guard)
  → features/account.js, student.js, operator.js
  → shared/ui.js, messages.js, scanner.js
  → domain/service.js
      → mocks/engine.js + mocks/operator-engine.js (chế độ demo)
      → features/hub-search-api.js → shared/api/client.js (UC01 thật)
  → Spring Boot m1_student (UC01 hiện có)
```

Backend sở hữu xác thực/phân quyền, giao dịch nguyên tử, tài nguyên, lưu trữ và IoT simulator. Frontend không đọc/ghi `data.json`, không điều khiển thiết bị thật. Kiểm tra trong DemoEngine chỉ mô phỏng các quy tắc để phát triển UI; không thay thế server.

`main.js` dựng HTML qua các hàm render và dùng event delegation. Dữ liệu động phải qua `e()` để escape HTML; không chèn dữ liệu người dùng vào HTML thô. Mỗi route UC vẫn riêng trong `catalog.js`; thêm module feature mới thay vì đưa nghiệp vụ vận hành vào router.

Phiên demo nằm trong bộ nhớ, hết hạn sau 30 phút không tương tác. Route guard chỉ hỗ trợ trải nghiệm; quyền thật cần kiểm tra server. LocalStorage chỉ lưu vi/en và light/dark. SessionStorage chỉ lưu thời gian form theo user/route, TTL 15 phút, xóa khi đăng nhập/đăng xuất. Camera dừng khi rời trang hoặc đọc được mã. GPS chỉ yêu cầu khi người dùng bấm nút và không lưu vị trí.

Lệnh pickup/return có PENDING → SUCCESS/FAILED/TIMEOUT; gửi lệnh chưa phải thành công. TIMEOUT giữ trạng thái chờ đối soát, không cho gửi lại. Reload đặt lại demo; đây không phải cách đối soát trong production. Backend cần hỗ trợ truy vấn trạng thái/idempotency.

IDs dùng JavaScript string. DTO demo chưa phải hợp đồng API. Nội dung giao diện vi/en, định danh mã nguồn tiếng Anh, tài liệu tiếng Việt.

## Chế độ API

`main.js` mount `mobile-api.js` khi bật API. Renderer dùng lại font, màu, khung mobile và các component tài khoản/trang chủ. `shared/api/mobile.js` quản lý token trong bộ nhớ, DTO, biên nhận giao dịch và khóa idempotency. `shared/api/client.js` giữ status và lỗi có cấu trúc, hỗ trợ timeout/AbortSignal. Không dùng snapshot hoặc giao dịch DemoEngine trong API mode.

Public: tìm Hub, danh sách xe từ kết quả tìm kiếm, chi tiết xe. Giao dịch yêu cầu Bearer. Khi rời trang, hủy request đọc, dừng camera và timer polling; mutation đã gửi có thể vẫn hoàn thành và cập nhật biên nhận trong bộ nhớ, nhưng không ghi đè màn hình mới. Mọi kết quả dữ liệu động được escape trước khi dựng HTML.

Khi chạy với checkout backend hiện tại, chọn `VITE_AUTH_PROFILE=backend` để gửi DTO auth tương ứng. Các màn sinh viên đọc telemetry và trạng thái lệnh từ API. [Cách chạy và kiểm thử](API.md).
