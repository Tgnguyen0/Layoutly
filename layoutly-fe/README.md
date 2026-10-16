# Layoutly Frontend

Giao diện React + Vite + Tailwind cho **Layoutly**, kết nối backend PostgreSQL/Spring Security qua API thật.

Hướng dẫn chính thức về Docker Compose, biến môi trường, migration dữ liệu cũ và tests: [PostgreSQL, Authentication và Saved Projects](../docs/LAYOUTLY_POSTGRESQL_AUTH_PROJECTS.md).

## Chạy dự án

```bash
npm install
npm run dev
```

Mặc định chạy tại `http://localhost:5173`. Trong lúc dev, mọi request tới `/api/...` sẽ được Vite tự động forward sang backend Spring Boot tại `http://localhost:8080` (xem `vite.config.js`) — vì vậy cần khởi động `layoutly-be` trước (`mvn spring-boot:run`).

## Build production

```bash
npm run build
```

Kết quả nằm ở thư mục `dist/`. Khi deploy thật, nhớ trỏ backend qua cùng domain/reverse proxy ở path `/api`, hoặc sửa `BASE` trong `src/lib/api.js` thành URL đầy đủ của backend.

## Cấu trúc

```
src/
├── App.jsx                 # Layout chính: sidebar form + khu vực kết quả
├── components/
│   ├── InputPanel.jsx      # Form nhập token / fileKey / nodeIds + các nút hành động
│   └── JsonTree.jsx        # Cây hiển thị JSON, có icon/màu riêng theo type node Figma
└── lib/
    └── api.js              # Lớp gọi API tới layoutly-be (/api/figma/*, /api/export/*)
```

## Tính năng

- Gọi các endpoint: `/me`, `/file`, `/nodes`, `/images`, `/components`, `/styles`
- Hiển thị kết quả dưới dạng cây có thể thu gọn/mở rộng, tô màu theo loại node Figma (CANVAS, FRAME, COMPONENT, TEXT, VECTOR...)
- Tải kết quả xuống dạng `.txt`, `.docx`, `.pdf` (2 định dạng sau gọi qua backend)
- Register/Login/Logout, Current User, Edit Profile, Change Password và email reset khi cấu hình SMTP sẵn sàng
- Dashboard quản lý project thật: tạo, tìm kiếm, sửa/đổi tên, nhân bản, xóa mềm, mở lại và xem export history
- Snapshot, DesignNode, assets, accepted revisions và AI conversation được liên kết với tài khoản
- Session dùng HttpOnly cookie; Figma token chỉ ở bộ nhớ. Không lưu password/JWT trong localStorage
- `npm run test:e2e:saved-projects` chạy luồng end-to-end với backend/PostgreSQL thật và HTTP fixture Figma
