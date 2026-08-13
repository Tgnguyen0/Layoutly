# Layoutly Backend (Spring Boot)

Backend của dự án **Layoutly** — công cụ tự động chuyển đổi bản vẽ thiết kế Figma thành mã nguồn giao diện Front-end.

Ở giai đoạn hiện tại, backend đảm nhiệm việc gọi Figma REST API và trả về dữ liệu JSON mô tả cấu trúc file thiết kế (Document → Canvas → Frame → Node), làm nền tảng cho module Parser/Generator sinh mã HTML/CSS ở các bước tiếp theo.

## 1. Lấy Figma Personal Access Token
- Vào Figma > Settings > Personal access tokens > Generate new token

## 2. Lấy fileKey
- Mở file Figma trên web, URL dạng: `https://www.figma.com/file/ABC123XYZ/Ten-File`
- `ABC123XYZ` chính là fileKey

## 3. Chạy project
```bash
mvn spring-boot:run
```
(Không bắt buộc set `FIGMA_TOKEN` trước — có thể nhập token trực tiếp ở bước 4)

## 4. Cách test dễ nhất: dùng trang web có sẵn

Mở trình duyệt vào **http://localhost:8080** — có sẵn giao diện để nhập token, fileKey, node IDs và bấm nút xem JSON ngay, không cần Postman hay curl. Token/fileKey được lưu tạm trong trình duyệt (localStorage) nên không phải nhập lại mỗi lần.

Sau khi xem kết quả, có 3 nút để tải xuống ngay dưới các nút gọi API:
- **.txt** — tải trực tiếp trong trình duyệt, không qua server
- **.docx** — server dùng Apache POI tạo file Word, giữ format monospace cho dễ đọc JSON
- **.pdf** — server dùng Apache PDFBox tạo file PDF, tự xuống dòng và tự sang trang khi nội dung dài

## 5. Hoặc test bằng curl (dùng khi không có token cấu hình sẵn, nhớ thêm header)

```bash
TOKEN="figd_xxxxxxxxxxxx"

# Test token còn sống không
curl -H "X-Figma-Token: $TOKEN" http://localhost:8080/api/figma/me

# Lấy toàn bộ cấu trúc file (document tree, pages, layers...)
curl -H "X-Figma-Token: $TOKEN" http://localhost:8080/api/figma/file/ABC123XYZ

# Lấy 1 vài node cụ thể (lấy id bằng cách click phải layer > Copy/Paste as > Copy link, id nằm trong URL sau node-id=)
curl -H "X-Figma-Token: $TOKEN" "http://localhost:8080/api/figma/file/ABC123XYZ/nodes?ids=1:2,1:3"

# Xuất ảnh PNG của node
curl -H "X-Figma-Token: $TOKEN" "http://localhost:8080/api/figma/file/ABC123XYZ/images?ids=1:2&format=png"

# Lấy components
curl -H "X-Figma-Token: $TOKEN" http://localhost:8080/api/figma/file/ABC123XYZ/components

# Lấy styles (color/text/effect)
curl -H "X-Figma-Token: $TOKEN" http://localhost:8080/api/figma/file/ABC123XYZ/styles
```

Nếu đã set biến môi trường `FIGMA_TOKEN` trước khi chạy app thì không cần thêm header `-H "X-Figma-Token: ..."` nữa — app sẽ tự dùng token đó làm mặc định.

Response trả về là JSON gốc từ Figma — copy vào công cụ format JSON để xem cấu trúc dễ hơn.
