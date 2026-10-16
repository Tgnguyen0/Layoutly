# Tài khoản và giao diện Layoutly

> Trạng thái lịch sử trước khi chuyển PostgreSQL. Từ 16/10/2026, account/session lưu bằng JPA, API được Spring Security bảo vệ và project có owner. Xem [yêu cầu và hướng dẫn hiện hành](LAYOUTLY_POSTGRESQL_AUTH_PROJECTS.md); các mô tả account lưu file và cookie chỉ ở /api/auth bên dưới đã được thay thế.

Ngày cập nhật: 16/10/2026. Triển khai trên frontend React và backend Spring Boot hiện có. Không thay thế phần AI/workspace đang được phát triển trong checkout.

## Các chức năng đã hoạt động

- Đăng ký bằng họ tên, email, mật khẩu và chấp thuận điều khoản. Email được chuẩn hóa, không phân biệt hoa/thường và không được trùng. Đăng ký thành công tự đăng nhập và mở dashboard.
- Đăng nhập, đăng xuất và khôi phục trạng thái đăng nhập khi tải lại trang. Ghi nhớ đăng nhập tối đa 30 ngày; phiên thông thường hết hạn sau 8 giờ và cookie không được lưu bền qua lần đóng trình duyệt.
- Chỉnh sửa họ tên, email, công ty/tổ chức và giới thiệu. Đổi email yêu cầu mật khẩu hiện tại và thu hồi các phiên khác.
- Đổi mật khẩu với xác nhận mật khẩu mới, xoay token hiện tại và đăng xuất mọi phiên cũ.
- Xem danh sách phiên đăng nhập, nhận diện trình duyệt/hệ điều hành và đăng xuất các thiết bị khác.
- Xóa tài khoản với hộp thoại xác nhận và kiểm tra mật khẩu. Xóa hồ sơ và mọi phiên của đúng tài khoản; giữ các snapshot cục bộ.
- Trang tài khoản khi chưa đăng nhập hiển thị lời mời đăng nhập, không hiển thị hồ sơ. Backend tự kiểm tra quyền truy cập từng thao tác; không nhận user ID từ form để chọn hồ sơ cần sửa.

Các trang chính khi chạy frontend tại cổng 5174:

| Trang | URL |
| --- | --- |
| Đăng ký | http://127.0.0.1:5174/#/register |
| Đăng nhập | http://127.0.0.1:5174/#/login |
| Hồ sơ | http://127.0.0.1:5174/#/account |
| Bảo mật | http://127.0.0.1:5174/#/settings |
| Dashboard | http://127.0.0.1:5174/#/dashboard |

## Chạy trên máy

Trong terminal PowerShell thứ nhất, từ thư mục gốc dự án:

```powershell
Set-Location layoutly-be
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot'
mvn spring-boot:run
```

Trong terminal thứ hai, cũng từ thư mục gốc dự án:

```powershell
Set-Location layoutly-fe
node scripts/build-preview-runtime.mjs
node node_modules/vite/bin/vite.js --host 127.0.0.1 --port 5174 --strictPort
```

Dùng hai terminal này để giữ backend 8080 và frontend 5174 chạy cùng lúc. Chọn một hostname nhất quán khi truy cập, vì cookie của `localhost` và `127.0.0.1` khác nhau. Dừng bằng Ctrl+C. Những lệnh trên dùng dependencies đã có trong checkout; khi cài mới cần cài dependencies frontend và Maven trước.

## Giao diện

Trang đăng nhập/đăng ký dùng bố cục hai cột, màu nền xanh xám nhẹ, tiêu đề rõ ràng và form có trạng thái chờ/lỗi. Bỏ minh họa sneaker và danh sách chức năng chưa phát hành khỏi trang tài khoản. Ở mobile, phần giới thiệu được thu gọn để người dùng thấy form ngay.

Trang hồ sơ và bảo mật có điều hướng riêng, avatar bằng chữ viết tắt, các nhóm thông tin và nút lưu chỉ bật khi có thay đổi. Dashboard có lời chào theo tài khoản thật, lối vào hồ sơ/bảo mật và nút đăng xuất. Bỏ các mục menu bị vô hiệu hóa trước đây. Header và nội dung landing dùng tiếng Việt nhất quán, mô tả những chức năng đang có.

Ảnh nghiệm thu được tạo bằng các bài kiểm thử của dự án, lưu cục bộ trong thư mục đã ignore `layoutly-fe/visual-artifacts/accounts/`:

- [Đăng nhập desktop](../layoutly-fe/visual-artifacts/accounts/login-1440.png)
- [Đăng ký mobile](../layoutly-fe/visual-artifacts/accounts/register-390.png)
- [Hồ sơ desktop](../layoutly-fe/visual-artifacts/accounts/account-profile-1440.png)
- [Bảo mật mobile](../layoutly-fe/visual-artifacts/accounts/account-security-390.png)
- [Dashboard desktop](../layoutly-fe/visual-artifacts/accounts/dashboard-1440.png)

Các PNG là artifacts QA cục bộ, không nằm trong Git. Tài khoản trong ảnh là tài khoản thử nghiệm đã được xóa sau bài test.

## Dữ liệu và phiên đăng nhập

Store nằm tại `<layoutly.data.directory>/accounts/accounts.json`, mặc định là `layoutly-be/layoutly-data/accounts/accounts.json` khi khởi chạy từ thư mục backend. Store được ghi qua file tạm rồi thay thế file chính; lỗi đọc store khiến dịch vụ dừng khởi tạo thay vì ghi đè dữ liệu. Sao lưu thư mục dữ liệu khi backend dừng.

