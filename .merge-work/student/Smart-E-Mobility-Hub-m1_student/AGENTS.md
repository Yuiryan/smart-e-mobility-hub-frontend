# AGENTS.md — Mandatory Agent Operational Rules

Tài liệu này là **nguồn luật bắt buộc số 1** dành cho tất cả các AI Agent và Developer khi làm việc trên repository `smart-emobility-backend`. Agent **PHẢI ĐỌC FILE NÀY ĐẦU TIÊN** trước khi đọc source code hoặc thực hiện bất kỳ hành động nào.

---

## 1. Thứ Tự Đọc Tài Liệu Bắt Buộc Của Agent

Trước khi bắt tay vào giải quyết bất kỳ tác vụ nào, Agent phải đọc tài liệu theo đúng thứ tự phân tầng ngữ cảnh sau:

```
AGENTS.md (Luật vận hành)
  └── docs/00-product-context.md (Bối cảnh sản phẩm, use case, phạm vi, thuật ngữ)
        └── docs/01-domain-rules.md (Quy tắc nghiệp vụ, trạng thái, ngưỡng an toàn)
              └── docs/02-architecture.md (Kiến trúc, module, luồng dữ liệu, ownership)
                    └── docs/03-api-contract.md & docs/04-data-schema.md (Hợp đồng API & Schema dữ liệu)
                          └── docs/plans/feature-x.md (Kế hoạch cụ thể của PR / Feature)
                                └── Mã nguồn liên quan (src/main/java, src/test/java)
```

---

## 2. Các Luật Bắt Buộc (Core Rules)

### Quy tắc 1: Lập kế hoạch trước khi viết code (Plan before Code)
- Không bao giờ nhảy vào chỉnh sửa code ngay khi nhận yêu cầu nếu chưa có hoặc chưa trình bày Feature Plan rõ ràng.
- Với các tính năng mới hoặc thay đổi lớn, phải tạo hoặc cập nhật file plan trong `docs/plans/feature-<name>.md` tuân thủ đúng mẫu tại `docs/plans/feature-template.md`.
- Kế hoạch phải nêu rõ: `Current state`, `Goal`, `Non-goals`, `Design decisions`, `Files affected`, `Implementation steps`, `Acceptance criteria`, và `Verification`.

### Quy tắc 2: Không tự ý thay đổi API Contract hoặc Data Schema
- Không tự ý thêm, bớt, đổi tên trường, đổi HTTP Method, đổi endpoint path hoặc đổi kiểu dữ liệu của API/DTO nếu chưa được nêu rõ và phê duyệt trong Feature Plan.
- Khi có thay đổi API hoặc Schema, **phải cập nhật `docs/03-api-contract.md` và `docs/04-data-schema.md` cùng lúc trong chính PR đó**.

### Quy tắc 3: Không duplicate business rule giữa các tầng
- Mọi logic nghiệp vụ (business rule), ngưỡng kiểm tra (threshold), điều kiện hợp lệ (eligibility) PHẢI thuộc quyền sở hữu của backend domain/service.
- Không để business rule bị tản mạn hay lặp lại trên Controller, DTO hoặc giao diện Frontend. Controller chỉ nhận input, validate format cơ bản, gọi service và trả về kết quả.

### Quy tắc 4: Không hard-code domain rule trong code
- Các ngưỡng số học và hằng số quan trọng (ví dụ: ngưỡng pin tối thiểu `20%`, ngưỡng cảnh báo quá tải `85%`, thời hạn giữ chỗ `15 phút`, tuổi telemetry hợp lệ `10 phút`) phải được khai báo tập trung trong domain constant hoặc file cấu hình `application.properties`.
- Không nhúng số ma thuật (magic numbers) trực tiếp vào thân hàm xử lý.

### Quy tắc 5: Missing data không được tự hiểu là valid/normal/default
- Khi thiếu dữ liệu đo xa (telemetry) hoặc cảm biến bị mất kết nối, hệ thống **tuyệt đối không được gán giá trị mặc định xem như xe đang tốt / pin đầy**.
- Dữ liệu đo xa phải được phân định rõ ràng 3 trạng thái: `FRESH`, `STALE`, `MISSING`.
- Nếu telemetry của xe bị `STALE` hoặc `MISSING`, xe đó **không được phép xuất hiện trong danh sách khả dụng cho thuê** (UC-01) để đảm bảo an toàn cho người dùng.

