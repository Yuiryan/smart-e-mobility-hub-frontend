# 00 — Product Context & Problem Statement

Tài liệu này định hình bức tranh toàn cảnh về sản phẩm, đối tượng người dùng, các use case trọng tâm, giới hạn phạm vi (in-scope/out-of-scope), và các quyết định kỹ thuật đã được chốt của dự án **Smart E-Mobility Hub**.

---

## 1. Tên Dự Án & Thông Tin Đồ Án
- **Tên dự án:** Smart E-Mobility Hub (Hệ thống Trạm điều phối và Dịch vụ Di chuyển Xanh Thông minh).
- **Môn học:** Kỹ thuật Phần mềm (Software Engineering - CO3001), Học kỳ 261.
- **Đơn vị:** Khoa Khoa học & Kỹ thuật Máy tính — Trường Đại học Bách Khoa, ĐHQG-HCM (HCMUT).
- **Nhóm thực hiện:** Nhóm A01 - Lớp L07 (Nhóm 7).

---

## 2. Bài Toán Nghiệp Vụ (Problem Statement)

Đô thị Đại học Quốc gia TP.HCM (VNU-HCM tại TP. Thủ Đức/Dĩ An) có diện tích trải rộng hơn 643 hecta với nhiều trường thành viên (HCMUT cơ sở 2, ĐH Khoa học Tự nhiên, ĐH Khoa học Xã hội & Nhân văn, ĐH Quốc tế, ĐH Công nghệ Thông tin, ĐH Kinh tế - Luật), các khu Ký túc xá (Khu A, Khu B), Thư viện Trung tâm và kết nối tuyến Metro số 1 (Bến Thành - Suối Tiên).

**Các thách thức di chuyển hiện tại:**
1. **Khoảng cách di chuyển lớn:** Cự ly giữa ký túc xá, trạm xe buýt/metro và các khu giảng đường từ 2 đến 5 km, gây khó khăn cho việc đi bộ dưới thời tiết nắng nóng hoặc mưa lớn.
2. **Thiếu hạ tầng chia sẻ xe điện:** Sinh viên chưa có hệ thống thuê xe đạp điện / xe máy điện công cộng thuận tiện, giá rẻ, dễ tiếp cận tại các điểm nút giao thông.
3. **Mất cân bằng tải giữa các trạm:** Vào giờ cao điểm (sáng sớm và chiều tối), các trạm ở Ký túc xá thường cạn kiệt xe (thiếu xe phục vụ), trong khi trạm tại các giảng đường lại đầy ứ chỗ đỗ (không còn chỗ trả xe).
4. **Hạ tầng sạc phân mảnh:** Xe điện cá nhân của sinh viên thiếu điểm sạc thông minh được kiểm soát công suất, dễ gây quá tải cục bộ lưới điện nội khu.
5. **Thiếu giám sát và phản hồi sự cố:** Không có hệ thống theo dõi trạng thái pin xe, chất lượng dữ liệu đo xa (telemetry), dẫn đến tình trạng sinh viên nhận phải xe cạn pin hoặc hư hỏng.

**Giải pháp Smart E-Mobility Hub:**
Hệ thống phần mềm quản lý mạng lưới trạm giao thông xanh thông minh (Mobility Hubs), kết hợp:
- **Ứng dụng di động cho Sinh viên:** Tìm trạm, kiểm tra xe khả dụng, đặt trước xe (giữ chỗ 15 phút), mở khóa lấy xe bằng mã QR hoặc mã 6 chữ số, trả xe tự động, và đăng ký sạc xe cá nhân.
- **Bảng điều khiển Web cho Nhân viên Vận hành (Operator):** Giám sát bản đồ mạng lưới thời gian thực, nhận cảnh báo quá tải trạm (ngưỡng 85%), ra lệnh điều phối xe tái cân bằng (dispatch rebalancing), can thiệp khóa/mở xe từ xa, xử lý sự cố bảo trì, lập lịch sạc thông minh và chạy mô phỏng kịch bản giả lập (What-if scenario).
- **Trình giả lập IoT tích hợp (In-Process IoT Simulator):** Giả lập tín hiệu đo xa, cảm biến trạm, và phản hồi lệnh phần cứng mà không cần thiết bị IoT vật lý tốn kém trong giai đoạn thử nghiệm học thuật.

