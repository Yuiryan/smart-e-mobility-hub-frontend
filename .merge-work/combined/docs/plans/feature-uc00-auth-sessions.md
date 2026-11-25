# UC-00: Đồng bộ tài khoản, phiên đăng nhập và phân quyền

- Ngày lập: 24/11/2026, Asia/Saigon.
- Branch: feat/system-auth-iot.
- Phạm vi plan này: đồng bộ phần UC-00 đã có, sau đó hoàn thiện authentication/logout/authorization. Recovery và đổi mật khẩu vẫn thuộc M3 nhưng sẽ có plan tiếp theo, không bị loại khỏi yêu cầu.

## Current state
- Đồng bộ response register gồm accountId, studentId, email, role, status, createdAt.
- Đồng bộ response login gồm sessionToken, tokenType, expiresAt và user(accountId, studentId, email, role).
- Dữ liệu nhận dạng trong response lấy từ Account của backend.
- Có regression test HTTP cho luồng register → login và các field response.
### Source hiện có

- Account, AccountRole, AccountStatus; Account có id/fullName/studentId/email/passwordHash/role/status/createdAt.
- POST /api/auth/register: fullName/studentId/email/password, trả accountId/role/createdAt.
- POST /api/auth/login: email/password/role, trả opaque Bearer token, expiresAt, user(accountId/role).
- BCrypt cho password; min length 8 trong DTO; giới hạn 72 UTF-8 byte trong Service.
- SessionTokenService sinh 32 byte ngẫu nhiên; Base64 URL không padding; hash SHA-256 được lưu trong Session.id.
- Login thay phiên cũ trong cùng StateCoordinator.commit. AccountDataStore có thao tác đọc/lưu/xóa Account/Session cần thiết.
- SessionService.authenticate kiểm tra token, hạn và Account hiện hành; gia hạn idle expiry bằng cùng coordinator. Chưa được gọi bởi HTTP filter.
- Handler cho validation/register/login/session; error details đang dùng reason; code lỗi còn theo implementation cũ.
- Chưa có filter/security chain, logout, me, role route/ownership/CORS; chưa có recovery/change password.

### Kiểm tra đã có và các lỗ hổng quy trình

- Rà soát trước đã biên dịch source mới và thực hiện 52 kiểm tra tạm bằng dữ liệu/cổng cô lập: các luồng đăng ký/login/session/persistence chính hoạt động.
- Phát hiện thực tế: role số 0 được chấp nhận như ordinal; 404/500 chưa theo common envelope.
- Không có src/test Java được lưu trong repo. Kiểm tra tạm/ảnh không thay thế regression suite.
- mvnw test và clean package -DskipTests=false đã thành công trên bản sao cô lập, nhưng báo No tests to run. Chưa đạt DoD.
- Bộ docs mới đang untracked trong repo. Không dùng git add docs/ để vô tình stage mọi tài liệu chưa review.

### Căn cứ phải đọc

- AGENTS.md do người dùng cung cấp tại M1_branch/AGENTS.md.
- ../00-product-context.md, ../01-domain-rules.md, ../02-architecture.md, ../03-api-contract.md, ../04-data-schema.md, ../06-test-plan.md.
- ./feature-template.md; PDF A01_7.pdf phần UC-00 và PROJECT_INFO.md.
- Bộ Draft tại Thịnh dùng làm lịch sử quyết định/conflict; không tự coi nó giống docs/03-api-contract.md mới.

## Goal

1. Đồng bộ API/schema/error contract cho phần đăng ký/login/session đang có bằng các thay đổi được ghi trong plan và review.
2. Lưu regression tests thực sự trong src/test; bug fix có bằng chứng fail trước, pass sau.
3. Xác thực opaque Bearer từ HTTP, idle timeout 30 phút, logout và danh tính sau reload.
4. Role STUDENT/OPERATOR và quyền sở hữu được kiểm tra tại backend.
5. Mọi mutation Account/Session qua StateCoordinator.commit; lỗi không làm lộ password/token/hash hoặc stack trace.

## Non-goals

