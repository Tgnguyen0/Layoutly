# PostgreSQL, Authentication và Saved Projects

Yêu cầu chính thức ngày 16/10/2026: **PostgreSQL là database chính duy nhất của Layoutly. Không sử dụng MySQL.** Tài liệu này thay thế các đề xuất database và mô tả account lưu file trước đây.

## Kết quả kiểm tra trước triển khai

- Backend Spring Boot 3.3.2 / Java 17 chưa có JPA entities, repositories, JDBC driver hay Flyway migrations.
- Account đã có triển khai lưu file tại `<layoutly.data.directory>/accounts/accounts.json`, gồm hồ sơ, PBKDF2 password hashes và session digests.
- Snapshot lưu raw Figma JSON, DesignNode IR, metadata, assets/reference trên ổ đĩa. AI workspace, revisions và accepted revision cũng lưu file.
- Dashboard cũ chỉ hiển thị snapshot trong sessionStorage, chưa có thư viện project theo tài khoản.
- Không phát hiện database khác được backend sử dụng. Không có bước chuyển đổi/xóa MySQL hay dữ liệu database cũ.

## Chạy PostgreSQL và website

Yêu cầu: Java 17+, Maven, Node.js, Docker Desktop/Compose đang chạy.

Từ thư mục gốc, tạo `.env` từ `.env.example` nếu chưa có:

```powershell
if (-not (Test-Path -LiteralPath .env)) { Copy-Item -LiteralPath .env.example -Destination .env }
```

Đặt `DATABASE_PASSWORD` thành mật khẩu riêng trong `.env`, sau đó:

```powershell
docker compose up -d postgres
docker compose ps
.\scripts\Start-Backend.ps1
```

Script đọc các biến cấu hình Layoutly/database/mail từ `.env` vào process backend, không in secret. Docker Compose tự đọc `.env`; Spring Boot không tự đọc file này nếu chạy trực tiếp bằng Maven.

Trong terminal khác:

```powershell
Set-Location layoutly-fe
npm ci
npm run dev
```

Mở `http://localhost:5173/#/register`. Đăng ký tài khoản thật, tạo project và import qua Figma URL hoặc Bridge. Không có tài khoản hay project demo được seed.

Backend mặc định cổng 8080; PostgreSQL cổng 5432, chỉ bind localhost. Docker volume `layoutly_postgres` giữ dữ liệu qua lần khởi động lại. `docker compose down` dừng dịch vụ; không dùng tùy chọn xóa volume khi cần giữ dữ liệu.

### Biến môi trường

| Biến | Mục đích |
| --- | --- |
| DATABASE_URL | JDBC URL, mặc định `jdbc:postgresql://localhost:5432/layoutly` |
| DATABASE_USER / DATABASE_PASSWORD | Tài khoản PostgreSQL; không hardcode password trong source |
| DATABASE_POOL_SIZE | Hikari pool, mặc định 10 |
| POSTGRES_DB / POSTGRES_PORT | Tên DB/cổng trong Docker Compose; nếu đổi cổng, cập nhật DATABASE_URL tương ứng |
| LAYOUTLY_AUTH_SECURE_COOKIE | `true` khi phục vụ qua HTTPS |
| LAYOUTLY_PUBLIC_URL | Origin frontend dùng trong email reset |
| LAYOUTLY_API_TARGET | Proxy của Vite nếu backend chạy ở cổng khác, mặc định `http://localhost:8080` |
| FIGMA_TOKEN | Token Figma server tùy chọn; UI có thể truyền token riêng trong bộ nhớ |
| LAYOUTLY_IMPORT_LEGACY | Bật quy trình import file cũ có kiểm soát, mặc định false |
| LAYOUTLY_LEGACY_OWNERSHIP_FILE | File JSON ánh xạ snapshot cũ sang UUID user đã xác nhận |

Production phục vụ frontend và `/api` qua cùng origin/reverse proxy, dùng HTTPS và cookie Secure. Cấu hình CORS của backend chỉ cho các origin tin cậy; không dùng wildcard cho tài khoản.