---

## 3. Chân Dung Người Dùng (User Personas)

### 3.1. Sinh viên VNU-HCM (`STUDENT` / `SINH_VIEN`)
- **Đặc điểm:** Sinh viên, giảng viên hoặc cán bộ đang học tập, làm việc tại khuôn viên VNU-HCM.
- **Nhu cầu chính:**
  - Tìm trạm xe điện gần nhất có sẵn xe đạt chuẩn pin ($\ge 20\%$).
  - Đặt trước xe trong 15 phút để đảm bảo có xe khi đi bộ ra trạm.
  - Quét mã QR hoặc nhập mã PIN 6 số trên xe để mở khóa và bắt đầu chuyến đi nhanh chóng.
  - Tìm chỗ đỗ trống tại trạm đích và trả xe an toàn.
  - Đăng ký sạc xe điện cá nhân theo khung giờ học tập.
- **Kênh tương tác:** Mobile Web hoặc Native Mobile App (giao diện tối ưu cho màn hình cảm ứng di động).

### 3.2. Nhân viên Vận hành Mạng lưới (`OPERATOR` / `VAN_HANH`)
- **Đặc điểm:** Nhân sự thuộc Trung tâm Quản lý Vận hành Giao thông nội bộ VNU-HCM.
- **Nhu cầu chính:**
  - Giám sát toàn diện mạng lưới trạm, số lượng xe, tỷ lệ chiếm dụng chỗ đỗ và công suất sạc.
  - Nhận cảnh báo tức thời khi trạm quá tải ($\ge 85\%$ chỗ đỗ bị chiếm) hoặc cạn kiệt xe ($\le 2$ xe khả dụng).
  - Khởi tạo và quản lý Lệnh điều phối xe (Dispatch Orders) để xe tải chuyển xe từ trạm thừa sang trạm thiếu.
  - Khóa cưỡng bức hoặc chuyển xe/cổng sạc sang trạng thái Bảo trì (`MAINTENANCE`) khi phát hiện hư hại hoặc có báo cáo sự cố.
  - Chạy mô phỏng kịch bản giả lập (What-if simulation) để đánh giá tác động trước khi quyết định điều phối quy mô lớn.
- **Kênh tương tác:** Desktop Web Console (giao diện máy tính bảng hoặc PC với bản đồ rộng, bảng biểu phân tích và công cụ quản trị).

### 3.3. Hệ thống Giả lập & Tiến trình Nền (`SYSTEM` / `SIMULATOR`)
- Bộ giả lập IoT chạy ngầm trong tiến trình backend (In-Process Simulator), định kỳ 3-5 giây cập nhật tọa độ, dung lượng pin và nhận phản hồi lệnh phần cứng (ACK/NACK).
- Bộ lập lịch (`JobScheduler`) kiểm tra và hủy các đơn đặt xe/chỗ đỗ quá hạn 15 phút.

---

## 4. Danh Mục Use Case (UC-00 đến UC-15)

Dự án bao gồm 16 Use Cases tiêu chuẩn được phân chia theo 3 nhóm tác vụ chính:

