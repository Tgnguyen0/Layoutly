# Layoutly Backend (Spring Boot)

Backend của dự án **Layoutly** — công cụ tự động chuyển đổi bản vẽ thiết kế Figma thành mã nguồn giao diện Front-end.

**Cập nhật 16/10/2026:** dùng PostgreSQL, Spring Data JPA, Flyway và Spring Security. Chạy PostgreSQL trước backend; xem [hướng dẫn chạy, schema, migration dữ liệu cũ và tests](../docs/LAYOUTLY_POSTGRESQL_AUTH_PROJECTS.md). Không sử dụng MySQL.

Backend hiện hỗ trợ tài khoản/session, saved projects theo owner, Figma REST/Bridge, DesignNode, HTML/CSS và React generators, AI workspace/revisions và export ZIP.

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

Sử dụng frontend tại **http://localhost:5173/#/register**, đăng nhập rồi import Figma. API yêu cầu session cookie và CSRF cho mutation. Trang static debug cũ tại backend chỉ là công cụ legacy; luồng được hỗ trợ là React frontend. UI hiện tại giữ Figma token trong bộ nhớ, không lưu credential/JWT vào localStorage.

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
