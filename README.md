# Smart E-Mobility Hub — Frontend

Bộ khung frontend web cho Submission 4, nhóm A01–07. Hai khu vực: sinh viên và đơn vị vận hành; kết nối chung backend Spring Boot theo kiến trúc Submission 3.

[Figma của nhóm](https://www.figma.com/design/exswez5Rtk8mrmEgxqbNBS/Final?node-id=0-1) · [Kiến trúc](docs/ARCHITECTURE.md) · [Danh sách UC và phân công](docs/FEATURES.md) · [Hướng dẫn Figma](docs/DESIGN.md) · [Tích hợp API](docs/API.md) · [Làm việc nhóm](CONTRIBUTING.md)

## Chạy trên Windows

Cài Node.js 24 LTS và Git. Dùng PowerShell hoặc terminal của VS Code:

```powershell
git clone https://github.com/Yuiryan/smart-e-mobility-hub-frontend.git
cd smart-e-mobility-hub-frontend
npm.cmd ci
Copy-Item .env.example .env.local
npm.cmd run dev
```

Mở http://localhost:5173. Repo công khai: mọi người có thể clone; thành viên cần được cấp quyền cộng tác để push trực tiếp. Nếu đã có thư mục repo trên máy, chỉ cần mở terminal tại thư mục đó và bắt đầu từ `npm.cmd ci`.

Dùng `npm.cmd` để tránh lỗi PowerShell chặn script `npm.ps1`. Node 24 thống nhất với CI. `package-lock.json` được commit để các máy cài cùng dependency.

## Hiện đã có gì?

- React + TypeScript + Vite, React Router, Lucide icons, CSS variables; chưa thêm UI framework để tránh áp đặt style khác Figma.
- Điều hướng riêng sinh viên / vận hành, route cho UC00–UC15 và trang 404.
- Starter giao diện responsive theo hướng teal, light/dark đã quan sát trong Figma; **chưa phải bản triển khai pixel-perfect**.
- Tiếng Việt / English cho nội dung starter, sáng/tối lưu trong trình duyệt.
- Trang Hub mẫu có loading, search, empty, error và retry.
- API client có timeout, hủy request và lỗi HTTP; mock tách riêng. Chỉ `/hubs` có ví dụ tích hợp, hợp đồng vẫn là đề xuất.
- Lint (Oxlint), formatter (Prettier), TypeScript, Vitest, GitHub Actions chạy trên Windows.
- PR template, issue templates và hướng dẫn nghiệm thu.

**Chưa có:** xác thực thật, phân quyền thật, các giao dịch đặt/nhận/trả xe, QR/camera/GPS, bản đồ, nghiệp vụ vận hành, API backend đã chốt. Ngoài trang Hub mẫu và tùy chọn hiển thị, các trang UC là scaffold có thông báo rõ. Không dùng starter làm hệ thống production.

## Lệnh làm việc

| Lệnh                     | Công dụng                                     |
| ------------------------ | --------------------------------------------- |
| `npm.cmd run dev`        | Chạy dev server tại cổng 5173                 |
| `npm.cmd run format`     | Định dạng mã nguồn                            |
| `npm.cmd run lint`       | Kiểm tra lỗi lint                             |
| `npm.cmd run typecheck`  | Kiểm tra TypeScript                           |
| `npm.cmd test`           | Chạy kiểm thử                                 |
| `npm.cmd run test:watch` | Chạy kiểm thử khi sửa file                    |
| `npm.cmd run build`      | Tạo thư mục `dist/`                           |
| `npm.cmd run preview`    | Xem bản build trên máy                        |
| `npm.cmd run check`      | Format check + lint + tests + build, giống CI |

## Cấu trúc

```text
src/
  App.tsx                    # Router, layout student/operator và màn hình scaffold
  features/catalog.ts        # 16 UC, route, tên Việt/Anh, người phụ trách
  features/hubs/             # Ví dụ module có dữ liệu và UI
  shared/api/                # HTTP client dùng chung
  shared/preferences.ts      # Ngôn ngữ và theme, không lưu thông tin đăng nhập
  styles/tokens.css          # Design tokens tạm; cần chốt với Figma Inspect
  test/                      # Thiết lập test
docs/                        # Kiến trúc, API, Figma, backlog, triển khai
.github/                     # CI và mẫu issue/PR
```

Khi triển khai UC, tạo `src/features/<feature>/` với page, API adapter, kiểu dữ liệu và test liên quan; thay route scaffold trong `App.tsx`. Không gom toàn bộ nghiệp vụ vào `App.tsx`.

## Kết nối backend

Đổi `VITE_DATA_SOURCE=api` trong `.env.local`; backend chạy ở `http://localhost:8080`, dev proxy chuyển `/api` sang backend. **Phải thống nhất DTO và endpoint với backend trước**, xem [API.md](docs/API.md). Khởi động lại dev server sau khi đổi `.env.local`.

Không đưa mật khẩu, token, API secret vào `VITE_*`: các giá trị này được đóng gói công khai vào JavaScript. Frontend không đọc/ghi `data.json` và không gọi IoT Simulator trực tiếp.

## Phân công hai thành viên

- **Bạn (~60–65%):** nền tảng chung, UC00–UC07, UC15, tích hợp và chuẩn bị bản demo tổng.
- **Thành viên 2 (~35–40%):** layout vận hành và UC08–UC14, bao gồm API adapter và kiểm thử các feature này.
- Xem phạm vi, thứ tự và quy tắc bàn giao tại [bảng phân công](docs/FEATURES.md). Tỷ lệ là ước lượng công việc frontend, không phải tỷ lệ đóng góp đã hoàn thành.

## Thứ tự bắt đầu

1. Chốt frame Figma cụ thể, font, màu, spacing và asset; đọc `docs/DESIGN.md`.
2. Thống nhất OpenAPI và cơ chế session/CSRF với backend.
3. Làm component chung và UC00; sau đó từng thành viên triển khai nhóm UC ở `docs/FEATURES.md`.
4. Kiểm tra luồng nghiệp vụ, lỗi, Việt/Anh, sáng/tối và viewport mục tiêu.
5. Mở PR, chạy CI và review trước khi merge.

Repo chưa tự động deploy và chưa mời collaborator. Chủ repo quản lý quyền truy cập; xem `CONTRIBUTING.md`.

Cách khởi tạo dựa trên [tài liệu Vite chính thức](https://vite.dev/guide/). Phiên bản thực tế nằm trong `package.json` và lockfile.