- Không triển khai IoT, scheduler, UC-13/UC-14 hoặc nghiệp vụ Student/Operator khác trong PR này.
- Không đổi sang JWT/cookie, không thêm refresh token hoặc đa thiết bị.
- Không chuyển DBMS, không đổi toàn bộ package hoặc version cơ chế store.
- Không tự hạ Spring Boot 4.1.2 xuống 3.3.x vì văn bản chưa đồng bộ.
- Không ghi raw token xuống JSON để khớp một bảng schema cũ.
- Không thêm API tạo Operator public, không hardcode credentials demo.
- Không mở rộng sang unique MSSV khi chưa có yêu cầu rõ.

## Design decisions

### D01 — Tài liệu và runtime

Giữ Java 21/Spring Boot 4.1.2/Jackson 3 theo pom đã build được. docs/00 và docs/02 đang ghi Boot 3.3.x: đề xuất cập nhật mô tả runtime trong cùng đợt đồng bộ docs, không đổi dependency lớn.

Giữ Controller -> Service -> Domain/Store, config ở common/config của repo. Không di chuyển package chỉ vì sơ đồ minh họa dùng bku/emobility.

### D02 — Request đăng ký: điểm cần review

**Đề xuất:** giữ fullName bắt buộc theo RegData của PDF và Account schema; bổ sung fullName vào request mẫu/schema tại docs/03. Lý do: nếu bỏ field khỏi request hiện tại thì không có nguồn họ tên để đáp ứng Account.fullName bắt buộc. Không tự gán tên từ email hoặc dữ liệu giả.

Trước khi review/cập nhật contract, giữ source hiện có; không âm thầm bỏ validation fullName. Không mô tả đề xuất này là quyết định đã được FE duyệt.

### D03 — Request login: điểm cần review

**Đề xuất:** giữ role bắt buộc như chữ ký login(email, pass, role) của PDF và luồng chọn vai trò, bổ sung field vào docs/03. Service so sánh role request với role Account; role trả về luôn lấy từ Account.

API docs mới chỉ minh họa email/password nên đây là thay đổi contract cần review. Nếu nhóm chọn public login không nhận role, phải sửa DTO/service/UI và method mapping có chủ đích; không tự fallback role tùy ý trong bước bug fix.

### D04 — Response công khai

Theo docs/03 mới:

- Register: accountId, studentId, email, role, status, createdAt.
- Login: sessionToken, tokenType, expiresAt, user(accountId, studentId, email, role).
- Không trả passwordHash hoặc token hash. DTO toString che credentials/token.
- Status đăng ký ACTIVE khớp test plan mới; không tự thêm activation workflow trong PR này.
- GET /api/auth/me là endpoint bổ sung để FE phục hồi danh tính sau reload; cần ghi rõ trong docs/03 trước khi thêm Controller.

### D05 — Error envelope và mã lỗi

- Đổi details[].reason thành details[].issue theo docs/03 mới; giữ field/code/message/requestId/timestamp.
- Sai email/password/role -> 401 AUTH_INVALID_CREDENTIALS.
- Phiên thực sự hết hạn -> 401 AUTH_TOKEN_EXPIRED. Cần reason riêng trong SessionException để không gộp hết hạn với mọi token lỗi.
- Account không ACTIVE hoặc role không đủ -> 403 ACCESS_DENIED.
- Stale version -> 409 OPTIMISTIC_LOCK_CONFLICT.
- Email đã tồn tại -> 409 RESOURCE_ALREADY_EXISTS, theo TC-UC00-02; bổ sung mã này vào bảng error codes docs/03 vì bảng hiện chưa liệt kê.
- 404 -> RESOURCE_NOT_FOUND.
- Thiếu/sai/không tìm thấy token -> đề xuất 401 AUTH_INVALID_TOKEN; 500 ngoài dự kiến -> INTERNAL_ERROR. Hai mã này cần ghi vào contract, không tuyên bố đã có trong bảng hiện tại.
- Tách duplicate registration khỏi version conflict bằng reason hoặc exception rõ ràng, không dựa vào parse message.
- Các framework status 405/406/415 phải giữ đúng status; không để generic handler biến thành 500.
- Lỗi HTTP 405 dùng METHOD_NOT_ALLOWED và giữ header Allow từ Spring.
- Lỗi HTTP 415 dùng UNSUPPORTED_MEDIA_TYPE và giữ các header hỗ trợ media type từ Spring.
- Lỗi HTTP 404 của đường dẫn không tồn tại dùng RESOURCE_NOT_FOUND.
- INVALID_SESSION được ánh xạ thành AUTH_INVALID_TOKEN.
- Handler tổng quát dùng INTERNAL_ERROR cho lỗi server ngoài dự kiến.
- Exception triển khai ErrorResponse của Spring được giữ HTTP status/header. Lỗi 4xx chưa có mã riêng dùng HTTP_ERROR.
- Exception phía server được log để điều tra; response 500 có message cố định. Không trả parser message/stack/rejected password.
- Khi thêm filter, lỗi ngoài DispatcherServlet cũng dùng cùng error factory/writer, không chỉ @RestControllerAdvice.
- requestId hiện chỉ UUID trên response. Nếu bổ sung correlation thì dùng request ID do server sinh cho một request và chia sẻ giữa filter/advice/log.

