# M3 — UC-14 mô phỏng What-if
## Current state
Có snapshot bất biến và gateway telemetry. Chưa có endpoint simulation. Ride cũ thiếu tuyến và quãng đường để tái dựng nhu cầu.
## Goal
Chạy mô phỏng theo bước thời gian trên bản sao cách ly, tổng hợp nguy cơ quá tải và đề xuất điều phối.
## Non-goals
Áp dụng đề xuất vào dữ liệu thực qua luồng dispatch của M2 nằm ở bước tích hợp.
## Design decisions
- Ride thêm originHubId,destinationHubId,distanceKm nullable, constructor cũ giữ nguyên. Lịch sử thiếu tuyến/quãng đường được báo missingData, không đoán nhu cầu.
- Baseline lấy các Ride hoàn tất trong 24 giờ gần nhất, theo tuyến có đủ dữ liệu. Tỷ lệ lượt/giờ nhân rentalMultiplier, dồn phần lẻ xác định theo bước 15 phút. Thời lượng chuyến lấy trung bình lịch sử. Đầu vào không có lịch sử hợp lệ thì chỉ báo trạng thái ban đầu và thiếu baseline.
- Tham số scenario theo contract. Thời lượng >0 và <=168 giờ; multiplier từ 0 tới100, hữu hạn. Đây là giới hạn triển khai. Cấu hình consumption-percent-per-km mặc định 1.0 là giả định tiêu hao cho mô phỏng, có thể thay đổi.
- Sao chép StoreSnapshot sang InMemoryDataStore riêng. Mô hình hub/vehicle riêng giữ occupancy và pin, không ghi live state và không gửi command.
- Occupancy lấy từ ParkingSpace của snapshot, chỉ tin khi số record khớp capacity. Xe khả dụng phải AVAILABLE, telemetry FRESH, pin >=20. Mẫu thiếu/cũ được liệt kê. Hub outage không cho thuê/trả/điều phối.
- Chuyến đi mô phỏng giảm pin theo distanceKm, giữ xe IN_USE tới thời điểm đến. Trạm đích đầy hoặc outage ghi failedReturns và xe tiếp tục ngoài trạm.
- Quá tải xét peak occupancy >=85%. Đề xuất chọn nguồn còn xe vượt mức dự trữ 3, giải phóng nguồn quá tải hoặc bù trạm còn <=2 xe, trong sức chứa trạm đích. Gợi ý theo khoảng cách hub và ID để kết quả tái lập.
- Response giữ các trường contract và thêm sourceVersion,generatedAt,failedBookingsCount,failedReturnsCount,congestionMetrics,missingData. Các giá trị này phục vụ đối chiếu snapshot.
## Files affected
- common/domain/Ride.java.
- simulation/{Scenario,SimulationResult,SimulationProperties,SimulationState,SimulationEngine}.java.
- controller/SimulationController.java, common/exception/SimulationException.java, common/web/GlobalExceptionHandler.java.
- src/test/.../SimulationEngineTest.java, SimulationHttpTest.java.
- docs/03-api-contract.md, docs/04-data-schema.md và plan này.
## Implementation steps
Bổ sung nguồn tuyến tương thích; tạo snapshot và mô hình riêng; chạy sự kiện thuê/trả theo thời gian; tổng hợp peak và đề xuất; nối endpoint Operator và test.
## Acceptance criteria
Không commit live state, không gửi IoT command; kết quả có thể tái lập với cùng snapshot và thời gian. Pin thấp/telemetry thiếu không được cho thuê; thiếu lịch sử được công bố; không vượt capacity khi nhận xe.
## Verification
Agent test-compile. Người dùng chạy test và package trước commit UC-14.

Giới hạn mặc định 100000 lượt dự báo mỗi lần chạy. Kịch bản lớn hơn trả lỗi 400 SIMULATION_TOO_LARGE. Chuyến đang chạy chỉ được đưa vào khi có tuyến, baseline duration và pin còn mới; thiếu dữ liệu được báo.

## Trạng thái kiểm chứng
Đã thêm 24 lượt test dự kiến cho engine và HTTP. Source và test đã biên dịch thành công. Thực thi test và commit do người dùng thực hiện.
