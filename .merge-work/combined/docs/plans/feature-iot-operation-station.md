# M3 — Journal de commande và cảm biến trạm
## Current state
Adapter giữ receipt trong RAM. Tick hiện chỉ phát mẫu xe.
## Goal
Lưu intent và kết quả thiết bị theo ID, đối soát sau restore. Cấp mẫu trạm và cổng theo thời điểm tick.
## Non-goals
Quyền sở hữu Ride và API Student thuộc luồng tích hợp.
## Design decisions
Commit PENDING trước send. Retry chỉ query adapter. Thiếu receipt sau restart giữ AWAITING_RECONCILIATION. Terminal ACK/FAILED dùng lại từ snapshot. Frame trạm có occupiedSpaces và bản đồ portId với status/powerKw; dữ liệu thiếu giữ null. Cache đo xa theo vòng đời tiến trình.
## Files affected
iot/{DeviceOperation,JournaledIoTAdapter,StationSensors,SimulatorProperties,SimulatorConfiguration,IoTSimulator}.java, common/store/{StoreSnapshot,InMemoryDataStore}.java, GatewayStationTest.java, docs/03-api-contract.md, docs/04-data-schema.md và plan này.
## Implementation steps
Mở rộng snapshot, nối journal vào gateway qua Spring, đối soát khi ready. Bind frame trạm, phát theo tick và cung cấp phép đọc cho service.
## Acceptance criteria
Retry qua restart không gửi lại lệnh. Mẫu cổng bị thiếu không kế thừa giá trị frame trước.
## Verification
Người dùng chạy test và package sau test-compile.
