# Kết nối backend thật — UC01

Đã đối chiếu [backend](https://github.com/PhuscCea07/Smart-E-Mobility-Hub) nhánh **m1_student**, commit **58c5985**. `main` vẫn ở `8a5aaad`, chưa chứa controller UC01. `docs/03-api-contract.md` của backend là **draft**: chỉ API UC01 được hiện thực; các endpoint khác là đề xuất.

## Chạy hai repo trên Windows

Terminal backend (JDK 21 trở lên, Maven wrapper):

```powershell
git clone --branch m1_student https://github.com/PhuscCea07/Smart-E-Mobility-Hub.git
cd Smart-E-Mobility-Hub
.\mvnw.cmd spring-boot:run
```

Terminal frontend:

```powershell
npm.cmd ci
npm.cmd run dev:api
```

Mở http://localhost:5173. Nếu cổng đang được dùng bởi bản demo, dừng terminal demo hoặc chạy `npm.cmd run dev:api -- --port 5174`. Vite proxy `/api` sang `http://localhost:8080` (đổi bằng `API_PROXY_TARGET`). Không cần thay cấu hình CORS backend khi dùng proxy này. `npm.cmd run build:api` đóng gói bản API. Bản triển khai cần reverse proxy `/api` hoặc cấu hình API origin/CORS phù hợp.

Chế độ mặc định `npm.cmd run dev` với `.env.example` là demo. `dev:api` luôn bật API dù `.env.local` đang để mock. API mode không đăng nhập demo, không dùng xe/chỗ đỗ giả và không gửi endpoint chưa có. Các route chưa hỗ trợ hiển thị thông báo và đường về UC01.

## Endpoint đã kết nối

`POST /api/search/hubs`, public theo controller hiện tại:

```json
{
  "origin": { "latitude": 10.77, "longitude": 106.66 },
  "destination": null,
  "radiusKm": 3
}
```

UI cho chọn điểm đi hoặc điểm đến; chỉ gửi một điểm, điểm còn lại null. Controller sử dụng origin nếu cả hai được gửi. Bán kính >0, mặc định giới hạn 50 km. GPS chỉ điền form sau thao tác người dùng; tọa độ gửi tới backend khi bấm tìm kiếm, không lưu storage.

Response `{ hubs: [...] }` được giữ nguyên thứ tự server. Mỗi Hub hiển thị `hubId`, `hubName`, `distanceKm`, `availableVehicleCount`, `telemetryStatus`; mỗi xe hiển thị `vehicleId`, `batteryPct`, `status`. Không suy ra vị trí Hub/type/observedAt vì DTO chưa cung cấp. FRESH/STALE/MISSING là chất lượng dữ liệu, không phải trạng thái xe.

Backend quyết định xe đủ điều kiện: xe dùng chung, AVAILABLE, pin >=20%, telemetry còn mới. **Cấu hình trong nhánh hiện tại là `app.search.freshness-limit=PT10M`**, trong khi giá trị mặc định ở service là 15 giây. Frontend không áp lại ngưỡng riêng. Giới hạn bán kính cũng có thể được cấu hình phía backend; lỗi 400 sẽ yêu cầu kiểm tra đầu vào.

## Kết quả chạy thử

Đã khởi động Spring Boot thực tế và gọi qua Vite proxy. Tọa độ H1 trả HTTP 200, `Hub 1`, khoảng cách 0 km, `availableVehicleCount: 0`, `vehicles: []`, `telemetryStatus: STALE`. Dữ liệu mẫu có telemetry từ ngày 21/11/2026 nên đã cũ khi kiểm tra. Không sửa dữ liệu/backend để biến kết quả thành FRESH. Đã xác minh cùng kết quả trong UI trình duyệt.

## Những phần chưa thể nối

UC00, UC02–UC14 chưa có controller tương ứng trong nhánh kiểm tra. Auth Bearer, idempotency, operation polling và các DTO trong tài liệu backend vẫn là đề xuất. Chưa gửi password/token, chưa gọi các endpoint đó.

Các enum trong draft cũng ghi rõ một số chỗ lệch domain hiện tại. Cần nhóm backend chốt trước khi nối giao dịch; không âm thầm đổi enum hoặc dùng trạng thái demo làm hợp đồng thật.

`src/features/hub-search-api.js` xử lý UC01; `live-search.js` hiển thị loading/empty/error và kết quả. HTTP client có timeout, AbortSignal và giữ HTTP status. Response sai định dạng báo lỗi, không rơi về mock. Khi có API tiếp theo, bổ sung adapter/test theo controller thực tế.
