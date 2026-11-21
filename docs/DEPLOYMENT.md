# Build và bàn giao

```powershell
npm.cmd ci
npm.cmd run check
npm.cmd run preview
```

Thư mục `dist/` chứa static frontend. CI tải thư mục này lên artifact `frontend-dist` sau khi kiểm tra thành công. Không commit dist vào Git.

## Khi nhóm sẵn sàng triển khai

- Build với cấu hình `.env` production đúng; `VITE_*` là build-time và public.
- Web host phục vụ `index.html`, JS, CSS qua HTTPS.
- Cấu hình fallback route SPA về `index.html` để refresh `/student/hubs` không 404; giữ `/api` và static asset ngoài fallback.
- Reverse proxy `/api` đến Spring Boot hoặc cấu hình CORS với origin cụ thể nếu khác origin.
- Auth và CSRF phải hoàn thiện, kiểm tra quyền ở backend; starter hiện chưa có.
- Không dùng Vite dev server hoặc preview làm web host production.
- Không đưa IoT simulator/data.json/backend secret vào frontend hosting.
- Kiểm tra Chrome/Edge trên Windows, kích thước giao diện sinh viên và dashboard theo Figma.

Không cấu hình tự động deploy trong repo starter để tránh công khai giao diện chưa hoàn thiện.