## Schema và lưu trữ

Flyway chạy `V1__identity_and_saved_projects.sql` và `V2__session_and_history_constraints.sql`. Hibernate luôn `ddl-auto=validate`, Flyway clean bị tắt và không tự baseline database có dữ liệu.

| Bảng | Dữ liệu |
| --- | --- |
| users | UUID, email chuẩn hóa/unique, password hash, profile, timestamps, optimistic version, soft deletion |
| user_sessions | UUID, user UUID, SHA-256 token digest, expiry, device, WEB/BRIDGE, scope snapshot cho Bridge |
| snapshots | Owner UUID, ID tương thích storage cũ, JSONB metadata; snapshot mới dùng chuỗi UUID |
| projects | UUID, owner, tên/mô tả/framework, snapshot, JSONB metadata, timestamps/version, soft deletion |
| project_versions | UUID, project/snapshot/revision, DesignNode và metadata JSONB tại thời điểm lưu |
| project_assets | UUID, project/snapshot, relative path, byte size, SHA-256 và JSONB metadata |
| exports | UUID, project/snapshot/revision, HTML/REACT, byte size, timestamp, JSONB metadata |
| ai_conversations / ai_messages | Hội thoại theo snapshot đã có ownership; prompt/response JSONB, khôi phục trong UI |
| password_reset_tokens | Digest của token dùng một lần, expiry và thời điểm sử dụng |
| legacy_imports | Nguồn import, checksum, thời điểm và số bản ghi |

FK ghép `(snapshot_id, owner_id)` bảo đảm project không trỏ snapshot của người khác. Raw JSON, ảnh, reference, workspace và revision files vẫn dùng storage hiện tại; DB giữ quyền sở hữu và liên kết. ZIP được tạo khi tải xuống; bảng exports lưu lịch sử, không lưu bản ZIP nhị phân.

Import REST tạo snapshot riêng; raw Figma cache được tách theo hash của credential. Cache cũ chưa có scope được giữ trên đĩa nhưng không được phục vụ qua API nhiều tài khoản. Refresh qua API tạo snapshot mới, giữ snapshot cũ. Duplicate sao chép cả snapshot/assets và workspace/revision files sang ID mới.

Preview nhúng ảnh đã được authorization thành data URL để hoạt động trong iframe sandbox; các API asset riêng vẫn yêu cầu quyền. React revision preview tải asset qua API của editor trước khi đưa bytes vào iframe cô lập.

## Authentication và phân quyền

Spring Security bảo vệ mọi `/api/**` ngoài capabilities, CSRF, login/register/me, password reset và Bridge status. Các route công khai có mutation vẫn chịu CSRF.

- Password mới dùng Spring Security PBKDF2-HMAC-SHA256, salt ngẫu nhiên 16 bytes, 600.000 vòng. Hash PBKDF2 cũ được xác minh và nâng định dạng sau login.
- Session dùng token ngẫu nhiên 32 bytes trong cookie `layoutly_session`, HttpOnly, SameSite=Lax, path `/api`. Database chỉ lưu SHA-256 digest. Không dùng JWT hoặc lưu credential trong localStorage.
- Login/password change xoay token và servlet CSRF session. Logout thu hồi session. Change/reset password thu hồi phiên cũ; reset token có hiệu lực 30 phút và dùng một lần.
- CSRF áp dụng cho các API thay đổi trạng thái, gồm project và AI. Các yêu cầu frontend đồng thời dùng chung lần khởi tạo CSRF đang chạy để tránh race cookie.
- Mọi route snapshot, assets, preview, AI jobs/files/revisions/conversation/screenshots/export đều đi qua kiểm tra owner ở backend. Project truy vấn theo owner và trả 404 cho tài khoản khác.
- Xóa project và account là soft deletion; không tự xóa snapshot, workspace hoặc file gốc.

