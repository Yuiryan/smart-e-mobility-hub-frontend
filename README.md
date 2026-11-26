# Smart E-Mobility Hub — Frontend

Frontend HTML, CSS và JavaScript thuần, sử dụng Vite. Bản chạy chính chỉ kết nối API Spring Boot, không còn giao diện đăng nhập hoặc thao tác demo.

## Chạy trên Windows

```powershell
npm.cmd ci
npm.cmd run dev
```

Mở http://localhost:5173. Backend mặc định ở http://localhost:8080; cấu hình `API_PROXY_TARGET` trong `.env.local` nếu cần. `VITE_*` là cấu hình công khai, không đặt bí mật vào đó. Dùng tài khoản được backend cấp. `dev:api` và `build:api` vẫn được giữ để tương thích.

## Kiểm tra

```powershell
npm.cmd run check
```

Lệnh kiểm tra định dạng, lint, test và build. Không tự triển khai ứng dụng.

## Luồng sinh viên

- Đặt xe → lưu biên nhận và đúng mã xe → quét QR nhận xe → chờ backend/thiết bị xác nhận → quay lại Trả xe → chọn Hub và ô đỗ.
- Đặt chỗ đỗ và trả xe tải danh sách Hub, sau đó hiển thị các ô lấy từ API; ô không khả dụng không chọn được. Nếu tải danh sách thất bại, người dùng có thể thử lại.
- Tài khoản có thông tin chung, quyền riêng tư, quốc gia, ngôn ngữ, giao diện và bảo mật. Premium chỉ là thông tin thiết kế, chưa có dịch vụ đăng ký.
- Đã bỏ thanh giờ/pin giả, mục thông báo sinh viên và công cụ API ở góc màn hình.

## Giới hạn tích hợp

Sau khi đăng nhập, frontend đọc lượt đặt còn hiệu lực và chuyến `ACTIVE` của tài khoản từ `GET /api/student/journey`. Chuyến đang chạy hiện ở Trang chủ và Lượt đặt để tiếp tục trả xe. Mã nhận xe từ biên nhận được lưu theo tài khoản và lượt đặt trong cùng trình duyệt để dùng lại sau khi đóng tab, trong hạn giữ do backend trả về. Màn nhận xe hiển thị xác nhận thành công sau khi backend báo đã mở khóa. Token và mật khẩu chỉ ở bộ nhớ.

Các module mock còn lại phục vụ kiểm thử và không được mount bởi ứng dụng. Không có giao dịch giả thay thế khi API lỗi. Chỉ kết quả backend xác nhận mới hoàn tất nhận/trả xe. Chưa kiểm chứng kết nối backend thực trong lần sửa này.

Giao diện tham khảo CSS Figma được cung cấp, chưa cam kết khớp từng pixel. [Kiến trúc](docs/ARCHITECTURE.md) · [Contract](docs/API-CONTRACT.md) · [API](docs/API.md) · [Các UC00–UC15](docs/FEATURES.md).

## Chạy với BE IoT demo và xử lý HTTP 502

Chọn đúng cặp lệnh; chạy BE và FE ở hai terminal riêng:

| Chế độ   | BE                                                                 | FE                         | API proxy                           |
| -------- | ------------------------------------------------------------------ | -------------------------- | ----------------------------------- |
| Thường   | `.\mvnw.cmd spring-boot:run`                                       | `npm.cmd run dev:api`      | `localhost:5173` → `localhost:8080` |
| IoT demo | `.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=iot-demo'` | `npm.cmd run dev:iot-demo` | `localhost:5174` → `localhost:8081` |

BE `iot-demo` dùng cổng 8081; FE `dev:api` mặc định gọi 8080. Ghép hai lệnh này khiến proxy không tới đúng BE. Dừng FE, chạy `npm.cmd run dev:iot-demo` và mở **http://localhost:5174**. Chờ BE báo `Started SmartEmobilityBackendApplication` trước khi đăng nhập. `VITE_AUTH_PROFILE=backend` không chọn cổng BE.

Nếu chưa cài dependency, chạy `npm.cmd ci` trong thư mục FE. Demo vẫn gọi API thật và dùng snapshot demo riêng của BE; không thay giao dịch lỗi bằng dữ liệu giả.

Muốn đổi đích BE cho demo, đặt biến trước khi chạy FE:

```powershell
$env:IOT_DEMO_API_PROXY_TARGET="http://localhost:8081"
npm.cmd run dev:iot-demo
```

`API_PROXY_TARGET` chỉ áp dụng cho `dev`/`dev:api`. Giữ `VITE_API_BASE_URL=/api`; khởi động lại Vite sau khi sửa biến môi trường hoặc `.env.local`. Không đặt URL BE tuyệt đối vào `VITE_API_BASE_URL` khi dùng proxy. Các origin mặc định BE cho demo là `localhost:5174` và `127.0.0.1:5174`.

Kiểm tra trực tiếp và qua proxy:

```powershell
curl.exe -i http://localhost:8081/api/vehicles/bike_001
curl.exe -i http://localhost:5174/api/vehicles/bike_001
```

Lệnh đầu lỗi kết nối: xem log khởi động BE/cổng. Lệnh đầu được nhưng lệnh sau lỗi: kiểm tra FE proxy. `403 ACCESS_DENIED` liên quan quyền/CORS cần đọc JSON lỗi từ BE, không xử lý bằng đổi proxy ngẫu nhiên. Cấu hình proxy theo [tài liệu Vite](https://vite.dev/config/server-options.html#server-proxy).
