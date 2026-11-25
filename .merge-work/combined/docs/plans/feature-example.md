# T-03: Reserve Shared Vehicle (UC-03)

## Current state
- Backend đã có `InMemoryDataStore`, `StateCoordinator` và entity `Vehicle`.
- API tìm kiếm xe `POST /api/search/hubs` (UC-01) đã hoạt động.
- Chưa có endpoint đặt xe `POST /api/reservations` và chưa có cơ chế kiểm tra xe đang được giữ chỗ trong 15 phút.

## Goal
- Sinh viên có thể gửi yêu cầu đặt trước 1 xe chia sẻ (`ownership == SHARED`, `status == AVAILABLE`, pin $\ge 20\%$, telemetry `FRESH`).
- Khi đặt thành công:
  - Hệ thống sinh mã nhận xe ngẫu nhiên 6 chữ số (`pickupCode`) và trả về cho sinh viên (chỉ trả 1 lần).
  - Trạng thái xe chuyển thành `RESERVED`.
  - Đơn đặt xe có hạn chót 15 phút (`expiresAt = createdAt + 15m`).
- Nếu 2 sinh viên cùng đặt 1 xe cùng lúc, chỉ 1 người thành công (`201 Created`), người thứ hai nhận `409 Conflict`.

## Non-goals
- Không tích hợp camera quét QR vật lý (việc quét QR thuộc về Mobile App, backend chỉ xác thực mã băm).
- Không xây dựng cổng thanh toán tiền cọc trong PR này.
- Không sửa đổi cấu trúc dữ liệu của Hub hay Cổng sạc.

## Design decisions
- Trạng thái `RESERVED` của xe và đơn `Reservation` được quản lý trong bộ nhớ thông qua `StateCoordinator.commit(...)` với `expectedVersion` của `Vehicle` để đảm bảo race condition an toàn.
- `pickupCode` thô chỉ trả về trong body HTTP của lệnh tạo; cơ sở dữ liệu chỉ lưu chuỗi băm SHA-256 (`pickupCodeHash`).
- Sinh viên chỉ được có tối đa 01 đơn `Reservation` ở trạng thái `ACTIVE` cùng lúc.

## Files affected
- `src/main/java/bku/emobility/common/domain/Reservation.java`
- `src/main/java/bku/emobility/common/domain/ReservationStatus.java`
- `src/main/java/bku/emobility/service/ReservationService.java`
- `src/main/java/bku/emobility/controller/ReservationController.java`
- `src/main/java/bku/emobility/controller/dto/ReservationRequest.java`
- `src/main/java/bku/emobility/controller/dto/ReservationResponse.java`
- `src/test/java/bku/emobility/service/ReservationServiceTest.java`
- `src/test/java/bku/emobility/controller/ReservationControllerTest.java`

## Implementation
1. Tạo domain entity `Reservation` và enum `ReservationStatus` (`ACTIVE`, `CANCELLED`, `EXPIRED`, `PICKED_UP`).
2. Viết `ReservationService.reserveVehicle(studentId, vehicleId)`:
   - Kiểm tra sinh viên có đơn `ACTIVE` nào chưa $\rightarrow$ ném `ActiveReservationExistsException`.
   - Lấy `Vehicle` từ store, kiểm tra điều kiện cho thuê: `status == AVAILABLE`, `batteryPct >= 20.0`, `telemetry.quality == FRESH`.
   - Sinh chuỗi ngẫu nhiên 6 số, băm SHA-256 lưu vào `Reservation`.
   - Gọi `StateCoordinator.commit(vehicle.getVersion(), ...)` để cập nhật xe thành `RESERVED` và thêm đơn `Reservation`.
3. Viết `ReservationController` với endpoint `POST /api/reservations`:
   - Nhận header `Authorization: Bearer <token>`, trích xuất `studentId`.
   - Nhận body `{ "vehicleId": "..." }`.
   - Trả về `201 Created` kèm `pickupCode` dạng thô.
4. Bổ sung Exception Handler ánh xạ `OptimisticLockException` hoặc xe đã được đặt sang `409 Conflict`.
5. Viết Unit Tests & Concurrency Test: 2 luồng cùng đặt 1 xe $\rightarrow$ 1 thành công (201), 1 thất bại (409).

## Acceptance criteria
- Đặt xe hợp lệ: Xe chuyển sang `RESERVED`, đơn tạo thành công với hạn 15 phút, trả về mã 6 chữ số.
- Đặt xe có pin 19.9%: Trả về `422 Unprocessable Entity` với mã lỗi `VEHICLE_NOT_RENTABLE`.
- Đặt xe đang có người giữ: Trả về `409 Conflict`.
- Sinh viên đã có 1 đơn đang giữ: Trả về `409 Conflict`.
- Test đồng thời 10 luồng: Chính xác 1 luồng thành công, 9 luồng nhận 409.

## Verification
- `.\mvnw.cmd test -Dtest=ReservationServiceTest,ReservationControllerTest`
- `.\mvnw.cmd clean package`

