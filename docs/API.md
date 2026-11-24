# Kết nối API mobile — UC00–UC07

Frontend dùng `docs/API-CONTRACT.md` làm hợp đồng. Theo lựa chọn của chủ dự án ngày 24/11/2026, không tự thêm `role` vào login hoặc `fullName` vào register để chiều theo DTO backend đang lệch hợp đồng.

## Chạy

```powershell
npm.cmd ci
npm.cmd run dev:api
```

Mở http://localhost:5173. Nếu cổng đã được dùng, thêm `-- --port 5176`. `npm.cmd run dev` vẫn là demo; `npm.cmd run build:api` build bản API. Vite proxy `/api` tới `http://localhost:8080`, thay bằng `API_PROXY_TARGET` trong `.env.local` nếu cần. Bản triển khai cần reverse proxy `/api` tương ứng.

Không lưu token, mật khẩu, mã nhận xe hoặc tọa độ vào localStorage/sessionStorage. Token và các biên nhận chỉ nằm trong bộ nhớ; reload cần đăng nhập lại. Không fallback về mock khi server lỗi.

## API đã gắn với UI

| Luồng          | Request                                                    |
| -------------- | ---------------------------------------------------------- |
| Đăng ký        | POST /api/auth/register — studentId, email, password       |
| Đăng nhập      | POST /api/auth/login — email, password                     |
| Đăng xuất      | POST /api/auth/logout                                      |
| Tìm Hub/xe     | POST /api/search/hubs                                      |
| Chi tiết xe    | GET /api/vehicles/{vehicleId}                              |
| Đặt/hủy xe     | POST /api/reservations; POST /api/reservations/{id}/cancel |
| Đặt chỗ đỗ     | POST /api/parking-reservations                             |
| Nhận xe        | POST /api/pickups                                          |
| Theo dõi lệnh  | GET /api/operations/{operationId}                          |
| Trả xe         | POST /api/rides/{rideId}/return                            |
| Sạc xe cá nhân | POST /api/charging-requests                                |

Tìm kiếm, danh sách xe và chi tiết xe là public. Có thể mở `/#/student/search` để kiểm tra khi auth chưa chạy. Người dùng phải chọn GPS hoặc nhập tọa độ; frontend không tự gán vị trí. Đặt xe mở chi tiết xe từ kết quả backend đã tải. Nếu chưa có kết quả, mở danh sách xe với liên kết tìm kiếm. Chi tiết được tải lại từ server trước khi đặt.

Bearer token gửi cho các API cần quyền. Mốc giờ chuyển thành ISO-8601 UTC. Lỗi giữ nguyên HTTP status, code, details và requestId. Xe có dữ liệu thiếu hoặc reason cảnh báo không được đặt ở UI.

Nhận/trả xe sinh UUID Idempotency-Key cho mỗi thao tác. 202 chỉ hiển thị đang chờ, không tạo chuyến đi thành công. Poll mỗi 2 giây, tối đa 30 lần trong một lượt mở màn; người dùng có thể kiểm tra tiếp thủ công. Khi lỗi mạng/503, giữ key và payload cũ để đối soát bằng đúng yêu cầu đó. Không tự tạo lệnh khác khi kết quả chưa rõ. Kết quả SUCCEEDED mới chuyển trạng thái chuyến đi.

## Điểm cần backend hoàn thiện

Đối chiếu repository ngày 24/11/2026:

- `m1_student` commit `ca7ac1b`: có controller UC01–UC07, chưa có AccountController.
- `feat/system-auth-iot` commit `1f2eacd`: có AccountController nhưng login yêu cầu thêm `role`, register yêu cầu thêm `fullName`. Login response thiếu email/studentId so với contract (UI có thể dùng email vừa nhập). Chưa có logout endpoint.
- Backend cần hợp nhất auth và student flow dùng cùng kho account/session, đồng thời sửa DTO theo contract. Chạy hai server riêng biệt không tự chia sẻ token/session.
- Chưa có API liệt kê chỗ đỗ, xe cá nhân, lịch sử lượt đặt hoặc chuyến đang chạy. UI hiện cho nhập ID tại màn đỗ/sạc/trả và hiển thị các biên nhận nhận được trong phiên; không tạo danh sách giả.
- Quên/đổi mật khẩu, thông báo và operator API chưa được nối trong đợt mobile này.
- Logout xóa phiên trong frontend kể cả khi endpoint server lỗi; khi backend chưa có logout, token chưa được thu hồi phía server.

## Kiểm chứng

- Kiểm tra tự động payload, Bearer, UTC, conflict, response rỗng, 202/poll, idempotency khi retry, 401 xóa phiên, phản hồi cũ sau chuyển trang và trạng thái thiếu dữ liệu.
- Chạy Spring Boot thực tế từ worktree riêng của `ca7ac1b`, cổng 8085; gọi qua Vite API proxy cổng 5176: tìm Hub trả 200 (Hub H1, STALE, 0 xe), GET E20 trả 200 với reason `Telemetry is stale`, ID không có trả 404, đặt xe không token trả 401.
- Không sửa dữ liệu backend để biến xe thành khả dụng. Chưa chứng minh giao dịch đã đăng nhập thành công xuyên suốt vì DTO auth và nhánh backend chưa thống nhất.

Mã chính: `src/shared/api/mobile.js` (adapter/session/receipts/commands), `src/features/mobile-api.js` (mobile UI và lifecycle), `src/shared/api/client.js` (HTTP/error/timeout). `live-search.js` là renderer UC01 cũ, không còn được mount trong API mode.
