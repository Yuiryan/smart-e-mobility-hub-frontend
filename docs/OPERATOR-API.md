# API vận hành — UC08–UC14

Đối chiếu trực tiếp mã Java từ repository backend ngày 25/11/2026:

- `M2/Operator` tại `dc86996`: UC08–UC12 và Hub catalog.
- `feat/system-auth-iot` tại `9765829`: tài khoản, lịch sạc UC13, mô phỏng UC14, IoT.
- `m1_student` tại `ca7ac1b`: UC01–UC07.
- `main` vẫn ở `8a5aaad`; ba nhóm chức năng chưa được tích hợp trên một nhánh/server.

## Khởi chạy frontend

```powershell
npm.cmd ci
npm.cmd run dev:api
```

Mở `/#/account/operator`. Sau khi backend trả phiên hợp lệ với role OPERATOR, frontend chuyển sang `/#/operator/overview`. Token nằm trong bộ nhớ và dùng chung cho mobile/operator; không nhận token tự nhập, không tạo phiên giả. Chế độ demo vẫn dùng `npm.cmd run dev`.

`API_PROXY_TARGET` trong `.env.local` trỏ tới server backend tích hợp (mặc định `http://localhost:8080`). Không chia proxy auth/operator qua hai server độc lập: chúng không tự chia sẻ session hay dữ liệu.

## Các endpoint đã gắn với giao diện

| Màn hình               | Endpoint                                                                                                                                          |
| ---------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| Tổng quan              | GET `/operator/network-report`                                                                                                                    |
| Hub, chỗ đỗ            | GET `/hubs`, `/hubs/{id}/spaces`                                                                                                                  |
| Tài nguyên             | GET `/operator/vehicles?hubId=...`, `/operator/charging-ports?hubId=...`                                                                          |
| Chi tiết tài nguyên    | GET `/operator/vehicles/{id}`, `/operator/parking-spaces/{id}`, `/operator/charging-ports/{id}`                                                   |
| Can thiệp              | POST `/operator/interventions`; GET `/operator/audit-logs`                                                                                        |
| Cảnh báo               | GET `/operator/alerts`                                                                                                                            |
| Điều phối              | GET `/operator/dispatch-candidates?sourceHubId=...&transportMode=...`; GET/POST `/operator/dispatch-orders`; GET `/operator/dispatch-orders/{id}` |
| Hoàn tất/hủy điều phối | POST `/operator/dispatch-orders/{id}/arrival`, `/cancel`                                                                                          |
| Sự cố                  | GET/POST `/operator/incidents`; GET `/operator/incidents/{id}`; POST `/{id}/maintenance`, `/resolve`, `/dismiss`                                  |
| Lịch sạc               | POST `/operator/charging-schedules`; GET `/{id}`; PUT `/{id}/slots/{slotId}`; POST `/{id}/confirm`                                                |
| Mô phỏng               | POST `/operator/simulations`                                                                                                                      |
| Theo dõi IoT           | GET `/operations/{operationId}`                                                                                                                   |

Tất cả đường dẫn trên có tiền tố `/api`. Adapter ở `src/shared/api/operator.js`, màn hình/lifecycle ở `src/features/operator-api.js`. Không gọi OperatorEngine và không fallback sang demo.

## Quy tắc payload và trạng thái

- Bearer và role OPERATOR; HTTP 401 xóa phiên, HTTP 403 không gọi lại bằng dữ liệu demo.
- Can thiệp chỉ chọn `allowedActions` từ backend; gửi `expectedVersion`, `parameters` theo action. ID lượt đặt/phiên sạc/allocation lấy từ tài nguyên hiện tại, không tự suy đoán.
- Điều phối gửi sourceHubId, destinationHubId, vehicleIds, transportMode (TRUCK/SELF_DRIVE), reason. Server là nơi kiểm tra sức chứa và điều kiện xe.
- Sự cố gửi version của incident và resourceVersion cho maintenance/resolve. Ghi chú theo đúng reason/resolutionNote/dismissNote.
- Mỗi mutation M2 sinh UUID Idempotency-Key. Lỗi mạng/5xx giữ nguyên payload và key; thao tác mới bị chặn tới khi đối soát. Không đổi key khi retry. 409 hiển thị lỗi; người dùng tải lại để lấy version mới.
- Lệnh PENDING/RECONCILING chưa phải thành công; truy vấn trạng thái mỗi 2 giây, tối đa 30 lượt, có nút kiểm tra tiếp. Không áp dụng trạng thái tài nguyên lạc quan.
- UC13 dùng revision, PUT và startAt/endAt dạng ISO UTC. Backend không công bố idempotency cho nhóm này nên không tự retry khi kết quả chưa rõ; tải lại proposal để đối soát. Với tạo draft/mô phỏng bị mất response chưa có ID, thông báo rõ nguy cơ đã có bản nháp/kết quả trước khi người dùng bỏ trạng thái chờ.
- UC14 gửi scenarioName và parameters: simulatedDurationHours, rentalMultiplier, hubOutageId. Đề xuất chỉ mở form điều phối, vẫn cần chọn xe hợp lệ và xác nhận.
- Hủy request đọc/poll và đóng dialog khi rời route; mutation đang chạy có thể đã được server xử lý. Không ghi kết quả cũ vào màn hình mới hoặc phiên khác.

## Vướng mắc tích hợp đã xác minh

Login DTO tại `9765829` vẫn bắt buộc role, register vẫn bắt buộc fullName. Theo chỉ dẫn đã chốt của chủ dự án, frontend tiếp tục gửi contract email/password và studentId/email/password; không tự thêm hai trường này. Do đó chưa thể tuyên bố đăng nhập và toàn bộ UC chạy xuyên suốt với backend hiện tại.

Backend cần hợp nhất M1/M2/M3, thống nhất Account/Session, OperationController và kho dữ liệu; đồng thời sửa DTO auth hoặc thống nhất lại contract với nhóm. Không tự merge các nhánh backend có mô hình chung khác nhau trong đợt frontend này.

## Kiểm chứng

- M2/Operator: Maven controller tests chạy thành công, 18 tests / 0 failures / 0 errors (network, alerts, interventions, dispatch, incidents).
- M3 `9765829`: 28 tests / 0 failures / 0 errors trong AuthApiRegressionTest, ChargingSchedulerHttpTest và OperationAccessTest (HTTP server ngẫu nhiên trong test).
- Frontend: kiểm thử Bearer, 401/403, version conflict, exact-key retry, các tham số can thiệp, UTC/revision, recovery token trong bộ nhớ, render dữ liệu thực và bỏ response sau khi rời màn.
- Không dùng dữ liệu mock để tuyên bố E2E backend thành công. Chưa kiểm tra giao dịch đầy đủ qua một server backend tích hợp vì repository chưa có nhánh tích hợp đáp ứng các điều kiện trên.
