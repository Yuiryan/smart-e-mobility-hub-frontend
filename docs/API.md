# Tích hợp backend

Nguồn: https://github.com/PhuscCea07/Smart-E-Mobility-Hub — đã đọc `main` và `m1_student` tại commit `8a5aaad`.

Bản backend được kiểm tra chỉ có bootstrap, common/domain và common/store/StateCoordinator. Chưa có controller/API nghiệp vụ, DTO request-response, OpenAPI hoặc luồng auth hoàn chỉnh. Không suy ra endpoint thật từ class diagram.

## Các kiểu hiện có và chênh lệch cần thống nhất

| Backend hiện có                                                                 | Frontend demo                                | Việc cần chốt                                                     |
| ------------------------------------------------------------------------------- | -------------------------------------------- | ----------------------------------------------------------------- |
| Vehicle: id, hubId, batteryPct, status                                          | id, hubId, battery, status, type, observedAt | Mapping battery; API bổ sung type/telemetry time                  |
| Reservation: accountId, vehicleId, pickupCodeHash, createdAt, expiresAt, status | Booking kind VEHICLE; code demo hiển thị     | Không trả hash cho UI; backend cung cấp credential đúng quy trình |
| ParkingReservation: parkingSpaceId, startsAt, endsAt, holdExpiresAt             | Booking kind PARKING, resourceId, start/end  | Tên trường, mốc thời gian, chính sách giữ chỗ                     |
| ChargingRequest: vehicleId, hubId, targetBatteryPct, requestedAt, status        | Booking kind CHARGING với time window, cổng  | API cấp cổng/thời gian và tính estimate                           |
| Session: accountId, createdAt, expiresAt                                        | user trong bộ nhớ demo                       | Cookie/session, CSRF, logout, 401/403                             |
| AccountRole: STUDENT / OPERATOR                                                 | STUDENT / OPERATOR                           | Backend luôn kiểm tra role                                        |

## Điểm tích hợp

`src/domain/service.js` là adapter thay thế demo. `src/shared/api/client.js` có timeout 15 giây, AbortSignal, JSON, xử lý HTTP và credentials include; chưa gọi endpoint nghiệp vụ. Dev proxy `/api` → `API_PROXY_TARGET` (mặc định http://localhost:8080).

`VITE_DATA_SOURCE=mock` chạy demo. Chọn `api` hiện dừng và báo chưa tích hợp; không tự chuyển sang mock hoặc gửi các đường dẫn phỏng đoán. `VITE_API_BASE_URL` chỉ là URL công khai, không chứa secret.

Cần backend cung cấp: login/register/reset/session; tìm Hub/chi tiết xe; đặt/hủy xe và chỗ đỗ; mã pickup; gửi lệnh/tra cứu ACK; trả xe; đăng ký sạc; lịch sử. Thống nhất DTO, timezone ISO8601, lỗi 400/401/403/404/409, idempotency key và xử lý timeout. Đổi bộ nhớ demo sang gọi server qua adapter khi hợp đồng được duyệt.
