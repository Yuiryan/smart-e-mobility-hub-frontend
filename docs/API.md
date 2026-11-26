# Kết nối API mobile — UC00–UC07

Frontend dùng `docs/API-CONTRACT.md` cho các API nghiệp vụ. Riêng auth adapter mặc định theo DTO backend hiện tại: login có `role`, register có `fullName`.

## Chạy

```powershell
npm.cmd ci
npm.cmd run dev:api
```

Mở http://localhost:5173. Nếu cổng đã được dùng, thêm `-- --port 5176`. `npm.cmd run dev` vẫn là demo; `npm.cmd run build:api` build bản API. Vite proxy `/api` tới `http://localhost:8080`, thay bằng `API_PROXY_TARGET` trong `.env.local` nếu cần. Bản triển khai cần reverse proxy `/api` tương ứng.

Sau khi mở lại web, sinh viên đăng nhập rồi tải lượt đặt còn hạn và chuyến đang chạy từ `GET /api/student/journey`. Frontend dùng lại mã nhận xe đã lưu trong cùng trình duyệt khi khớp tài khoản, lượt đặt và hạn giữ. Token và mật khẩu ở bộ nhớ. Nếu API lỗi, màn hình cho phép thử tải lại.

## API đã gắn với UI

| Luồng         | Request                                                    |
| ------------- | ---------------------------------------------------------- |
| Đăng ký       | POST /api/auth/register — studentId, email, password       |
| Đăng nhập     | POST /api/auth/login — email, password                     |
| Đăng xuất     | POST /api/auth/logout                                      |
| Tìm Hub/xe    | POST /api/search/hubs                                      |
| Chi tiết xe   | GET /api/vehicles/{vehicleId}                              |
| Đặt/hủy xe    | POST /api/reservations; POST /api/reservations/{id}/cancel |
| Đặt chỗ đỗ    | POST /api/parking-reservations                             |
| Nhận xe       | POST /api/pickups                                          |
| Theo dõi lệnh | GET /api/operations/{operationId}                          |
| Trả xe        | POST /api/rides/{rideId}/return                            |
| Khung giờ sạc | GET /api/charging-requests/options                         |
| Đăng ký sạc   | POST /api/charging-requests                                |

Màn tìm Hub yêu cầu đăng nhập để lấy danh mục đầy đủ từ `GET /hubs`, sau đó người dùng bấm dùng vị trí và cấp quyền định vị. Không có ô nhập tọa độ hoặc bán kính. Chỉ tải danh sách sau khi lấy được vị trí thiết bị; từ chối quyền sẽ hiển thị hướng dẫn bật lại. Danh mục giữ tất cả Hub, kết hợp `POST /search/hubs` với vị trí thực để sắp xếp gần trước. Endpoint tìm xe giới hạn 50 km: Hub ngoài kết quả vẫn hiển thị, nhưng không tự suy diễn khoảng cách hay số xe. API tìm kiếm và chi tiết xe vẫn là public. Đặt xe mở chi tiết xe từ kết quả backend đã tải. Nếu chưa có kết quả, mở danh sách xe với liên kết tìm kiếm. Chi tiết được tải lại từ server trước khi đặt.

Bearer token gửi cho các API cần quyền. Mốc giờ chuyển thành ISO-8601 UTC. Lỗi giữ nguyên HTTP status, code, details và requestId. Xe có dữ liệu thiếu hoặc reason cảnh báo không được đặt ở UI.

Nhận/trả xe sinh UUID Idempotency-Key cho mỗi thao tác. 202 chỉ hiển thị đang chờ, không tạo chuyến đi thành công. Poll mỗi 2 giây, tối đa 30 lần trong một lượt mở màn; người dùng có thể kiểm tra tiếp thủ công. Khi lỗi mạng/503, giữ key và payload cũ để đối soát bằng đúng yêu cầu đó. Không tự tạo lệnh khác khi kết quả chưa rõ. Kết quả SUCCEEDED mới chuyển trạng thái chuyến đi.

## Cập nhật backend ngày 25/11/2026

Đã fetch repository mới nhất. Nhánh `feat/system-auth-iot` hiện ở `9765829`, đã có logout, me, khôi phục/đổi mật khẩu, lịch sạc và mô phỏng. Frontend đã gắn thêm các bước khôi phục/đổi mật khẩu: request → verify → recoveryToken → reset, token chỉ ở bộ nhớ; reset yêu cầu xác nhận mật khẩu mới.

DTO login bắt buộc role và register bắt buộc fullName; frontend hiện gửi đủ hai trường. Auth/student/operator vẫn ở các nhánh riêng, nên giao dịch xuyên suốt vẫn cần backend đang chạy tích hợp đủ các phần này.

API vận hành UC08–UC14 đã được nối trong API mode; xem [OPERATOR-API.md](OPERATOR-API.md) để biết endpoint, revision/version, idempotency và giới hạn kiểm chứng. API liệt kê Hub/chỗ đỗ đã có trong nhánh M2 nhưng chưa hợp nhất vào nhánh student.

## Kiểm chứng

- Kiểm tra tự động payload, Bearer, UTC, conflict, response rỗng, 202/poll, idempotency khi retry, 401 xóa phiên, phản hồi cũ sau chuyển trang và trạng thái thiếu dữ liệu.
- Chạy Spring Boot thực tế từ worktree riêng của `ca7ac1b`, cổng 8085; gọi qua Vite API proxy cổng 5176: tìm Hub trả 200 (Hub H1, STALE, 0 xe), GET E20 trả 200 với reason `Telemetry is stale`, ID không có trả 404, đặt xe không token trả 401.
- Không sửa dữ liệu backend để biến xe thành khả dụng. Payload auth đã khớp DTO trong mã backend được rà soát; chưa xác nhận giao dịch đăng nhập xuyên suốt trên backend tích hợp.

Mã chính: `src/shared/api/mobile.js` (adapter/session/receipts/commands), `src/features/mobile-api.js` (mobile UI và lifecycle), `src/shared/api/client.js` (HTTP/error/timeout). `live-search.js` là renderer UC01 cũ, không còn được mount trong API mode.

## Tương thích backend demo tích hợp

Để dùng DTO auth cũ theo `API-CONTRACT.md`, có thể đặt trong `.env.local`:

```dotenv
VITE_AUTH_PROFILE=contract
```

Chạy `npm.cmd run dev:api`. Form đăng ký gửi họ tên đang nhập; đăng nhập gửi STUDENT/OPERATOR theo màn đã chọn. Adapter mặc định dùng DTO backend hiện tại (có `fullName` khi đăng ký và `role` khi đăng nhập); chỉ đặt `VITE_AUTH_PROFILE=contract` khi kết nối dịch vụ còn dùng DTO cũ. Thông báo validation hiển thị tên trường và lý do backend trả về.

Đã kiểm tra ngày 25/11/2026 với backend demo ở D:/test, qua proxy localhost:5173 → localhost:8080: đăng ký 201, đăng nhập 200, /auth/me 200, đăng xuất 204. Kiểm tra dùng một tài khoản QA mới với email example.invalid; không thay đổi tài khoản của người dùng. Kết quả này xác nhận luồng tài khoản, không thay thế kiểm thử toàn bộ giao dịch xe/đỗ/sạc.