| API | Chức năng |
| --- | --- |
| POST /api/auth/register, /login, /logout | Đăng ký, đăng nhập, đăng xuất |
| GET /api/auth/me, /csrf, /capabilities | Current user, CSRF handshake, capability |
| PATCH /api/auth/profile | Hồ sơ; đổi email cần mật khẩu hiện tại |
| POST /api/auth/password | Đổi mật khẩu |
| GET /api/auth/sessions | Thiết bị/phiên đăng nhập |
| POST /api/auth/sessions/revoke-others | Thu hồi phiên khác |
| POST /api/auth/forgot-password, /reset-password | Reset khi cấu hình email sẵn sàng |
| POST /api/auth/bridge-token | Mã kết nối Bridge có hiệu lực 10 phút |
| GET/POST /api/projects | Search theo q/framework; tạo project |
| GET/PATCH/DELETE /api/projects/{id} | Đọc, sửa/đổi tên, xóa mềm |
| POST /api/projects/{id}/duplicate, /reopen | Nhân bản, mở lại |
| GET/POST /api/projects/{id}/versions | Đọc lịch sử, lưu phiên bản/accepted revision |
| GET /api/projects/{id}/assets, /exports | Asset manifest và export history |
| GET /api/snapshots/{snapshotId}/ai/conversation | Hội thoại đã lưu |

### Figma Bridge

Trong workspace đã đăng nhập, mở **Import → Figma Bridge → Tạo mã kết nối Bridge**. Dán mã vào plugin Layoutly hoặc Layoutly Figma Bridge. Plugin gửi header `X-Layoutly-Bridge-Token`; không dựa cookie bên thứ ba trong Figma.

Mã chỉ được dùng để import và GET snapshot vừa được gắn vào mã đó, không được truy cập projects/auth APIs. Cookie-authenticated import vẫn chịu CSRF; chỉ request Bridge có header token mới dùng cơ chế xác thực riêng thay CSRF. Hết 10 phút cần tạo mã mới. Token chỉ ở bộ nhớ UI và input; không lưu localStorage.

### Email reset

Điền `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`, `MAIL_SMTP_AUTH`, `MAIL_STARTTLS` và `LAYOUTLY_PUBLIC_URL`, sau đó bật `LAYOUTLY_PASSWORD_RESET_ENABLED=true`. Capability vẫn false khi thiếu sender/from hoặc chưa bật. Link đi tới `/#/reset-password?token=...`.

Chưa cấu hình SMTP thực trong môi trường kiểm thử này. Integration test dùng mail sender thu nhận thư để kiểm tra link dùng một lần và thu hồi phiên; chưa gửi email ra bên ngoài.

## Chuyển dữ liệu cũ an toàn

Không tự gán snapshot cũ cho người đăng ký đầu tiên. Không sửa file account hay snapshot gốc.

1. Dừng phiên backend cũ đang ghi file và sao lưu toàn bộ data directory. Ví dụ khi chạy backend từ `layoutly-be`:

   ```powershell
   New-Item -ItemType Directory -Path backups -Force | Out-Null
   Copy-Item -LiteralPath layoutly-be/layoutly-data -Destination backups/layoutly-data-before-postgres -Recurse
   ```

2. Khởi động PostgreSQL mới bằng Compose, dùng đúng `layoutly.data.directory` đang chứa dữ liệu cũ. Bật `LAYOUTLY_IMPORT_LEGACY=true` cho lần khởi động chuyển đổi.
3. Import account giữ UUID/profile, password hash và session digest chưa hết hạn. Import chạy trong transaction, có advisory lock và checksum provenance; nguồn lỗi/xung đột sẽ rollback, không ghi đè user đã tồn tại. Chạy lại cùng file không tạo trùng.
4. Sau khi kiểm tra UUID user trong DB, tạo ownership manifest riêng từ thông tin đã xác minh:

   ```json
   {
     "existing-snapshot-id": "confirmed-owner-user-uuid"
   }
   ```

5. Đặt `LAYOUTLY_LEGACY_OWNERSHIP_FILE` thành đường dẫn tuyệt đối tới manifest, giữ cờ import cho lần chạy này. Quy trình tạo ownership và saved project, giữ raw/assets/workspace cũ. Snapshot chưa được ánh xạ tiếp tục nằm trên đĩa và không thể bị tài khoản mới tự claim qua API.
6. Kiểm tra số user/project, đăng nhập lại để nâng cookie cũ từ path `/api/auth` sang `/api`, mở preview/export một project. Tắt cờ import sau cutover. Không chạy đồng thời backend file cũ làm writer.

