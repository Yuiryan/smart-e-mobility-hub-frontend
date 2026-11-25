# Kết nối API mobile — UC00–UC07

Frontend dùng `docs/API-CONTRACT.md` làm hợp đồng. Theo lựa chọn của chủ dự án ngày 24/11/2026, không tự thêm `role` vào login hoặc `fullName` vào register để chiều theo DTO backend đang lệch hợp đồng.

## Chạy

```powershell
npm.cmd ci
$env:VITE_AUTH_PROFILE = 'backend'
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

Màn tìm Hub yêu cầu đăng nhập để lấy danh mục đầy đủ từ `GET /hubs`, sau đó người dùng bấm dùng vị trí và cấp quyền định vị. Không có ô nhập tọa độ hoặc bán kính. Chỉ tải danh sách sau khi lấy được vị trí thiết bị; từ chối quyền sẽ hiển thị hướng dẫn bật lại. Danh mục giữ tất cả Hub, kết hợp `POST /search/hubs` với vị trí thực để sắp xếp gần trước. Endpoint tìm xe giới hạn 50 km: Hub ngoài kết quả vẫn hiển thị, nhưng không tự suy diễn khoảng cách hay số xe. API tìm kiếm và chi tiết xe vẫn là public. Đặt xe mở chi tiết xe từ kết quả backend đã tải. Nếu chưa có kết quả, mở danh sách xe với liên kết tìm kiếm. Chi tiết được tải lại từ server trước khi đặt.

Bearer token gửi cho các API cần quyền. Mốc giờ chuyển thành ISO-8601 UTC. Lỗi giữ nguyên HTTP status, code, details và requestId. Xe có dữ liệu thiếu hoặc reason cảnh báo không được đặt ở UI.

Màn xác nhận và danh sách lượt đặt đếm ngược theo `expiresAt` do BE trả về. Lượt đặt xe mới có hạn giữ mặc định 10 phút. Khi đồng hồ về `00:00`, màn hình báo hết hạn và ẩn thao tác nhận hoặc hủy lượt giữ.

Nhận/trả xe sinh UUID Idempotency-Key cho mỗi thao tác. 202 chỉ hiển thị đang chờ, không tạo chuyến đi thành công. Poll mỗi 2 giây, tối đa 30 lần trong một lượt mở màn; người dùng có thể kiểm tra tiếp thủ công. Khi lỗi mạng/503, giữ key và payload cũ để đối soát bằng đúng yêu cầu đó. Không tự tạo lệnh khác khi kết quả chưa rõ. Kết quả SUCCEEDED mới chuyển trạng thái chuyến đi.

## Dữ liệu IoT trong chế độ API

Khởi động backend với profile `iot-demo` trên bản sao snapshot ví dụ để thử UC01–UC07. Khi tìm Hub hoặc mở chi tiết xe, frontend nhận mẫu đo mới nhất mà backend có. Backend kiểm tra lại pin và thời điểm đo lúc đặt/nhận xe. Với luồng trả xe, ô đỗ phải khớp với mẫu cảm biến hiện tại. Profile mẫu báo ô đỗ gốc của từng xe.

Khi UC07 trả 503 do chờ xác nhận, gửi lại cùng xe, Hub, giờ bắt đầu và mức pin mục tiêu. Nếu yêu cầu đã có lịch, API trả lại cùng ID và FE hiển thị một biên nhận.

Xem thêm [API vận hành UC08–UC14](OPERATOR-API.md).

## Kiểm chứng

- Kiểm tra tự động payload, Bearer, UTC, conflict, response rỗng, 202/poll, idempotency khi retry, 401 xóa phiên, phản hồi cũ sau chuyển trang và trạng thái thiếu dữ liệu.
- Backend có kiểm thử tích hợp UC01–UC07 với simulator thật trong Spring. Kiểm thử bao gồm mẫu cũ/thiếu, ACK trễ, trả sai ô và gửi lại yêu cầu sạc.

Mã chính: `src/shared/api/mobile.js` (adapter/session/receipts/commands), `src/features/mobile-api.js` (mobile UI và lifecycle), `src/shared/api/client.js` (HTTP/error/timeout). `live-search.js` là renderer UC01 cũ, không còn được mount trong API mode.

## Tương thích backend demo tích hợp

Nếu backend yêu cầu `fullName` khi đăng ký và `role` khi đăng nhập, thêm vào `.env.local`:

```dotenv
VITE_AUTH_PROFILE=backend
```

Chạy `npm.cmd run dev:api`. Form đăng ký gửi họ tên đang nhập; đăng nhập gửi STUDENT/OPERATOR theo màn đã chọn. Mặc định `contract` giữ payload cũ để tương thích hợp đồng ban đầu. Thông báo validation hiển thị tên trường và lý do backend trả về.

Đã kiểm tra ngày 25/11/2026 với backend demo ở D:/test, qua proxy localhost:5173 → localhost:8080: đăng ký 201, đăng nhập 200, /auth/me 200, đăng xuất 204. Kiểm tra dùng một tài khoản QA mới với email example.invalid; không thay đổi tài khoản của người dùng. Kết quả này xác nhận luồng tài khoản, không thay thế kiểm thử toàn bộ giao dịch xe/đỗ/sạc.
