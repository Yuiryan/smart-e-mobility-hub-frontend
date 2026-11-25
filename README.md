# Smart E-Mobility Hub — Frontend

Frontend web Submission 4 dùng **HTML, CSS và JavaScript thuần (ES modules)**. Không sử dụng React, JSX hoặc TypeScript. Vite chỉ chạy máy chủ phát triển và đóng gói mã nguồn; không phải framework giao diện.

[Figma](https://www.figma.com/design/exswez5Rtk8mrmEgxqbNBS/Final?node-id=0-1) · [Backend](https://github.com/PhuscCea07/Smart-E-Mobility-Hub) · [Phân công](docs/FEATURES.md) · [Kiến trúc](docs/ARCHITECTURE.md) · [API](docs/API.md)

## Chạy trên Windows

$env:VITE_AUTH_PROFILE = 'backend'
npm.cmd run dev:api

Cài Node.js 24 và Git, sau đó mở PowerShell:

```powershell
git clone https://github.com/Yuiryan/smart-e-mobility-hub-frontend.git
cd smart-e-mobility-hub-frontend
npm.cmd ci
Copy-Item .env.example .env.local
npm.cmd run dev
```

Mở http://localhost:5173. Không mở `index.html` bằng cách nhấp đúp: ES modules và thư viện QR cần máy chủ phát triển hoặc bản build.

## Phần đã triển khai

- UC00: đăng nhập hai vai trò, đăng ký sinh viên, khôi phục/đổi mật khẩu, đăng xuất và chặn trang theo vai trò trong demo.
- UC01–02: tìm Hub/xe, sơ đồ minh họa, yêu cầu vị trí khi bấm nút, xem pin/trạng thái và dữ liệu cũ.
- UC03–04: đặt/hủy xe, tạo QR, thời hạn giữ xe, tìm và xác nhận chỗ đỗ, kiểm tra trùng thời gian.
- UC05–06: nhập mã, camera hoặc ảnh QR; nhận/trả xe với pending, thành công, thất bại và timeout mô phỏng. Chỉ thay đổi chuyến đi sau ACK.
- UC07: đăng ký sạc xe cá nhân mẫu, mức pin mục tiêu, thời gian ước tính, kiểm tra thời lượng và trùng cổng.
- UC15: Việt/Anh, sáng/tối; chỉ lưu tùy chọn và thời gian form không nhạy cảm trong 15 phút.
- Danh sách lượt đặt, lịch sử và thông báo; bố cục responsive, kiểm tra form, hộp xác nhận và thông báo lỗi.
- UC08–UC14: tổng quan, can thiệp tài nguyên, cảnh báo quá tải, điều phối xe, xử lý sự cố, lịch sạc và mô phỏng; các thao tác sử dụng dữ liệu demo trong bộ nhớ.
- UI: font Be Vietnam Pro tự lưu trữ, logo xuất từ Figma, màu sáng/tối và kích thước thành phần theo mẫu đã cung cấp; màn tài khoản tách các bước và trạng thái thành công/lỗi.

**Mobile API:** chạy `npm.cmd run dev:api` với `VITE_AUTH_PROFILE=backend` để kết nối checkout backend hiện tại. Dùng profile `iot-demo` phía backend để thử luồng tìm, đặt, nhận, trả và sạc với dữ liệu cảm biến giả lập. `npm.cmd run dev` vẫn chạy demo độc lập. [Hướng dẫn kết nối API](docs/API.md).

Đây là **frontend chạy với dữ liệu mô phỏng**. Tải lại trang đặt lại toàn bộ tài khoản demo, lượt đặt, chuyến đi và lệnh. Không dùng demo làm hệ thống thật. Bản đồ là minh họa, tọa độ và công thức thời gian sạc là dữ liệu mẫu. Giao diện bám hướng thiết kế Figma, chưa cam kết khớp từng pixel.

## Tài khoản demo

| Vai trò   | Email               | Mật khẩu |
| --------- | ------------------- | -------- |
| Sinh viên | student@demo.local  | Demo123! |
| Vận hành  | operator@demo.local | Demo123! |

Mã khôi phục demo: `123456`, hiệu lực 5 phút sau khi yêu cầu, chỉ dùng một lần. Không có email thật được gửi. Không nhập mật khẩu thật. Mật khẩu và phiên chỉ nằm trong bộ nhớ, không lưu localStorage. Demo có một xe cá nhân mẫu dùng chung để trình diễn sạc; quyền sở hữu thật chờ backend.

Luồng nhanh: đăng nhập sinh viên → chọn E-024 → đặt xe → nhận xe → điền mã demo → xác nhận mở khóa → chờ ACK → trả xe → chọn chỗ → xác nhận khóa.

## Lệnh kiểm tra

```powershell
npm.cmd run format
npm.cmd run check
npm.cmd run build
npm.cmd run preview
```

`check` gồm Prettier, Oxlint, Vitest và build. CI chạy trên Windows. `dist/` là kết quả build, không commit. Camera/GPS cần quyền trình duyệt và HTTPS hoặc localhost.

## Cấu trúc

```text
index.html                  Điểm vào HTML
src/main.js                 Điều hướng hash, gắn sự kiện, vòng đời trang
src/features/account.js     UC00
src/features/student.js     UC01–UC07
src/features/catalog.js     Danh mục UC00–UC15 và phân công
src/domain/service.js       Ranh giới adapter dữ liệu
src/mocks/engine.js          Nghiệp vụ demo trong bộ nhớ
src/shared/                 HTML helpers, vi/en, camera QR, HTTP client
src/index.css               CSS bố cục và thành phần
src/styles/tokens.css       Màu light/dark
```

URL dùng `#/student/hubs`, nên bản build không cần rewrite route phía server. QR dùng `qrcode` và `@zxing/browser`; các thư viện này không phải framework UI.

## Backend và phần còn lại

UC01–UC07 dùng API của backend. Kết quả tìm và xem chi tiết phản ánh mẫu telemetry mới nhất tại lúc tải. Trạng thái nhận/trả xe và lập lịch sạc dựa trên xác nhận từ thiết bị giả lập ở backend.

Chạy `npm.cmd run dev:api` để dùng API. `npm.cmd run build:api` tạo bản build API. [Cách chạy và kiểm thử](docs/API.md).

## Giao diện vận hành desktop

Chạy chế độ demo bằng `npm.cmd run dev`, mở `/#/welcome/pc` để xem màn chào PC hoặc `/#/account/operator` để đăng nhập vận hành. Tài khoản: `operator@demo.local` / `Demo123!`.

Bản cập nhật UI nằm trên nhánh `feat/operator-workflows`. Khi lấy mã bằng Git, chuyển sang nhánh này trước khi chạy:

```powershell
git switch feat/operator-workflows
git pull --ff-only
npm.cmd ci
npm.cmd run dev
```

Sidebar gồm tổng quan, tài nguyên Hub, can thiệp, cảnh báo, điều phối, sự cố, lịch sạc, mô phỏng và giao diện. Chuyển Việt/Anh và sáng/tối ở góc trên hoặc trong mục Giao diện. `dev:api` dùng các API vận hành UC08–UC14. [Hướng dẫn API vận hành](docs/OPERATOR-API.md). `dev` vẫn là demo.