| Phân hệ | Mã UC | Tên Use Case | Mô tả tóm tắt |
|---|---|---|---|
| **Hệ thống & Auth** | **UC-00** | Đăng ký & Đăng nhập Hệ thống | Xác thực tài khoản Sinh viên/Operator, cấp token phiên 30 phút, kiểm soát phân quyền (RBAC). |
| **Sinh viên (Student)** | **UC-01** | Tìm kiếm Trạm & Xe khả dụng | Tìm hub theo tọa độ/bán kính, lọc xe `AVAILABLE`, pin $\ge 20\%$, telemetry `FRESH`. |
| | **UC-02** | Xem Chi tiết Xe & Trạm | Xem chi tiết thông số xe, pin, vị trí và chất lượng dữ liệu đo xa. |
| | **UC-03** | Đặt trước Xe chia sẻ | Giữ chỗ xe tối đa 15 phút, sinh mã nhận xe 6 chữ số / QR code, kiểm soát đồng thời. |
| | **UC-04** | Đặt trước Chỗ đỗ tại Hub đích | Đặt trước ô đỗ xe trong khoảng thời gian xác định, giữ chỗ 15 phút. |
| | **UC-05** | Mở khóa & Nhận xe | Quét QR / nhập mã lấy xe, gửi lệnh IoT mở khóa, xác nhận ACK thành công và bắt đầu `Ride`. |
| | **UC-06** | Trả xe & Kết thúc chuyến đi | Đối chiếu chuyến, xe và ô đỗ, gửi lệnh khóa IoT. Khi ACK khớp và có pin cuối, ưu tiên bảo trì nếu báo sự cố, cập nhật pin: $<20\%$ thành `LOW_BATTERY`, $\ge 20\%$ thành `AVAILABLE`. |
| | **UC-07** | Đăng ký Sạc thông minh cá nhân | Đặt lịch cổng sạc cho xe cá nhân, tính toán thời lượng và công suất giới hạn của trạm. |
| **Vận hành (Operator)** | **UC-08** | Giám sát Toàn diện Mạng lưới | Hiển thị bản đồ, chỉ số hoạt động các hub, xe, cổng sạc, chất lượng dữ liệu đo xa. |
| | **UC-09** | Can thiệp Điều khiển Từ xa | Operator cưỡng bức khóa/mở khóa xe, đổi trạng thái bảo trì (`MAINTENANCE`), ghi vết audit log. |
| | **UC-10** | Cảnh báo Quá tải & Thiếu hụt | Phát hiện hub quá tải ($\ge 85\%$ chỗ đỗ) hoặc thiếu xe ($\le 2$ xe), phát sinh cảnh báo theo cấp độ. |
| | **UC-11** | Điều phối Luân chuyển Xe | Tạo lệnh chuyển xe từ hub này sang hub khác, quản lý trạng thái `IN_TRANSIT`, xác nhận hoàn tất. |
| | **UC-12** | Quản lý & Xử lý Sự cố | Tiếp nhận báo cáo hỏng hóc, ngắt thiết bị khỏi mạng lưới, chuyển trạng thái sau khi bảo dưỡng xong. |
| | **UC-13** | Bộ Lập lịch Sạc Thông minh | Tối ưu hóa phân bổ sạc trạm, ưu tiên sạc xe có pin $<20\%$ trước để nhanh chóng đưa vào phục vụ. |
| | **UC-14** | Mô phỏng Kịch bản Giả lập | Chạy mô phỏng What-if trong môi trường bộ nhớ cách ly, không ảnh hưởng dữ liệu thực và không gửi lệnh IoT thực tế. |
| **Cá nhân hóa** | **UC-15** | Tùy chỉnh Hiển thị Giao diện | Lưu cấu hình hiển thị, giao diện sáng/tối, ngôn ngữ (chủ yếu xử lý phía client). |

---

## 5. Thuật Ngữ Domain (Glossary)

- **Mobility Hub (Hub):** Điểm dừng chân đa phương thức tại khuôn viên VNU-HCM, bao gồm các vị trí đỗ xe (`ParkingSpace`), cổng sạc xe điện (`ChargingPort`), và bộ thu phát viễn thông.
- **Shared Vehicle (Xe chia sẻ):** Xe đạp điện / xe máy điện thuộc quyền sở hữu của mạng lưới (`ownership = SHARED`), cho phép sinh viên thuê theo lượt.
- **Private Vehicle (Xe cá nhân):** Xe điện cá nhân của sinh viên (`ownership = PRIVATE`), sinh viên có thể đăng ký sử dụng dịch vụ sạc thông minh tại Hub.
- **SoC (State of Charge):** Tỷ lệ phần trăm dung lượng pin hiện tại của phương tiện (từ `0.0` đến `100.0%`).
- **Rentable Battery Threshold:** Ngưỡng pin tối thiểu để xe được phép đặt trước hoặc cho thuê: **$\ge 20.0\%$**.
- **Hold Timeout (Thời gian giữ chỗ):** Khoảng thời gian bảo lưu xe hoặc chỗ đỗ sau khi đặt trước thành công: **15 phút**.
- **Pickup Code:** Mã số xác thực gồm 6 chữ số ngẫu nhiên cấp cho sinh viên khi đặt trước xe để mở khóa thủ công hoặc qua mã QR. Backend chỉ lưu bản băm (hash).
- **Telemetry (Dữ liệu đo xa):** Dữ liệu trạng thái truyền từ xe về hệ thống: tọa độ GPS (`latitude`, `longitude`), pin (`batteryPct`), trạng thái khóa (`lockStatus`), và thời điểm ghi nhận (`observedAt`).
- **Data Quality (Chất lượng dữ liệu):**
  - `FRESH`: Dữ liệu nhận trong vòng $\le 10$ phút gần nhất.
  - `STALE`: Dữ liệu có tuổi đời $> 10$ phút.
  - `MISSING`: Không có bản tin đo xa nào hoặc mất kết nối hoàn toàn.