Nếu nguồn đã import thay đổi, chương trình dừng để đối soát; không tự đồng bộ hoặc ghi đè. Mỗi file nguồn có transaction riêng. Lỗi ownership không làm mất account đã import từ file account.

Sao lưu PostgreSQL trước những migration tiếp theo, ví dụ:

```powershell
docker compose exec -T postgres pg_dump -U layoutly -d layoutly -Fc -f /tmp/layoutly-backup.dump
docker compose cp postgres:/tmp/layoutly-backup.dump ./backups/layoutly-backup.dump
```

Giữ đồng bộ bản sao DB với data directory. Không tự chạy down migration hoặc xóa volume để rollback. Dữ liệu phát sinh sau cutover nằm trong PostgreSQL; cần đối soát trước khi quay lại reader/writer cũ.

## Kiểm thử

Backend mặc định dùng Testcontainers PostgreSQL thật, cần Docker:

```powershell
Set-Location layoutly-be
mvn test
```

Nếu có một PostgreSQL kiểm thử riêng, có thể chủ động cấu hình:

```powershell
$env:LAYOUTLY_TEST_DATABASE_URL='jdbc:postgresql://localhost:55432/postgres'
$env:LAYOUTLY_TEST_DATABASE_USER='layoutly_test'
$env:LAYOUTLY_TEST_DATABASE_PASSWORD='your-test-password'
mvn test
```

Chế độ này dùng schema `layoutly_integration` trong database đã chỉ định; chỉ dùng một database dành cho test. Không có fallback H2 hoặc skip ngầm khi thiếu PostgreSQL/Docker.

Frontend:

```powershell
Set-Location layoutly-fe
npm test
npm run build
```

E2E Saved Projects: chạy backend riêng ở cổng 18080 với database/data directory kiểm thử, upstream `--figma.api.base-url=http://127.0.0.1:59990/v1` và `--figma.api.min-interval-ms=0`. Sau đó:

```powershell
Set-Location layoutly-fe
$env:LAYOUTLY_API_TARGET='http://127.0.0.1:18080'
npm run test:e2e:saved-projects
```

Config tự khởi động HTTP fixture Figma và Vite cổng 5174, rồi chạy Chrome. Các tài khoản/project được tạo qua API thật, không mock frontend API. Fixture chỉ thay phía Figma bên ngoài; không phải phép thử gọi Figma live bằng PAT người dùng. Dữ liệu E2E cần được tách khỏi dữ liệu đang sử dụng.

Kết quả thực chạy ngày 16/10/2026 được ghi ở phần nghiệm thu bên dưới.

## Nghiệm thu

- Backend: 82 tests pass, gồm 10 integration tests với PostgreSQL 16 thật, Flyway V1/V2 và Hibernate validate.
- Frontend: 52 component/unit tests pass; production build pass.
- E2E: luồng register/login/import/save/logout/login/reopen/export ZIP; dashboard search/rename/duplicate/delete; phân quyền hai account; Bridge import/asset/preview/save/reopen.
- E2E tài khoản hiện có cũng chạy qua: sửa hồ sơ/email, đổi mật khẩu, thu hồi session trên thiết bị khác, đăng nhập lại và xóa mềm tài khoản thử.
- Máy kiểm thử chưa có Docker; đã chạy PostgreSQL native trong `.local-build` ở cổng 55432, không dùng H2. Nhánh Testcontainers đã được cấu hình nhưng chưa thực thi trên Docker trong phiên này.
- Giữ giao diện navy/yellow hiện tại và kiểm tra dashboard ở 1440, 1024, 768, 390, 320px qua screenshot E2E; không thêm project giả để lấp dashboard.
- Không migrate trực tiếp dữ liệu người dùng sang database thử nghiệm, không xóa dữ liệu hiện có, không commit hoặc push.
