# Use cases và kế hoạch triển khai

Phân công frontend Submission 4 cho **hai người**, theo yêu cầu người sở hữu repo nhận phần nhiều hơn. Tạm gọi là **Bạn** và **Thành viên 2** vì chưa có tên/tài khoản của người còn lại. Phân công này thay thế phân công frontend theo năm người ở bản trước; không thay đổi tác giả báo cáo Submission 3. Mỗi UC vẫn là một hạng mục riêng.

| UC   | Chức năng            | Route starter                  | Phụ trách    | Trạng thái                       |
| ---- | -------------------- | ------------------------------ | ------------ | -------------------------------- |
| UC00 | Tài khoản            | `/account`                     | Bạn          | Scaffold                         |
| UC01 | Tìm Hub              | `/student/hubs`                | Bạn          | Ví dụ mock + adapter API đề xuất |
| UC02 | Chi tiết xe          | `/student/vehicles/:vehicleId` | Bạn          | Scaffold                         |
| UC03 | Đặt xe               | `/student/reservations`        | Bạn          | Scaffold                         |
| UC04 | Đặt chỗ đỗ           | `/student/parking`             | Bạn          | Scaffold                         |
| UC05 | Nhận xe QR           | `/student/pickup`              | Bạn          | Scaffold                         |
| UC06 | Trả xe               | `/student/return`              | Bạn          | Scaffold                         |
| UC07 | Đăng ký sạc          | `/student/charging`            | Bạn          | Scaffold                         |
| UC08 | Tổng quan mạng lưới  | `/operator/overview`           | Thành viên 2 | Scaffold                         |
| UC09 | Can thiệp tài nguyên | `/operator/interventions`      | Thành viên 2 | Scaffold                         |
| UC10 | Cảnh báo quá tải     | `/operator/alerts`             | Thành viên 2 | Scaffold                         |
| UC11 | Điều phối xe         | `/operator/dispatch`           | Thành viên 2 | Scaffold                         |
| UC12 | Quản lý sự cố        | `/operator/incidents`          | Thành viên 2 | Scaffold                         |
| UC13 | Lập lịch sạc         | `/operator/charging`           | Thành viên 2 | Scaffold                         |
| UC14 | Mô phỏng What-if     | `/operator/simulation`         | Thành viên 2 | Scaffold                         |
| UC15 | Cài đặt giao diện    | `/settings`                    | Bạn          | Preferences starter              |

## Khối lượng và phạm vi

Ước lượng **Bạn 60–65%, Thành viên 2 35–40%** cho phần frontend còn lại. Tỷ lệ tính cả nền tảng chung, tích hợp và kiểm thử, không chỉ số UC; đây là ước lượng lập kế hoạch, không phải số giờ hoặc tỷ lệ đóng góp đã thực hiện. Cần rà lại sau khi chốt độ phức tạp bảng lịch sạc và mô phỏng trong Figma.

| Hạng mục            | Bạn                                                                                                   | Thành viên 2                                                                          |
| ------------------- | ----------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------- |
| Nền tảng chung      | Chốt tokens, component dùng chung, router, HTTP client, session provider, route guard, cấu trúc vi/en | Góp yêu cầu cho bảng, bộ lọc, modal và trạng thái dashboard                           |
| Tài khoản UC00      | Đăng nhập, đăng ký, khôi phục mật khẩu; dùng chung hai vai trò                                        | Dùng lại luồng tài khoản, kiểm tra quyền ở giao diện vận hành                         |
| Sinh viên UC01–UC07 | Tìm Hub, chi tiết xe, đặt xe/chỗ đỗ, QR nhận xe, trả xe, đăng ký sạc                                  | Review và thử luồng bàn giao                                                          |
| Vận hành UC08–UC14  | Hỗ trợ thành phần chung và xử lý vướng mắc tích hợp                                                   | Layout vận hành, tổng quan, can thiệp, cảnh báo, điều phối, sự cố, lịch sạc, mô phỏng |
| Cài đặt UC15        | Theme, ngôn ngữ, cơ chế lưu lựa chọn và giữ form theo thiết kế                                        | Áp dụng cơ chế chung cho tất cả màn hình vận hành                                     |
| API theo feature    | Adapter/DTO, mock, xử lý lỗi cho UC00–UC07/15                                                         | Adapter/DTO, mock, xử lý lỗi cho UC08–UC14                                            |
| Kiểm thử            | Test các phần mình làm; phụ trách ghép luồng toàn hệ thống, cấu hình build và chuẩn bị bản demo       | Test các phần mình làm; sửa lỗi vận hành và hỗ trợ kiểm thử tích hợp                  |

