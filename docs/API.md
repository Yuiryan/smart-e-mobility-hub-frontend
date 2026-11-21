# API integration — hợp đồng cần chốt

Backend đích: Spring Boot REST/JSON. Hiện chưa có OpenAPI/backend source được cung cấp cho repo này. **Không có endpoint nào trong starter được khẳng định là API đã tồn tại.**

## Ví dụ đang có

`src/features/hubs/api.ts` sử dụng endpoint **đề xuất** `GET /api/hubs` với body là mảng:

```json
[
  {
    "id": "hub-01",
    "name": "Tên Hub",
    "availableVehicles": 12,
    "availableParking": 8
  }
]
```

Nếu backend trả envelope, tên trường khác hoặc endpoint khác, sửa adapter `listHubs`; không thay UI để phụ thuộc trực tiếp nhiều định dạng backend. Adapter hiện kiểm tra cấu trúc tối thiểu trước khi render. Giá trị Hub A/B/C là dữ liệu demo, không phải số liệu thật của trường.

## Cấu hình

| Biến                | Mặc định                | Ý nghĩa                                |
| ------------------- | ----------------------- | -------------------------------------- |
| `VITE_DATA_SOURCE`  | `mock`                  | `mock` chạy fixture, `api` gọi backend |
| `VITE_API_BASE_URL` | `/api`                  | URL public được build vào JS           |
| `API_PROXY_TARGET`  | `http://localhost:8080` | Đích proxy chỉ khi chạy dev            |

`vite.config.ts` không bỏ prefix `/api`. Nếu backend không có prefix, thống nhất lại proxy/contract trước. Production không dùng dev proxy: cấu hình reverse proxy `/api` hoặc API URL HTTPS và CORS phù hợp.

## HTTP client

`request<T>()` gửi cookie với `credentials: include`, timeout 15 giây, nhận AbortSignal, xử lý body 204 và throw `ApiError` chứa status khi HTTP lỗi. Generic T không tự xác thực JSON; mỗi adapter cần kiểm tra DTO như `listHubs`.

Cookie session là lựa chọn kỹ thuật tạm thời cho lớp client, **chưa phải quyết định auth đã được backend xác nhận**. Thống nhất cookie HttpOnly/Secure/SameSite, CSRF, login/logout/session hoặc cơ chế bearer được nhóm chọn trước khi viết mutation. Không lưu token/password vào localStorage. Không tự động retry POST vì có thể lặp giao dịch.

## Cần chốt với backend

- [ ] OpenAPI URL/file và version
- [ ] Login/registration/reset/session/logout; 401/403, idle timeout
- [ ] ID/string, enum, nullable fields, thời gian ISO 8601 có timezone, đơn vị pin/kW/phút
- [ ] Danh sách Hub, chi tiết xe, pagination/filter
- [ ] Đặt xe/chỗ đỗ/sạc, cancellation/expiry và 409
- [ ] Pickup/return command IDs, idempotency keys, cách đọc ACK/timeout
- [ ] Monitoring freshness và polling hoặc SSE/WebSocket (không tự giả định)
- [ ] Operator commands, incidents, dispatch, charging proposals, simulation
- [ ] Error envelope và correlation ID

## Quy tắc hiển thị lỗi

400: trường dữ liệu sai; 401: yêu cầu đăng nhập; 403: không đủ quyền; 404: tài nguyên không còn; 409: xung đột tài nguyên/phiên bản; timeout/5xx: thông báo chưa xác định kết quả và cho tra cứu lại. Với lệnh thay đổi trạng thái, không kết luận thất bại hay thành công chỉ từ mất kết nối.
