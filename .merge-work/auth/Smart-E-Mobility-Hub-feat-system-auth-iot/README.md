# Smart E-Mobility Hub — Backend

Backend của dự án Smart E-Mobility Hub, được xây dựng bằng Java 21 và Spring Boot. Repo hiện có bộ khung khởi động và phần nền dùng chung để các thành viên phát triển API, nghiệp vụ theo từng nhánh.

## Yêu cầu

- JDK 21
- Git

Repo đã có Maven Wrapper nên không cần cài Maven riêng.

## Chạy ứng dụng

Trên Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

Trên macOS/Linux:

```bash
./mvnw spring-boot:run
```

Ứng dụng mặc định chạy tại `http://localhost:8080`.

Trạng thái dữ liệu được lưu trong bộ nhớ khi chạy và ghi xuống `data/data.json` khi có commit qua `StateCoordinator`.

## Build

```powershell
.\mvnw.cmd clean package
```

Trên macOS/Linux, dùng `./mvnw clean package`.

## Làm việc nhóm

Tạo nhánh tính năng từ nhánh tích hợp mà nhóm đã thống nhất. Có thể dùng các tên nhánh sau:

- `feat/student-flow`
- `feat/operator-flow`
- `feat/system-auth-iot`

Đẩy thay đổi lên nhánh cá nhân và mở pull request về nhánh tích hợp. Trao đổi trước khi sửa các phần dùng chung như domain, lưu trữ và cấu hình; không commit trực tiếp lên nhánh tích hợp.

## Phần nền dùng chung

```text
common/domain/  Entity và enum dùng chung
common/store/   In-memory store, JSON persistence, versioned commit
```

`StateCoordinator.commit(expectedVersion, change)` trả về `false` nếu version đã thay đổi; gọi commit để thay đổi trạng thái và lưu dữ liệu qua store chung. Chưa có API nghiệp vụ trong repo ở giai đoạn này.