Mỗi người hoàn thiện **cả giao diện, trạng thái lỗi, bản dịch và tích hợp API** của phần mình. Thành viên 2 không chỉ vẽ màn hình rồi chuyển phần kết nối API sang bạn. Xác thực/quyền thật, phân bổ tài nguyên, thuật toán lịch sạc/mô phỏng và ACK vẫn do backend chịu trách nhiệm; phân công này không bao gồm viết backend.

## Thứ tự triển khai và bàn giao

### Đợt 1 — nền tảng để hai người làm song song

- **Bạn:** tokens theo Figma, button/input/modal/status dùng chung, layout sinh viên, cấu trúc router, session/API client và cơ chế vi/en/light-dark; bắt đầu UC00.
- **Thành viên 2:** rà frame vận hành, thống kê component cần dùng, dựng layout vận hành và UC08 bằng mock.
- **Bàn giao:** thống nhất interface component, DTO/error contract và auth state. Không cần chờ backend hoàn tất để dựng UI bằng mock.

### Đợt 2 — luồng cốt lõi

- **Bạn:** UC01 → UC02 → UC03 → UC04; hoàn thiện UC15 để dùng xuyên suốt.
- **Thành viên 2:** UC10 → UC12 → UC09; triển khai loading/empty/stale/error và trạng thái lệnh chờ ACK.
- **Bàn giao:** các màn hình có frame tham chiếu, dữ liệu mock và adapter riêng; thay API thật khi contract được xác nhận.

### Đợt 3 — giao dịch và chức năng nâng cao

- **Bạn:** UC05 → UC06 → UC07; ghép luồng đặt–nhận–trả, xử lý QR/camera, xung đột và timeout theo contract.
- **Thành viên 2:** UC11 → UC13 → UC14; form điều phối, lịch sạc, cấu hình mô phỏng, kết quả và xác nhận áp dụng đề xuất.
- **Bàn giao:** UI không báo thành công trước xác nhận backend; kết quả mô phỏng không tự thay đổi dữ liệu thật.

### Đợt 4 — tích hợp và chuẩn bị nộp

- **Bạn:** điều phối tích hợp, kiểm tra session/quyền giữa hai khu vực, build, hướng dẫn chạy và kịch bản demo tổng.
- **Thành viên 2:** chạy kịch bản vận hành, sửa lỗi phần mình, đối chiếu Figma và hỗ trợ review phần sinh viên.
- **Cả hai:** nghiệm thu trên Chrome/Edge Windows, vi/en, light/dark và viewport theo Figma. Mỗi người tự sửa lỗi của feature mình phụ trách.

Chưa ấn định ngày vì chưa có hạn nộp và thời gian rảnh của hai người.

## Tránh sửa trùng và cách mở PR

- **Bạn** chủ trì thay đổi `src/App.tsx`, `src/shared/`, `src/styles/`, cấu hình build/CI và catalog route.
- **Thành viên 2** tạo module vận hành trong `src/features/` (overview, interventions, alerts, dispatch, incidents, charging-schedule, simulation), cùng component riêng của dashboard.
- Đây là cấu trúc thư mục cần tạo khi triển khai; starter hiện chưa có sẵn các module nghiệp vụ này.
- Khi cần component chung mới, thống nhất props trước. Thay đổi router/component chung trong PR nhỏ riêng; không tự sửa đồng thời cùng file.
- Nhánh theo UC: `feat/uc03-reservation`, `feat/uc08-overview`. Một PR tập trung một luồng; người còn lại review.
- Cả hai làm bản dịch và test trong module của mình. Bạn ghép bản cuối, không phải viết lại phần chưa hoàn tất của người còn lại.
- Chưa gán assignee GitHub vì chưa có username Thành viên 2; không tự mời collaborator.

## Definition of Done cho mỗi UC

- Link frame Figma cụ thể, kiểm tra Việt/Anh và sáng/tối.
- Luồng chính và nhánh lỗi theo Use Case Description được duyệt.
- Trạng thái loading, empty, error, pending, success nếu phù hợp.
- Backend contract được xác nhận và mock phân biệt rõ.
- Quyền truy cập backend được kiểm tra; không dùng UI guard thay server authorization.
- Test phần logic/UI có rủi ro, `npm run check` thành công.
- PR có ảnh, hướng dẫn thử và giới hạn còn lại; reviewer xác nhận.

## Luồng kiểm tra tích hợp tối thiểu

Đăng nhập đúng/sai/quyền; tìm Hub và dữ liệu rỗng/cũ; đặt cùng tài nguyên bị 409; QR hết hạn/sai; lệnh ACK trễ/mất; trả xe có sự cố/pin thấp; đăng ký sạc trùng lịch; dashboard mất dữ liệu; can thiệp bị từ chối; điều phối không đủ chỗ; proposal không tự áp dụng vào thực tế; đổi ngôn ngữ/theme giữ đúng trạng thái giao diện.

Những tiêu chí này là backlog kiểm thử, không phải kết quả kiểm thử đã thực thi.