- **Hub Overload:** Tình trạng trạm có tỷ lệ chiếm dụng chỗ đỗ $\ge 85\%$ hoặc số xe sẵn sàng phục vụ $\le 2$ xe.
- **Dispatch Order:** Lệnh luân chuyển số lượng xe nhất định giữa trạm nguồn (thừa xe) và trạm đích (thiếu xe).
- **What-if Simulation:** Trình mô phỏng kịch bản giả lập nhu cầu hoặc sự cố để thử nghiệm các phương án điều phối trên bản sao dữ liệu snapshot.

---

## 6. Phạm Vi Dự Án (Scope Boundaries)

### 6.1. Trong phạm vi (In Scope)
- Hệ thống backend nguyên khối (Monolith) phát triển bằng **Java 21 LTS** và **Spring Boot 4.1.2**.
- Cơ chế quản lý dữ liệu In-Memory thread-safe bằng `ConcurrentHashMap` kết hợp lưu trữ snapshot an toàn xuống file `data/data.json` thông qua `StateCoordinator`.
- Kiểm soát đồng thời bằng phiên bản (Optimistic Concurrency Control với trường `version`).
- Trình giả lập IoT chạy trực tiếp trong tiến trình backend (In-Process Simulator), mô phỏng chu kỳ phát dữ liệu 3-5 giây và phản hồi ACK/NACK có thể cấu hình độ trễ.
- Triển khai đầy đủ REST/JSON API cho cả 16 Use Cases.
- Hỗ trợ tài liệu hợp đồng API (`03-api-contract.md`) và kế hoạch kiểm thử tự động toàn diện.

### 6.2. Ngoài phạm vi (Out of Scope)
- Thiết bị phần cứng IoT vật lý thực tế (toàn bộ được trừu tượng hóa bằng In-Process Simulator).
- Cổng thanh toán tài chính/ngân hàng thực tế (sử dụng mô hình campus credit nội bộ hoặc chi phí mặc định bằng 0 trong phạm vi học thuật).
- Kiến trúc Microservices phân tán phức tạp hoặc kiến trúc đa cụm cơ sở dữ liệu (tập trung tối ưu vào Single-instance Monolith ổn định, dễ chấm điểm và triển khai).
- Tích hợp bản đồ thương mại tính phí (Google Maps API tính phí - sử dụng bản đồ mã nguồn mở OpenStreetMap hoặc tọa độ giả định nội khu VNU-HCM).

---

## 7. Các Quyết Định Kiến Trúc Đã Khóa (Locked Decisions)

1. **Ngôn ngữ & Runtime:** Java 21, Spring Boot 4.1.2, build bằng Maven Wrapper (`.\mvnw.cmd` / `./mvnw`).
2. **Cơ chế lưu trữ:** RAM là Single Source of Truth khi runtime; mọi cập nhật trạng thái hợp lệ phải được commit nguyên tử qua `StateCoordinator` và ghi ra đĩa `data/data.json` theo cơ chế temp-file rename an toàn. Không cài đặt RDBMS ngoài.
3. **Cơ chế Xác thực:** Token phiên làm việc (Session Token) sinh ngẫu nhiên, lưu trữ trong RAM, hết hạn sau 30 phút. Truyền qua Header `Authorization: Bearer <sessionToken>`.
4. **Cơ chế An toàn dữ liệu:** Thiếu dữ liệu đo xa (`MISSING`/`STALE`) là điều kiện bất thường; xe bị thiếu telemetry không được xuất hiện trong kết quả tìm kiếm sẵn sàng cho thuê của sinh viên.
5. **Quy tắc điều phối đồng thời:** Hai người dùng thao tác trên cùng một xe/chỗ đỗ tại cùng một thời điểm: request đầu tiên thành công (201 Created), request thứ hai thất bại vì xung đột phiên bản (409 Conflict).
