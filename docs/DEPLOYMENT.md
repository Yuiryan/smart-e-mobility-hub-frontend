# Build và chạy thử

Dùng Node.js 24 trên Windows: `npm.cmd ci`, `npm.cmd run check`, `npm.cmd run preview`. Build nằm trong `dist/`. Đây là web HTML/CSS/JavaScript thuần; Vite chỉ đóng gói tài nguyên. URL dùng hash nên không cần server rewrite.

Chưa deploy. Khi nhóm chọn hosting, cần HTTPS cho camera/GPS, xác định API URL và cookie/CORS/CSRF với backend. Không đặt secrets trong VITE_*. Không dùng mock làm production.