Mật khẩu dùng PBKDF2-HMAC-SHA256 với 600.000 vòng và salt ngẫu nhiên riêng. Đây là mức công việc được mô tả trong [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html). Token phiên có 32 byte ngẫu nhiên; server chỉ lưu SHA-256 digest. Cookie xác thực có HttpOnly, SameSite=Lax và scope `/api/auth`. Không đưa mật khẩu hoặc token đăng nhập vào localStorage/sessionStorage. DTO chứa mật khẩu được che trong `toString()` để tránh lộ mật khẩu qua log debug MVC.

Các thao tác ghi yêu cầu token CSRF gắn với servlet session. Xoay servlet session sau đăng nhập và đổi mật khẩu. Giới hạn số lần thử trong cửa sổ 15 phút và tối đa 20 phiên hoạt động mỗi tài khoản. API tài khoản dùng `Cache-Control: no-store`.

CORS tài khoản được cấu hình riêng, cho phép GET/POST/PATCH/DELETE từ các origin phát triển `localhost`/`127.0.0.1` cổng 5173/5174. Các origin khác không được đọc token CSRF. Cấu hình cũ chỉ GET/POST của API Figma được giữ nguyên. Có thể cấu hình `layoutly.auth.allowed-origins` cho hostname triển khai thực tế.

Nếu triển khai qua HTTPS, cấu hình `LAYOUTLY_AUTH_SECURE_COOKIE=true` và `SERVER_SERVLET_SESSION_COOKIE_SECURE=true`, đồng thời cấu hình reverse proxy đúng origin. Store hiện phục vụ một tiến trình backend; không chạy nhiều instance ghi chung file. Rate limit đang lưu trong bộ nhớ và dùng địa chỉ kết nối đến backend; cần thiết kế lại phần lưu trữ/rate limit khi triển khai nhiều instance hoặc sau proxy công cộng.

## API

| Method | Đường dẫn | Chức năng |
| --- | --- | --- |
| GET | `/api/auth/capabilities` | Khả năng dịch vụ, contract v1 |
| GET | `/api/auth/csrf` | Khởi tạo CSRF session |
| POST | `/api/auth/register` | Tạo tài khoản và đăng nhập |
| POST | `/api/auth/login` | Đăng nhập |
| GET | `/api/auth/me` | Hồ sơ hiện tại hoặc `user: null` |
| PATCH | `/api/auth/profile` | Cập nhật hồ sơ của phiên hiện tại |
| POST | `/api/auth/password` | Đổi mật khẩu và xoay phiên |
| GET | `/api/auth/sessions` | Các phiên của tài khoản hiện tại |
| POST | `/api/auth/sessions/revoke-others` | Đăng xuất các thiết bị khác |
| POST | `/api/auth/logout` | Thu hồi phiên và xóa cookie |
| DELETE | `/api/auth/account` | Xóa tài khoản sau xác nhận mật khẩu |

## Kiểm chứng

- Frontend: `npm run build` thành công; `npm test` qua 51/51 kiểm thử.
- Backend: `mvn -o test` với Java 17 và local repository hiện có qua 65/65 kiểm thử. Có 12 kiểm thử mới cho service/controller/CORS tài khoản. Bài live OpenRouter hiện có tự bỏ phần gọi mạng khi thiếu API key, dù JUnit không tính thành skipped; không coi đây là chứng minh tích hợp AI trực tiếp.
- E2E: `accounts.spec.js` và `public-site.spec.js` qua **11/11**, không skip, trong 36,4 giây. Kiểm tra backend thật, form thật, hai browser context độc lập, thu hồi phiên, đổi email/mật khẩu, đăng nhập lại, xóa tài khoản thử và responsive. Các trang public được kiểm tra ở 1440, 1280, 1024, 768, 390, 320px; hồ sơ/bảo mật ở 1440, 1024, 768, 390, 320px; dashboard thêm 850px để kiểm tra breakpoint tablet.
- Smoke Bridge deep link dùng snapshot đã có `e3f67b94-7edd-4880-9782-ab7d562fb90d`, không tạo dữ liệu thiết kế giả.
- Đã xem ảnh đăng nhập desktop, hồ sơ desktop và bảo mật mobile. Không có browser in-app được kết nối; ảnh và kiểm tra tương tác dùng test runner Playwright đã có trong dự án.

Trong quá trình thử đã phát hiện và sửa lỗi CORS chặn PATCH qua Vite, cùng việc trang login tự chuyển về dashboard khi browser context còn giữ hồ sơ của một phiên đã bị thu hồi. Các thay đổi AI/workspace có sẵn được giữ lại; không reset hoặc commit Git.

## Giới hạn hiện tại

Chưa tích hợp gửi thư, xác minh email và đặt lại mật khẩu qua email. Capability `passwordReset` trả `false`; màn hình khôi phục nói rõ giới hạn này và không gửi thông báo thành công giả. Đổi mật khẩu trong mục Bảo mật hoạt động khi người dùng biết mật khẩu hiện tại.

Tài khoản và hồ sơ là dữ liệu theo người dùng. Snapshot, assets và workspace thiết kế vẫn dùng cơ chế lưu cục bộ hiện có, chưa trở thành thư viện riêng theo tài khoản. Không nên coi những API snapshot/AI cũ là các tài nguyên đã được phân quyền theo tài khoản trong đợt này.