### Quy tắc 6: Không nuốt lỗi, catch rỗng hoặc fallback im lặng
- Nghiêm cấm hoàn toàn các pattern: `catch (Exception e) {}`, catch rồi chỉ in log mà không rethrow hoặc không trả về mã lỗi thích hợp.
- Tất cả lỗi trả về client phải tuân theo cấu trúc lỗi chuẩn trong `docs/03-api-contract.md`:
  ```json
  {
    "error": {
      "code": "ERROR_CODE",
      "message": "Human-readable description",
      "details": [],
      "requestId": "uuid",
      "timestamp": "ISO-8601"
    }
  }
  ```

### Quy tắc 7: Không thêm dependency tùy tiện
- Dự án ưu tiên sử dụng các thư viện có sẵn trong JDK 21 và Spring Boot 3 starter (Jackson, Spring Web, JUnit 5).
- Không tự ý thêm thư viện ngoài (Maven dependencies trong `pom.xml`) nếu giải pháp hiện có hoàn toàn đáp ứng được. Mọi bổ sung dependency phải được biện minh rõ ràng trong plan.

### Quy tắc 8: Giữ đúng phạm vi thay đổi (No refactoring creep)
- Chỉ sửa đúng các file trong danh sách `Files affected` của plan.
- Không tranh thủ "tiện tay dọn dẹp" (drive-by refactoring) các class không liên quan, không đổi style code toàn project làm loãng PR và khó review.

### Quy tắc 9: Test tái hiện lỗi cho mọi bug fix
- Khi sửa lỗi (bug fix), agent phải viết một unit/integration test tái hiện chính xác lỗi đó (test fail trước khi sửa) rồi sau đó chứng minh test pass sau khi sửa.

### Quy tắc 10: Test boundary và invalid input cho mọi rule
- Với mỗi business rule mới hoặc sửa đổi, bắt buộc phải viết test cho các ca biên (boundary) và dữ liệu không hợp lệ (ví dụ: pin đúng 20.0%, 19.9%, 20.1%; bán kính tìm kiếm 0, -1, 50, 51; thời gian giữ chỗ đúng 15 phút, 15 phút 1 giây).

### Quy tắc 11: Tuân thủ cơ chế đồng thời và lưu trữ nguyên tử (Concurrency & StateCoordinator)
- Dự án sử dụng mô hình In-Memory `ConcurrentHashMap` kết hợp lưu trữ file `data/data.json`.
- Mọi thao tác thay đổi trạng thái thực thể **BẮT BUỘC PHẢI THÔNG QUA** `StateCoordinator.commit(expectedVersion, change)` với Optimistic Locking.
- Tuyệt đối không can thiệp trực tiếp sửa đổi map trong RAM mà bỏ qua versioning hoặc bỏ qua ghi nhận persistence.

### Quy tắc 12: Chạy kiểm thử toàn diện trước khi hoàn thành
- Trước khi báo cáo hoàn thành nhiệm vụ, Agent phải chạy thành công lệnh kiểm thử:
  ```powershell
  .\mvnw.cmd test
  ```
  và build đóng gói:
  ```powershell
  .\mvnw.cmd clean package -DskipTests=false
  ```
- Không được kết thúc lượt khi code chưa compile hoặc còn test bị fail.

### Quy tắc 13: Báo cáo minh bạch và rõ ràng
- Khi hoàn thành tác vụ, Agent phải tóm tắt:
  1. Danh sách các file đã tạo hoặc sửa đổi (kèm link định dạng `[filename](file:///path)`).
  2. Các ca kiểm thử (tests) đã chạy và kết quả.
  3. Các điểm còn tồn đọng hoặc cần thành viên khác review (nếu có).

---

## 3. Checklist Tự Kiểm Tra (Agent Self-Checklist)

| Mục kiểm tra | Trạng thái |
|---|:---:|
| Đã đọc `docs/00-product-context.md` và `docs/01-domain-rules.md` liên quan? | [ ] |
| Đã tạo/đọc Feature Plan trong `docs/plans/` trước khi code? | [ ] |
| Không tự ý đổi API DTO/Schema nếu không có trong plan? | [ ] |
| Kiểm tra pin < 20% thì xe thành `LOW_BATTERY`, không cho thuê? | [ ] |
| Telemetry cũ/thiếu không bị tự gán thành giá trị tốt? | [ ] |
| Thay đổi dữ liệu có gọi qua `StateCoordinator.commit(...)` với version? | [ ] |
| Có viết test boundary / negative cases không? | [ ] |
| Đã chạy `mvnw test` thành công không có lỗi? | [ ] |

