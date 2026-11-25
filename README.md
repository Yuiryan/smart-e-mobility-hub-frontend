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

Contract hiện chưa có API tải chuyến đang chạy/lịch sử. Frontend giữ biên nhận và chuyến đã xác nhận trong `sessionStorage` theo tài khoản, tối đa 24 giờ, trong cùng tab. Đăng nhập lại cùng tài khoản có thể khôi phục ngữ cảnh này. Backend vẫn xác thực quyền và trạng thái mỗi giao dịch; dữ liệu lưu không phải xác nhận mới. Đóng tab, hết hạn lưu hoặc đổi thiết bị không khôi phục được chuyến bằng cơ chế này. Token và mật khẩu chỉ ở bộ nhớ.

Các module mock còn lại phục vụ kiểm thử và không được mount bởi ứng dụng. Không có giao dịch giả thay thế khi API lỗi. Chỉ kết quả backend xác nhận mới hoàn tất nhận/trả xe. Chưa kiểm chứng kết nối backend thực trong lần sửa này.

Giao diện tham khảo CSS Figma được cung cấp, chưa cam kết khớp từng pixel. [Kiến trúc](docs/ARCHITECTURE.md) · [Contract](docs/API-CONTRACT.md) · [API](docs/API.md) · [Các UC00–UC15](docs/FEATURES.md).
