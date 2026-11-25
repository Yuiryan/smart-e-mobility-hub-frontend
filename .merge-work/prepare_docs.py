from pathlib import Path
import shutil
target = Path(r'C:\Users\minhq\OneDrive\Desktop\HCMUT\3\CNPM\demo\Smart-E-Mobility-Hub')
stage = Path(__file__).parent / 'combined'
notes = {
'README.md': '''
## Tích hợp Student, Auth và IoT

Đã đối chiếu hai ZIP M1 Student và System Auth IoT, giữ module operator và cấu hình cục bộ. StudentAccess tra hash token do Auth cấp; route hủy đặt xe cho STUDENT; StudentIoTAdapter nối nhận/trả xe với journal IoT và lấy owner từ StudentOperation. Poll không gửi thêm lệnh, nghiệp vụ chỉ hoàn tất sau ACK.

Chạy từ thư mục gốc bằng `.\\mvnw.cmd spring-boot:run`. Login gửi email/password/role; đăng ký gửi fullName/studentId/email/password. Trả xe M1 yêu cầu issueReported (boolean). Khôi phục mật khẩu cần SMTP hoạt động. Dữ liệu và cấu hình đang dùng được giữ nguyên.

Xem [kế hoạch tích hợp](docs/plans/feature-merge-student-auth-iot.md). Hai ZIP không cung cấp đủ mọi UC vận hành; các phần operator hiện có được giữ lại.
''',
'docs/03-api-contract.md': '''
## Tích hợp Student/Auth/IoT

Giữ DTO auth: đăng ký fullName/studentId/email/password, login email/password/role. StudentAccess tra Bearer token mới bằng SHA-256. POST /api/reservations/{id}/cancel yêu cầu STUDENT và kiểm tra owner ở service. Nhận/trả xe dùng adapter với accountId từ claim bền vững; polling không phát lại lệnh. RETURN giữ yêu cầu issueReported boolean của M1. Đợt tích hợp không thêm endpoint.
''',
'docs/04-data-schema.md': '''
## Kho dữ liệu tích hợp Student/Auth/IoT

Giữ schema hợp nhất hiện tại và dữ liệu đang dùng. Session mới lưu tokenHash; StudentAccess băm raw Bearer token trước khi tra. StudentOperation tham chiếu studentId -> Student.accountId để gắn owner cho DeviceOperation. Các bản ghi dùng chung StateCoordinator/StoreSnapshot. Đợt tích hợp không ghi đè data.json hoặc data.example.json.
'''}
for name, note in notes.items():
    (stage / name).write_text((target / name).read_text(encoding='utf-8') + note, encoding='utf-8')
