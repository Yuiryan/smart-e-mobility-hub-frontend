# UC-00 — Password management

## Current state
Đã có register/login, Bearer authentication, me/logout và 63 test.
Chưa có đổi hoặc khôi phục mật khẩu.

## Goal
Đổi mật khẩu khi đã đăng nhập và khôi phục mật khẩu qua email.
Hai phần được thực hiện theo từng nhóm trong cùng commit quản lý mật khẩu.

## Non-goals
IoT, Optimize và What-if được thực hiện sau nhóm này.

## Design decisions
- POST /api/auth/password/change yêu cầu Bearer hợp lệ.
- Body gồm currentPassword, newPassword, confirmPassword.
- Tài khoản đích lấy từ authenticated principal.
- Mật khẩu mới tối thiểu 8 ký tự, tối đa 72 byte UTF-8.
- Thành công trả 204, cập nhật updatedAt và thu hồi mọi session.
- Cập nhật Account và xóa session nằm trong cùng coordinator commit.
- Sai mật khẩu hiện tại trả 401 AUTH_INVALID_CREDENTIALS.
- Xác nhận sai hoặc mật khẩu mới không hợp lệ trả 400 VALIDATION_ERROR.
- Version conflict trả 409 OPTIMISTIC_LOCK_CONFLICT.
- Clock được inject vào AuthService.
- Thiết kế recovery/email sẽ được bổ sung trước khi code nhóm khôi phục.

## Files affected
- common/domain/PasswordPolicy.java.
- common/exception/InvalidPasswordInputException.java.
- dto/ChangePasswordRequestDTO.java, RegisterStudentRequestDTO.java.
- service/AuthService.java.
- controller/AccountController.java.
- common/config/SecurityConfiguration.java.
- common/web/GlobalExceptionHandler.java.
- AuthSecurityIntegrationTest.java, AuthStateConsistencyTest.java.
- docs/03-api-contract.md, docs/04-data-schema.md, docs/06-test-plan.md.

## Implementation
1. Tập trung quy tắc mật khẩu.
2. Thêm DTO và exception.
3. Thêm nghiệp vụ đổi mật khẩu qua coordinator.
4. Nối endpoint và security rule.
5. Kiểm thử HTTP và lỗi persistence.
6. Bổ sung nhóm recovery trước commit.

## Acceptance criteria
- Mật khẩu hiện tại đúng và xác nhận khớp mới được đổi.
- Đúng các biên độ dài và byte UTF-8.
- Đổi thành công thu hồi token cũ.
- Lỗi ghi và version conflict giữ mật khẩu trước đó.
- Các test hiện có tiếp tục pass.

## Verification
- Chạy toàn bộ các lớp test auth.
- Chạy clean package trên bản sao tạm trước commit.
## Kết quả kiểm tra ngày 25/11/2026

- Trước sửa, test-compile tái hiện illegal start of type trong RegisterStudentRequestDTO.
- Khôi phục thành phần String password, dấu đóng record và import PasswordPolicy.
- Chuyển PasswordPolicy.java từ dto về common/domain để khớp package và plan.
- Bổ sung mô tả cập nhật passwordHash/updatedAt và thu hồi session vào data schema.
- Suite auth trên repo: 77 lượt chạy, 0 failures, 0 errors.
- Bản sao nguồn riêng: mvnw.cmd -o test và mvnw.cmd -o clean package -DskipTests=false đều BUILD SUCCESS, 77 lượt chạy.
- Log bản sao tại C:/Users/minhq/AppData/Local/Temp/m3-password-review-131df70280754e90b8eb2cc62b00a62a/.
- Nhóm đổi mật khẩu đã được kiểm chứng. Nhóm khôi phục mật khẩu tiếp tục được hướng dẫn code tay trước commit chung.

## Nhóm dữ liệu khôi phục mật khẩu

### Design decisions bổ sung
- RecoveryCode giữ tokenId, accountId, hashCode, expiresAt, isUsed và failedAttempts trong JSON.
- Java dùng codeHash để ánh xạ JSON hashCode, vì record component hashCode xung đột với Object.hashCode().
- RecoverySession giữ tokenHash, accountId, recoveryCodeId, createdAt và expiresAt.
- Service quản lý mã hiện hành, thời hạn, số lần thử và thu hồi grant trong cùng giao dịch.
- Snapshot cũ thiếu recoveryCodes/recoverySessions đọc thành danh sách rỗng.
- Snapshot và restore bao gồm cả hai bảng mới để coordinator có thể rollback chúng.
- Việc phát mã, xác minh, gửi email và đặt lại mật khẩu được nối ở nhóm nghiệp vụ tiếp theo.

### Files affected bổ sung
- common/domain/RecoveryCode.java, RecoverySession.java.
- common/store/AccountDataStore.java, InMemoryAccountDataStore.java.
- common/store/StoreSnapshot.java, InMemoryDataStore.java.
- RecoveryPersistenceTest.java do người dùng thêm và chạy theo mẫu hướng dẫn.
- docs/03-api-contract.md, docs/04-data-schema.md, docs/06-test-plan.md.

