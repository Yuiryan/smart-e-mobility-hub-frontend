# [ID]: [Feature Title]

## Current state
- Mô tả ngắn gọn trạng thái hiện tại của codebase.
- API nào đã có, component nào còn thiếu hoặc chưa xử lý.
- Vấn đề gì cần giải quyết hoặc mở rộng.

## Goal
- Kết quả mong muốn sau khi hoàn thành feature này.
- Hành vi mới của hệ thống đối với người dùng (Sinh viên hoặc Operator).

## Non-goals
- Những việc KHÔNG làm trong feature/PR này (để tránh phình to scope).
- Ví dụ: Không refactor các module không liên quan; không thêm dependency mới; không đổi schema chung nếu không cấp thiết.

## Design decisions
- Các quyết định kiến trúc, giải thuật hoặc kỹ thuật quan trọng cho feature này.
- Đâu là nguồn sự thật (Source of truth).
- Cơ chế kiểm soát đồng thời hoặc tính bất biến (Idempotency).
- Cách xử lý lỗi hoặc trường hợp dữ liệu rỗng/thiếu.

## Files affected
- `src/main/java/bku/emobility/...`
- `src/test/java/bku/emobility/...`
- `docs/03-api-contract.md` (nếu có thay đổi API)
- `docs/04-data-schema.md` (nếu có thay đổi schema)

## Implementation
1. Bước 1: Khởi tạo/cập nhật Domain Model hoặc Enum liên quan.
2. Bước 2: Thêm Service method xử lý logic nghiệp vụ và commit qua StateCoordinator.
3. Bước 3: Thêm Controller endpoint và DTOs tương ứng.
4. Bước 4: Xử lý Exception và map sang Error Envelope chuẩn.
5. Bước 5: Viết Unit Test và Integration Test kiểm chứng.

## Acceptance criteria
- Điều kiện 1: Hành vi chuẩn (Happy path) hoạt động chính xác.
- Điều kiện 2: Các ca biên (Boundary / Invalid inputs) trả về đúng mã lỗi quy định.
- Điều kiện 3: Kiểm soát đồng thời chuẩn (ví dụ: tranh chấp xe trả về 409 Conflict).
- Điều kiện 4: Toàn bộ test suite liên quan chạy PASS 100%.

## Verification
- `.\mvnw.cmd test -Dtest=YourTestClass`
- `.\mvnw.cmd clean package`

