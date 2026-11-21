# Kiến trúc frontend

## Nguồn và phạm vi

Dựa trên kiến trúc đã thống nhất ở Submission 3 (`Kien_truc_tong_the.md`) và Figma Final. Đây là diễn giải ranh giới frontend, không thay thế báo cáo kiến trúc hoặc thêm backend mới.

```text
Browser SPA
  Student routes / Operator routes
        ↓
  Feature pages → shared UI + preferences
        ↓
  Feature API adapters → HTTP client
        ↓ HTTPS / REST / JSON
  Spring Boot Controllers → Services → Repositories
        ├─ In-memory state + data.json trên máy chủ
        └─ In-process IoT Simulator
```

Một repo và một bundle SPA dùng hai khu vực route để bắt đầu làm việc chung. Nếu nhóm cần hai bundle triển khai độc lập, đó là quyết định cần thống nhất riêng; starter hiện không cấu hình hai ứng dụng độc lập.

## Ranh giới trách nhiệm

- Frontend: render, điều hướng, kiểm tra cú pháp form, hiển thị loading/error/empty, gọi API, preferences.
- Backend: xác thực, phân quyền, kiểm tra nghiệp vụ, chống đặt trùng, phiên bản dữ liệu và ghi dữ liệu bền vững.
- IoT simulator nằm trong tiến trình backend; frontend chỉ theo dõi kết quả lệnh qua API.
- Không đặt cơ sở dữ liệu hoặc bản sao `data.json` trong frontend.
- Mock chỉ phục vụ phát triển UI; không thể hiện giao dịch thật hoặc xác nhận ACK.

## Các nguyên tắc nghiệp vụ cần giữ khi triển khai

- UC03/04/07: backend phân bổ tài nguyên nguyên tử; 409 cần hiển thị xung đột và yêu cầu chọn lại.
- UC05/06/09/12: gửi lệnh thành công không đồng nghĩa thiết bị đã thực hiện; chỉ hiển thị thành công khi backend xác nhận ACK phù hợp. Có trạng thái pending/failed/timeout/reconciliation.
- UC08/10: hiển thị thời điểm dữ liệu, cảnh báo stale/missing; chu kỳ simulator 3–5 giây không tự động là hợp đồng polling frontend.
- UC11: điều phối chỉ thay đổi trạng thái theo kết quả backend, không sửa mảng xe cục bộ như giao dịch thật.
- UC13/14: proposal và simulation là dữ liệu dự kiến; áp dụng vào thực tế cần bước xác nhận riêng.
- UC15: chỉ lưu lựa chọn giao diện; nếu cache form phải loại bỏ dữ liệu nhạy cảm và có phạm vi/TTL phù hợp.

## Auth chưa triển khai

Nút chọn khu vực trên starter là điều hướng preview. Không phải đăng nhập và không phải route guard. Trước khi tích hợp production, thêm session provider, route guard và xử lý 401/403 theo hợp đồng backend; server luôn kiểm tra quyền. Không suy ra quyền từ localStorage hoặc tham số URL.

## Quy ước mã nguồn

Tên class, type, biến, method và file dùng tiếng Anh. Nội dung giao diện cần cả `vi`/`en`. Dùng `String` IDs của API dưới dạng TypeScript `string`; không chuyển ID sang số thực. Enum/DTO phải bám OpenAPI đã duyệt, không suy ra hợp đồng từ mock.
