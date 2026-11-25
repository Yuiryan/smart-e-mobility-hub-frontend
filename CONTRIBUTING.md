# Làm việc nhóm

1. Chủ repo thêm tài khoản thành viên qua GitHub Settings → Collaborators. Không chia sẻ token hoặc tài khoản.
2. Cập nhật `main`, tạo nhánh `feat/uc03-reservation`, `fix/uc06-return-state` hoặc `docs/api-contract`.
3. Một PR tập trung vào một UC/luồng; mô tả link Figma, API, ảnh, bước kiểm tra và phần chưa xong.
4. Chạy `npm.cmd run format`, rồi `npm.cmd run check` trước khi push.
5. Nhờ thành viên review và chỉ merge khi CI xanh. Không force-push main.

```powershell
git switch main
git pull --ff-only
git switch -c feat/uc03-reservation
# Sửa mã và kiểm tra
git add .
git commit -m "feat(uc03): add reservation form"
git push -u origin feat/uc03-reservation
```

Tên hàm/type/component bằng tiếng Anh; tài liệu giải thích tiếng Việt; text UI có vi/en. Không commit `.env.local`, secrets, dữ liệu cá nhân hay bản sao báo cáo. Cập nhật lockfile khi thêm package; tránh nhiều UI libraries làm lệch Figma.

## Thiết lập GitHub đề xuất cho chủ repo

Bật rule bảo vệ main (nếu gói GitHub hỗ trợ repo private), yêu cầu PR, một review và check `verify`. Đây là hướng dẫn, **chưa khẳng định branch protection đã được bật**. Có thể bật squash merge và xóa nhánh sau merge. Repo hiện không gán license công khai; nhóm thống nhất trước khi công khai/tái sử dụng.
