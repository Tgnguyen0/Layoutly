# Layoutly Frontend

Giao diện React + Vite + Tailwind cho **Layoutly** — công cụ khám phá cấu trúc file Figma, kết nối tới backend `layoutly-be`.

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
- Token/fileKey/nodeIds tự lưu vào `localStorage`, không cần nhập lại mỗi lần mở
