# UC-01: Search Mobility Hubs and Available Vehicles

## Current state
- `POST /api/search/hubs` and distance/rentability filtering are implemented.
- Search excludes vehicles unless they are shared, `AVAILABLE`, have battery at least 20%, and telemetry within the configured freshness limit.
- Constructor fallback previously differed from the 10-minute domain rule.
- The search response is a list of HubSearchResultDTO records containing the eligible VehicleSummaryDTO records.
- There are no UC-01 tests in `src/test/java`.

## Goal
- Complete UC-01 backend request validation and response fields according to Submission 3 and the API contract.
- Keep GPS permission prompts and manual location selection in the frontend; backend requires valid `origin` or `destination` and never invents a location.
- Complete backend search behavior and retain boundary checks in existing validation/domain logic.

## Non-goals
- Implement frontend GPS behavior.
- Change persistent entities or snapshot schema.
- Add dependencies or change unrelated use cases.

## Design decisions
- Keep existing `POST /api/search/hubs` contract and public/authenticated access.
- Continue filtering rentable vehicles in the domain method using configured battery and freshness thresholds; telemetry older than the limit, missing, or future-dated is not searchable.
- Build the UML result from hub identity, distance, rentable vehicles and telemetry quality.
- Preserve coordinate checks in `GeoPoint` and radius checks in controller/service validation.
- Keep this use case read-only; no `StateCoordinator` commit is needed.

## Files affected
- `docs/plans/feature-uc01-search-hubs.md`
- `docs/03-api-contract.md`
- `src/main/java/vn/edu/bku/smart_emobility_backend/common/store/InMemoryParkingSpaceDataStore.java`
- `src/main/java/vn/edu/bku/smart_emobility_backend/service/HubSearchService.java`
- `src/main/java/vn/edu/bku/smart_emobility_backend/common/domain/Vehicle.java`
- `src/main/java/vn/edu/bku/smart_emobility_backend/dto/HubSearchResultDTO.java`
- `src/main/java/vn/edu/bku/smart_emobility_backend/dto/VehicleSummaryDTO.java`

## Implementation
1. Build HubSearchResultDTO and VehicleSummaryDTO from eligible vehicles grouped by Hub.
2. Align freshness fallback and `Vehicle.isSearchable` validity checks with the 10-minute domain rule and safe telemetry behavior.
3. Validate request coordinate ranges and radius, preserving service-level validation.
4. Update the UC-01 API response documentation to match the implemented DTO fields and enum values.

## Acceptance criteria
- A shared `AVAILABLE` vehicle at exactly 20% with fresh telemetry appears; one at 19.9% does not.
- Reserved, private, low-battery, missing/stale/future telemetry vehicles do not appear.
- Search returns hubId, hubName, distanceKm, availableVehicleCount, vehicles and telemetryStatus.
- Invalid/missing coordinates and radius outside `0 < radiusKm <= 50` are rejected; no implicit location is used.
- Boundary and invalid-input behavior remains encoded in backend validation and rentability filtering; no test source was added per request.

## Verification
- `./mvnw.cmd test`
- `./mvnw.cmd clean package -DskipTests=false`

## Mô hình lớp và phương thức

Nguồn thiết kế: `UC01/UC01.drawio` và `UC01/UC01_Method_Description.docx`. Dấu `?` biểu thị giá trị tùy chọn. Phạm vi của mỗi lớp bên dưới là phần xuất hiện trong UC01.

### SearchScreen «boundary»

Thuộc tính: `-selectedLocation: GeoPoint`, `-destination: GeoPoint`, `-searchRadiusKm: double`.

- `+enterDestination(): void` — Cho phép người dùng nhập điểm đến.
- `+getCurrentLocation(): GeoPoint` — Lấy vị trí hiện tại được người dùng cho phép chia sẻ.
- `+requestHubSearch(): void` — Tạo yêu cầu từ vị trí, điểm đến và bán kính hiện tại rồi gửi đến SearchController qua REST.
- `+displayHubResults(results: List<HubSearchResultDTO>): void` — Hiển thị từng Hub, khoảng cách và số xe đủ điều kiện từ kết quả tìm kiếm.
- `+displaySearchError(message: String): void` — Hiển thị lý do xử lý thất bại do API hoặc trình duyệt trả về và cho phép thử lại.

### SearchController «controller»

Thuộc tính: `-hubSearchService: HubSearchService`.

- `+searchNearbyHubs(request: SearchHubsRequestDTO): SearchHubsResponseDTO` — Endpoint tìm Hub gần vị trí hoặc điểm đến.
- `+validateRequest(request: SearchHubsRequestDTO): void` — Kiểm tra cấu trúc và miền giá trị của request.

### HubSearchService «service»

Thuộc tính: `-minBatteryPct: double = 20`, `-freshnessLimit: Duration`, `-clock: Clock`.