### Verification của nhóm dữ liệu
- Agent kiểm tra test-compile và diff.
- Người dùng thực hiện kiểm thử legacy snapshot, lưu/nạp/restore, rollback và thu hồi grant.
- Quy mô dự kiến sau khi thêm bốn test persistence: 81 lượt chạy.

## Nhóm yêu cầu khôi phục và gửi email

### Design decisions
- API public POST /api/auth/password/recovery/request nhận email hợp lệ.
- Email chưa đăng ký trả 202 với body rỗng. Email đã đăng ký nhận mã qua SMTP.
- Mã gồm 6 chữ số và hết hạn mặc định sau 10 phút; đây là giá trị triển khai, cấu hình thời hạn tại app.auth.recovery.code-timeout.
- Dùng SecureRandom sinh mã và BCrypt lưu hash.
- Code mới thay code và thu hồi recovery session cũ trong cùng coordinator commit.
- Gửi mail sau khi persist thành công. SMTP thất bại trả 503 RECOVERY_DELIVERY_FAILED; code đã persist vẫn tồn tại.
- Public response 202 không trả code/hash. Các lỗi SMTP và persistence có response lỗi riêng.
- Bổ sung spring-boot-starter-mail để dùng JavaMailSender chuẩn của Spring; domain/service phụ thuộc RecoveryMailSender interface.
- Mặc định phát triển SMTP localhost:1025, cấu hình qua SMTP_HOST/PORT/USERNAME/PASSWORD/AUTH/STARTTLS/FROM.
- AuthService kiểm tra recovery timeout phải dương.
- Register/login và recovery request bỏ qua Authorization cũ.
- Agent implement source/test và kiểm tra biên dịch. Người dùng chạy test và commit theo yêu cầu ngày 25/11/2026.

### Files affected
- pom.xml, src/main/resources/application.properties.
- common/domain/RecoveryPolicy.java.
- common/mail/RecoveryMailSender.java, SmtpRecoveryMailSender.java.
- common/exception/RecoveryDeliveryException.java.
- dto/RecoveryRequestDTO.java.
- service/AuthService.java, controller/AccountController.java.
- common/config/SecurityConfiguration.java, common/security/BearerSessionFilter.java.
- common/web/GlobalExceptionHandler.java.
- AuthSecurityIntegrationTest.java, AuthStateConsistencyTest.java.
- docs/03-api-contract.md, docs/04-data-schema.md, docs/06-test-plan.md.

### Acceptance criteria
- Code được persist trước khi mail sender được gọi.
- Hash khớp mã gửi đi, thời hạn đúng cấu hình.
- Yêu cầu mới thu hồi code/grant cũ.
- Mail lỗi, persistence lỗi và conflict được phân biệt.
- Input email sai và token cũ được xử lý đúng.
- Suite dự kiến 91 lượt chạy sau khi thêm 8 ca HTTP và 2 ca cấu hình timeout bằng 0/âm, chờ người dùng xác nhận.

### Verification
- Kiểm tra test-compile và git diff --check.
- Người dùng chạy toàn suite và kiểm tra kết nối SMTP theo môi trường.

### Trạng thái implement nhóm email ngày 25/11/2026
- Source và test đã được bổ sung.
- mvnw.cmd test-compile và mvnw.cmd -o test-compile đều BUILD SUCCESS.
- Chỉ thực hiện biên dịch, phần thực thi test và commit do người dùng phụ trách.
- Bộ test dự kiến 91 lượt chạy, gồm 8 ca HTTP recovery mới và 2 ca timeout không dương.
- SMTP phát triển mặc định localhost:1025; gửi email thực tế cần môi trường SMTP được cấu hình.

## Xác minh mã và đặt lại mật khẩu

POST recovery/verify nhận email và code, cấp recoveryToken một lần cho mỗi mã. Token hết hạn cùng mã, lưu hash và ràng buộc accountId với recoveryCodeId. Tối đa 5 lần sai trên mỗi lần phát hành. POST recovery/reset nhận recoveryToken, newPassword và confirmPassword. Khi thành công, đánh dấu mã đã dùng, thu hồi mọi session đăng nhập và recovery grant trong cùng commit. Đổi mật khẩu khi đăng nhập cũng thu hồi mã và grant cũ.

Files affected gồm AuthService, RecoveryPolicy, AccountController, SecurityConfiguration, BearerSessionFilter, GlobalExceptionHandler, InvalidRecoveryException, ba DTO RecoveryVerifyRequestDTO, RecoveryGrantDTO, RecoveryResetRequestDTO, AuthSecurityIntegrationTest, docs/03-api-contract.md, docs/04-data-schema.md và plan này.

Kiểm tra luồng HTTP đầy đủ, mã sai đến giới hạn, hết hạn, token sai và replay, lỗi validation, thay mật khẩu và thu hồi phiên. Người dùng chạy Maven test và package trước commit.