### D06 — Enum JSON

- Từ chối enum dạng số cho toàn ứng dụng. Repo Jackson 3 dùng spring.jackson.datatype.enum.fail-on-numbers-for-enums=true.
- Test 0/1, chuỗi số, mã không tồn tại, khác hoa/thường và thiếu/null. Chỉ nhận mã chuỗi trong contract, không ordinal.
- Test bug đầu tiên dùng email không tồn tại và role số: code cũ trả 401 sau deserialize, code đúng phải từ chối 400 trước Service. Không cần tạo account/seed để tái hiện bug này.

### D07 — Account/Session schema

**Đề xuất mapping cần được ghi đồng thời vào docs/04:**

- Account.id vẫn đáp ứng IdentifiedEntity; JSON persistence dùng accountId hoặc mapping rõ, hỗ trợ tên id cũ bằng alias.
- Thêm Account.updatedAt. Account mới: createdAt và updatedAt cùng một instant; dữ liệu cũ thiếu updatedAt lấy createdAt khi hợp lệ theo migration đã mô tả.
- Session.id giữ token hash, không chứa raw token. docs/04 hiện gọi khóa chính sessionToken và có role cache: đề xuất mô tả lại là tokenHash/định danh nội bộ, Account là nguồn quyền hiện hành.
- Nếu giữ role snapshot trong Session để phục vụ consumer, không dùng nó thay kiểm tra Account hiện hành; thay đổi role/status phải có hiệu lực trên request kế tiếp. Không thêm field cache khi chưa cần chỉ để tránh lookup.
- Token thô chỉ được trả khi login, qua HTTPS khi triển khai; request sau gửi Authorization, không truyền qua JSON/query.
- Snapshot cũ có id hash đọc được qua alias. Không cố đoán raw token hay hash từ một chuỗi tùy ý; nếu có định dạng cũ khác phải mô tả migration hoặc vô hiệu hóa session cũ có chủ đích.
- Account thiếu role/status bắt buộc không được mặc định thành ACTIVE/OPERATOR. Chưa bổ sung quy tắc ánh xạ active boolean cũ sang ba trạng thái nếu không có căn cứ.
- Mọi thay đổi tên/field cần test load snapshot trước và sau. Không mở rộng toàn StoreSnapshot sang tất cả module ngoài phạm vi auth trong PR này.

### D08 — Time và tính đúng đồng thời

- Dùng một Clock bean để kiểm thử thời gian xác định. Refactor AuthService/SessionService dùng clock.instant thay Instant.now trong scope auth.
- Timeout cấu hình PT30M. Phiên hợp lệ khi now < expiresAt, hết hạn đúng tại now == expiresAt.
- Request đã xác thực gia hạn now + timeout; không khôi phục phiên đã hết hạn. createdAt giữ nguyên.
- Login mới thay phiên cũ, read/check/mutate trong cùng commit và có stale-version handling; không tự lặp lại request HTTP ghi nghiệp vụ.
- Gia hạn hiện ghi toàn snapshot và tăng version toàn cục. Giữ hành vi để bảo đảm persistence trước; đo/ghi nhận conflict. Không tự chuyển sang ghi RAM bỏ coordinator vì tối ưu.
- Thêm test hai request xác thực đồng thời, login đồng thời với renewal, và phiên cũ bị thu hồi; không kết luận mọi request phải thành công nếu policy version cho phép 409.

### D09 — Rule mật khẩu và dependency

