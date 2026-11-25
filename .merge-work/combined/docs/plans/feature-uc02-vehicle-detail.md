# UC-02: View Vehicle and Hub Detail

## Current state
- The API contract defines `GET /api/vehicles/{vehicleId}`, and the design includes a reservation event refresh.
- `Vehicle` stores battery and telemetry timestamp only; the assigned `IoTGateway` implementation is not present yet.
- Hub lookup and reservation data already exist in the in-memory snapshot.

## Goal
- Return VehicleDetails with vehicleId, status, batteryLevel, updatedAt, reason and missingData. Combine the stored business state with the latest IoT reading.
- Return not found for unknown vehicle IDs and explicitly represent missing telemetry without inventing healthy/default values.

## Non-goals
- Add IoT integration or generate telemetry in this use case.
- Change routes outside UC-02 or persistent snapshot top-level structure.
- Add dependencies or test source files.

## Design decisions
- Keep the contract endpoint and read-only behavior.
- Represent telemetry fields as optional where the schema allows them; quality is derived from observed time and configured freshness limit, and missing telemetry is `MISSING`.
- Read telemetry through the shared IoT gateway. Missing fields remain explicit in VehicleDetails.
- Return 404 for an unknown vehicle.

## Files affected
- `src/main/java/vn/edu/bku/smart_emobility_backend/controller/VehicleDetailsController.java`
- `src/main/java/vn/edu/bku/smart_emobility_backend/service/VehicleLookupService.java`
- `src/main/java/vn/edu/bku/smart_emobility_backend/common/store/VehicleDataStore.java`
- `src/main/java/vn/edu/bku/smart_emobility_backend/common/store/InMemoryVehicleDataStore.java`
- `src/main/java/vn/edu/bku/smart_emobility_backend/dto/VehicleDetails.java`
- `docs/03-api-contract.md`

## Implementation
1. Add `VehicleDataStore.findById` và `VehicleLookupService.getVehicleDetails`.
2. Implement `VehicleDetailsController.getVehicleDetails` — read-only lookup endpoint.
3. Derive telemetry quality với configured freshness threshold, preserve missing values.
4. Align response enum và optional fields với actual domain types.
5. Refresh the selected vehicle through `onVehicleReserved(vehicleId)` after a confirmed booking event.

## Acceptance criteria
- Xe tồn tại → trả về đúng fields, telemetry FRESH → `reason=null`, `missingData=null`.
- Telemetry STALE → `reason="Telemetry is stale"`, `missingData=null`.
- Telemetry MISSING (`telemetryAt==null`) → `reason="Telemetry is missing"`, `missingData="telemetryAt"`.
- Unknown ID → `404 Not Found`.
- Không có state mutation.
- Sự kiện đặt thành công làm mới đúng xe đang xem. Telemetry đến muộn không ghi đè RESERVED.

## Verification
- `.\mvnw.cmd test`
- `.\mvnw.cmd clean package -DskipTests=false`

## Mô hình lớp và phương thức

Nguồn thiết kế: `UC02/UC02.drawio` và `UC02/UC02_Method_Description.docx`. Dấu `?` biểu thị giá trị tùy chọn. Phạm vi của mỗi lớp bên dưới là phần xuất hiện trong UC02.

### VehicleDetailsView «boundary»

- `+selectVehicle(vehicleId: ID): void` — Lấy chi tiết của xe sinh viên vừa chọn tại Mobility Hub.
- `+showDetails(details: VehicleDetails): void` — Trình bày dữ liệu của xe đang xem.

### VehicleDetailsController «controller»

- `+getVehicleDetails(vehicleId: ID): VehicleDetails` — Tiếp nhận yêu cầu getVehicleDetails qua REST và trả kết quả cho trình duyệt.
- `+onVehicleReserved(vehicleId: ID): VehicleDetails` — Tiếp nhận yêu cầu onVehicleReserved qua REST và trả kết quả cho trình duyệt.

### VehicleLookupService «service»

- `+getVehicleDetails(vehicleId: ID): VehicleDetails` — Tổng hợp thông tin xe từ bản ghi hệ thống và số đo IoT. Trả thông tin có thể xác nhận. Dữ liệu thiếu hoặc cũ phải được ghi rõ.
- `+onVehicleReserved(vehicleId: ID): VehicleDetails` — Cập nhật màn hình khi xe vừa được người khác đặt. Trả trạng thái Đã đặt. Chỉ cập nhật màn hình nếu sinh viên đang xem đúng xe. Số đo cũ đến muộn không ghi đè trạng thái này.

### VehicleDataStore «interface»

- `+findById(vehicleId: ID): Optional<Vehicle>` — Tra cứu VehicleDataStore theo định danh bản ghi.

### Vehicle «entity»

Thuộc tính: `-vehicleId: ID`, `-hubId: ID?`, `-parkingSpaceId: ID?`, `-vehicleType: VehicleType`, `-batteryPct: double?`, `-batteryCapacityKWh: double`, `-maxChargingPowerKW: double`, `-status: VehicleStatus`, `-telemetryAt: DateTime?`, `-version: long`, `-ownership: VehicleOwnership`, `-ownerAccountId: ID?`.

### IoTGateway «interface»

- `+getVehicleStatus(vehicleId: ID): VehicleStatus` — Đọc trạng thái thiết bị mô phỏng của xe kèm thời điểm quan sát để phát hiện dữ liệu cũ hoặc thiếu.
- `+readVehicleStatus(vehicleId: ID): VehicleReading` — Đọc số đo của một xe qua bộ kết nối IoT.

### SimulatorAdapter «adapter»

- `+send(command: DeviceCommand): DeviceDispatchResult` — Gửi lệnh nhận diện bằng commandId đến bộ giả lập.
- `+getResult(commandId: ID): DeviceResponse` — Tìm kết quả lệnh thiết bị theo commandId. Giữ trạng thái chờ nếu chưa nhận được ACK.
- `+emitTelemetry(): void` — Phát mẫu pin, vị trí và trạng thái cổng sạc có thời điểm quan sát từ mô phỏng tới backend.

### IoTSimulator «simulator»

Thuộc tính: `-tickInterval: Duration = 3..5 seconds`.

- `+tick(now: DateTime): void` — Tiến đồng hồ mô phỏng tới thời điểm now, cập nhật dữ liệu pin, GPS và cổng sạc cho chu kỳ kế tiếp.
- `+handleCommand(command: DeviceCommand): DeviceResponse` — Đối chiếu đích và trạng thái của lệnh mô phỏng, thực hiện tác động hợp lệ rồi trả ACK mang đúng mã lệnh.

### VehicleDetails «DTO»

Thuộc tính: `+vehicleId: ID`, `+status: VehicleStatus?`, `+batteryLevel: Percentage?`, `+updatedAt: DateTime?`, `+reason: String?`, `+missingData: String`.

### VehicleTelemetry «value object»

Thuộc tính: `+vehicleId: ID`, `+batteryPct: double?`, `+status: VehicleStatus?`, `+observedAt: DateTime`, `+quality: DataQuality`.

### Quan hệ với thực thể

- `VehicleDataStore` → `Vehicle` (stores / reads).