- `+findSuitableHubs(criteria: SearchCriteria): List<HubSearchResultDTO>` — Thực hiện nghiệp vụ tìm Hub và tổng hợp xe khả dụng. 1. Gọi validateSearchCriteria(criteria). 2. Lấy Hub phù hợp từ HubDataStore.findNearby(criteria). 3. Với mỗi Hub, lấy xe bằng VehicleDataStore.listByHub(hubId). 4. Đánh giá trạng thái, pin và telemetry của từng xe bằng Vehicle.isSearchable(...). 5. Tính khoảng cách bằng MobilityHub.distanceTo(point). 6. Tạo HubSearchResultDTO và sắp xếp theo khoảng cách tăng dần.
- `+validateSearchCriteria(criteria: SearchCriteria): void` — Kiểm tra tiêu chí nghiệp vụ trước khi truy vấn. 1. Kiểm tra origin hoặc destination tồn tại. 2. Kiểm tra radiusKm nằm trong giới hạn cấu hình. 3. Chuẩn hóa điểm tham chiếu dùng để tính khoảng cách.
- `+buildHubResults(hubs: List<MobilityHub>, vehiclesByHub: Map<ID,List<Vehicle>>): List<HubSearchResultDTO>` — Chuyển dữ liệu miền thành kết quả phù hợp cho client. 1. Tính khoảng cách cho từng Hub. 2. Lọc xe thỏa điều kiện khả dụng. 3. Tạo VehicleSummaryDTO cho từng xe được hiển thị. 4. Gắn telemetryStatus để phân biệt dữ liệu mới, cũ hoặc thiếu.
- `+isTelemetryFresh(vehicle: Vehicle, now: DateTime): Boolean` — So sánh tuổi của mẫu gần nhất với giới hạn độ mới cấu hình bằng đồng hồ của backend.

### HubDataStore «interface»

- `+findNearby(criteria: SearchCriteria): List<MobilityHub>` — Tìm Hub nằm trong bán kính yêu cầu.

### VehicleDataStore «interface»

- `+listByHub(hubId: ID): List<Vehicle>` — Lấy các xe thuộc một Hub.

### MobilityHub «entity»

Thuộc tính: `-hubId: ID`, `-name: String`, `-address: String`, `-location: GeoPoint`, `-serviceRadiusKm: double`, `-parkingCapacity: int`, `-chargingCapacityKW: double`, `-expectedIncoming: int`, `-expectedOutgoing: int`.

- `+distanceTo(point: GeoPoint): double` — Tính khoảng cách địa lý giữa Hub và điểm tham chiếu.

### Vehicle «entity»

Thuộc tính: `-vehicleId: ID`, `-hubId: ID?`, `-parkingSpaceId: ID?`, `-vehicleType: VehicleType`, `-batteryPct: double?`, `-batteryCapacityKWh: double`, `-maxChargingPowerKW: double`, `-status: VehicleStatus`, `-telemetryAt: DateTime?`, `-version: long`, `-ownership: VehicleOwnership`, `-ownerAccountId: ID?`.

- `+isSearchable(now: DateTime, freshnessLimit: Duration, minBatteryPct: double): Boolean` — Xác định xe có được tính là khả dụng trong kết quả tìm kiếm hay không. 1. Kiểm tra status == AVAILABLE. 2. Kiểm tra batteryPct >= minBatteryPct. 3. Kiểm tra telemetryAt không null và now - telemetryAt <= freshnessLimit. 4. Chỉ trả true khi tất cả điều kiện đều đạt.

### IoTGateway «interface»

- `+getVehicleTelemetry(vehicleId: ID): VehicleTelemetry` — Lấy dữ liệu telemetry từ thiết bị hoặc bộ giả lập.

### SearchHubsRequestDTO «DTO»

Thuộc tính: `+origin: GeoPoint?`, `+destination: GeoPoint?`, `+radiusKm: double`.

- `+validate(): ValidationResult` — Kiểm tra trường bắt buộc, kiểu dữ liệu và miền giá trị trước khi chuyển yêu cầu sang bước tiếp theo.

### SearchCriteria «value object»

Thuộc tính: `+origin: GeoPoint?`, `+destination: GeoPoint?`, `+radiusKm: double`.

### SearchHubsResponseDTO «DTO»

Thuộc tính: `+hubs: List<HubSearchResultDTO>`.

### HubSearchResultDTO «DTO»

Thuộc tính: `+hubId: ID`, `+hubName: String`, `+distanceKm: double`, `+availableVehicleCount: int`, `+vehicles: List<VehicleSummaryDTO>`, `+telemetryStatus: DataQuality`.

### VehicleSummaryDTO «DTO»

Thuộc tính: `+vehicleId: ID`, `+batteryPct: double?`, `+status: VehicleStatus`.

### GeoPoint «value object»

Thuộc tính: `+latitude: double`, `+longitude: double`.

### VehicleTelemetry «value object»

Thuộc tính: `+vehicleId: ID`, `+batteryPct: double?`, `+status: VehicleStatus?`, `+observedAt: DateTime`, `+quality: DataQuality`.

### Quan hệ với thực thể

- `HubDataStore` → `MobilityHub` (stores / reads).
- `VehicleDataStore` → `Vehicle` (stores / reads).
- `MobilityHub` → `Vehicle` (current location · 0..1 / 0..*).