- BCrypt tiếp tục dùng spring-security-crypto hiện có; ghi lý do vào plan: PasswordEncoder chuyên dụng cho encode/matches, tránh tự dựng thuật toán password hashing.
- Tập trung min password length 8 và BCrypt max UTF-8 bytes 72 vào PasswordPolicy/domain constant thích hợp. 72 là giới hạn kỹ thuật, không tăng cấu hình vượt khả năng BCrypt.
- Chỉ kiểm tra độ mạnh password khi đăng ký/đặt mật khẩu mới; login chỉ kiểm tra dữ liệu và khả năng xử lý an toàn.
- Token entropy/encoding constants tập trung tại SessionTokenService; không để regex chiều dài bị lệch khi đổi generator.
- Spring Security starter chỉ thêm ở bước filter/role sau khi ghi rõ dependency và test trong plan; dùng framework cho filter chain, không tự viết toàn bộ HTTP security bằng interceptor.

### D10 — HTTP authorization và ownership

- Public: POST register/login; public Student reads theo contract được tích hợp sau.
- Authenticated: POST logout, GET me; GET operation phải thêm owner/operator guard khi nghiệp vụ operation hiện hữu.
- STUDENT cho Student mutations; OPERATOR cho /api/operator/**. Mặc định từ chối endpoint chưa được cho phép rõ.
- Danh tính lấy từ token đã xác thực. Resource owner phải đọc từ Store qua Service, không tin accountId/studentId client gửi như chứng cứ quyền.
- AuthService và SessionService cùng thuộc module auth; cập nhật ownership docs/02 để cho phép SessionService quản lý phiên qua coordinator, không ép mọi logic vào một class lớn.
- CORS dùng allowlist cấu hình theo FE origin thực tế, không wildcard tùy tiện. Không coi PowerShell là browser CORS test.
- Nếu tắt CSRF ở bước security, plan/config phải giải thích auth chỉ qua Authorization và không dùng cookie tự gửi; đánh giá lại nếu cơ chế này thay đổi.

## Files affected

Path Java rút gọn dưới src/main/java/vn/edu/bku/smart_emobility_backend/.

### Tài liệu

- docs/plans/feature-uc00-auth-sessions.md (file này).
- docs/00-product-context.md, docs/02-architecture.md: runtime, module ownership, không sửa nghiệp vụ Student ngoài scope.
- docs/03-api-contract.md và docs/04-data-schema.md: cập nhật cùng đợt với thay đổi API/schema, có mapping/migration.
- docs/06-test-plan.md: ghi coverage và mã lỗi UC-00 thống nhất.

### Source hiện có

- dto/RegisterStudentRequestDTO.java, RegisterStudentResponseDTO.java, LoginRequestDTO.java, LoginResponseDTO.java, ApiErrorResponse.java.
- controller/AccountController.java.
- service/AuthService.java, service/SessionService.java.
- common/domain/Account.java, Session.java.
- common/security/SessionTokenService.java.
- common/exception/RegistrationConflictException.java, LoginException.java, SessionException.java.
- common/web/GlobalExceptionHandler.java.
- common/store/AccountDataStore.java, InMemoryAccountDataStore.java, StoreSnapshot.java, JsonStore.java: chỉ sửa nếu mapping/migration auth thực sự yêu cầu.
- src/main/resources/application.properties, pom.xml.

### File mới dự kiến

- common/config/TimeConfiguration.java, SecurityConfiguration.java.
- common/domain/PasswordPolicy.java.
- common/security/CurrentAccount.java, BearerSessionFilter.java, ResourceAuthorization.java.
- common/web/ApiErrors.java, common/security/SecurityErrorWriter.java.
- DTO identity riêng nếu CurrentAccount không phù hợp làm response.

### Tests trong src/test/java/vn/edu/bku/smart_emobility_backend/

- AuthApiRegressionTest.java: enum/error schema/HTTP contract.
- service/AuthServiceTest.java: credentials/status/session replacement/concurrency/failed persistence.
- service/SessionServiceTest.java: expiry/renewal/status/missing account/concurrency.
- common/security/SessionTokenServiceTest.java: format/hash/non-disclosure.
- common/store/AuthSnapshotCompatibilityTest.java: snapshot aliases, updatedAt, token hash.
- AuthSecurityIntegrationTest.java: filter chain, role, ownership, logout, CORS, no-cookie auth.

Nếu cần thêm file ngoài danh sách, cập nhật plan trước. Các tên file mới có thể chỉnh trong plan trước khi nhập code, không tự phát sinh ngoài scope.

## Implementation

Mỗi bước thực hiện theo hướng dẫn code tay. Review diff trước commit; không stage toàn docs/ nếu chưa kiểm kê các file untracked.

| Bước | Nội dung | Mốc commit đề xuất |
|---|---|---|
| 0 | Review đề xuất D02/D03/D07; cập nhật docs chính có chủ đích trước khi thay API/schema | docs(auth): align UC-00 contract and schema decisions |
| 1 | Thêm regression test enum số, chạy và ghi nhận fail trước fix | test(auth): reproduce numeric enum input bug |
| 2 | Thêm config Jackson 3; chạy lại test pass, code chuỗi hợp lệ không bị hỏng | fix(api): reject numeric enum values |
| 3 | Regression tests issue field, error codes, 404/500/405/415; ghi nhận fail | test(api): cover common error contract |
| 4 | Sửa error DTO/reason phân loại exception/handler và docs tương ứng | fix(api): align auth error codes and envelopes |
| 5 | Test response register/login; bổ sung fields, schema mapping/updatedAt và compatibility đã duyệt | feat(auth): align account responses and snapshot schema |
| 6 | Clock/PasswordPolicy, unit tests password/expiry/boundary | refactor(auth): centralize policy and inject clock |
| 7 | Hoàn thiện Session tests và logout Service; mutation vẫn qua coordinator | feat(auth): revoke bearer sessions on logout |
| 8 | Thêm starter/security filter/error writer/current identity, ghi rõ route allowlist | feat(auth): authenticate bearer sessions on requests |
| 9 | Nối logout/me; role/ownership guard và test endpoint fixtures chỉ trong test | feat(auth): enforce roles and resource ownership |
| 10 | CORS/browser integration, test đầy đủ, đồng bộ docs và báo cáo coverage | test(auth): verify security integration and persistence |

Không tuyên bố xong authentication chỉ vì DTO hoặc SessionService compile. Không tuyên bố xong authorization trước khi HTTP filter/role/ownership được kiểm tra.

## Acceptance criteria

- [ ] Plan và các thay đổi public schema được review; D02/D03/D07 có kết quả ghi rõ, không suy ra phê duyệt từ việc tạo file này.
- [ ] API/code/mock dùng cùng fields/error codes với docs/03; schema auth có migration tương ứng trong docs/04.
- [ ] Numeric enum bị từ chối 400; valid string enum vẫn hoạt động; invalid/missing/null role theo contract được chọn.
- [ ] Register 201, duplicate email 409 RESOURCE_ALREADY_EXISTS, không lộ password/hash.
- [ ] Login đúng 200, sai thông tin 401 AUTH_INVALID_CREDENTIALS; Account không ACTIVE bị từ chối.
- [ ] Token ngẫu nhiên, raw token không nằm trong snapshot/log/toString; chỉ token hash được lưu.
- [ ] Một phiên hoạt động/tài khoản; login mới thay phiên cũ; logout thu hồi trước khi trả 204.
- [ ] Idle expiry đúng mốc 30 phút, không revive expired token; account role/status hiện hành được thực thi.
- [ ] 401/403/404/409/500 có đúng status/envelope; framework 405/415 không bị biến thành 500.
- [ ] Student không gọi được Operator API; chủ sở hữu không lấy từ input client; fixture kiểm tra ownership chỉ nằm trong test.
- [ ] Mọi mutation qua coordinator; lỗi ghi không báo thành công, rollback và concurrent cases có test.
- [ ] Snapshot auth cũ và mới đọc đúng; runtime/docs version không mâu thuẫn.
- [ ] Có automated tests trong src/test, không phải No tests to run; lỗi có fail-before/pass-after.
- [ ] mvnw test và clean package -DskipTests=false thành công; kiểm tra không xóa dữ liệu manual-auth của người dùng.

## Verification

### Test cases tối thiểu

| Nhóm | Ca cần có |
|---|---|
| Enum | role số 0/1, chuỗi số, STUDENT/OPERATOR, ADMIN, lowercase, null/missing |
| Register | hợp lệ, thiếu fullName theo contract sau review, email sai/trùng/khác hoa thường, đồng thời cùng email |
| Password | min-1/min/min+1; ASCII 71/72/73 byte; Unicode sát mốc; không đưa password vào response |
| Login | đúng/sai/không tồn tại/sai role nếu giữ role; ACTIVE/SUSPENDED/UNVERIFIED; thay phiên và login race |
| Session | null/malformed/unknown; expiresAt-1ns, bằng expiresAt, +1ns bằng Clock kiểm soát; renewal giữ createdAt; Account mất/đổi quyền |
| Persistence | token hash, snapshot alias, updatedAt cũ/mới, restart, lỗi save rollback; không đụng data thật |
| Errors | validation issue/code/meta, JSON sai, 404, 405, 415, storage 500 và filter 401/403 |
| Security | anonymous, đúng/sai role, owner/other/operator, logout token cũ, identity sau reload; CORS allowed/disallowed |

### Lệnh dự kiến

```powershell
.\mvnw.cmd test -Dtest=AuthApiRegressionTest
.\mvnw.cmd test
.\mvnw.cmd clean package -DskipTests=false
```

Giai đoạn red phải ghi rõ test fail vì bug, không phải lỗi import/context/dependency. Giai đoạn green phải giữ test đã tái hiện trong repo. Lệnh test thành công nhưng No tests to run không đáp ứng plan.

Dữ liệu test dùng TempDir hoặc path riêng, cổng random. Nếu clean package có thể xóa target/manual-auth/data.json, chạy trên bản sao tạm hoặc sao lưu dữ liệu manual theo chỉ định rõ; không xóa dữ liệu người dùng để chạy kiểm tra.

### Bằng chứng trong task lập plan

Chỉ tạo tài liệu này. Không thay source/test/contract/pom và không tạo commit. Test và package thành công nhưng không có test là bằng chứng của đợt review trước, chưa phải verification của implementation theo plan này.

## Kiểm chứng trạng thái auth trước commit gộp 2–5

- File bổ sung: src/test/java/vn/edu/bku/smart_emobility_backend/AuthStateConsistencyTest.java.
- Dùng InMemoryDataStore, StateCoordinator, JsonStore thật với file trong @TempDir.
- Kiểm tra login thay phiên cũ và snapshot chỉ lưu hash của token.
- Giả lập JsonStore.save thất bại để kiểm tra rollback RAM/version và giữ snapshot file trước đó.
- Đồng bộ hai login sau khi kiểm tra mật khẩu để buộc cùng tranh chấp version, kiểm tra một phiên thắng và một lỗi STATE_CONFLICT.
- Kiểm tra hai commit cùng expectedVersion chỉ có một giao dịch thành công.
- Đồng bộ ví dụ request với fullName bắt buộc khi register và role bắt buộc khi login theo lựa chọn hiện thực trong plan. Đây là cập nhật contract của đợt này, chưa có xác nhận tích hợp FE.
- Kiểm tra raw sessionToken chỉ có trong response, tokenHash thuộc schema Session.
- Test và build do người dùng chạy. Các acceptance criteria chỉ đánh dấu khi có kết quả thực tế.
### Checkpoint commit gộp 2–5, ngày 24/11/2026

Đã kiểm tra báo cáo Surefire của năm lớp test, tổng 32 lượt chạy, 0 failures và 0 errors.
Phạm vi commit gồm error contract, response register/login, schema auth và kiểm chứng vòng đời/nhất quán session.
Các tiêu chí HTTP security/logout/ownership/CORS trong plan vẫn thuộc các bước tiếp theo.
### Kết quả xác minh cuối trước commit

- Bản sao nguồn riêng tại thư mục tạm, không sao chép target/data của repo.
- mvnw.cmd -o test: BUILD SUCCESS, 32 tests, 0 failures, 0 errors.
- mvnw.cmd -o clean package -DskipTests=false: BUILD SUCCESS, 32 tests, JAR đóng gói thành công.
- Log tại C:/Users/minhq/AppData/Local/Temp/m3-precommit-3b9e50c03edc437a86359d45c8950a34/test.log và package.log.
- git diff --check đã qua.
- Chưa xác nhận tích hợp frontend với request fullName/role và các mã lỗi bổ sung.
## Nhóm tiếp theo sau commit 3af4263

Nhóm HTTP authentication/logout/role/CORS thực hiện theo feature-uc00-http-security.md.
Plan đó ghi nhận me DTO, dependency Spring Security, allowlist CORS và ownership của các endpoint hiện có.
