# Layoutly Landing — phần bên dưới Hero

Triển khai và kiểm tra ngày 2026-10-16. Frontend React hiện có được giữ lại. Header, Hero JSX, ProductShowcase và hai stylesheet public/visual được đối chiếu hash/markup: **không đổi**. Màu navy, teal và vàng hiện tại tiếp tục được dùng.

## Nội dung đã triển khai

- Dải quy trình Figma → Import → Preview & Code → Export: bốn bước, screenshot Workspace thật, liên kết mở Workspace.
- Hai hàng tính năng xen kẽ ảnh lớn: Workspace với layers/preview/code, Dashboard với bốn dự án đã lưu. Có liên kết mở ảnh ở kích thước đầy đủ.
- Showcase chọn được Landing page, E-commerce, Dashboard hoặc Form/tài khoản; ảnh, mô tả và trạng thái tab thay đổi thật. Có điều khiển bằng phím mũi tên, Home/End.
- Demo thiết kế nguồn → HTML/CSS/React: đoạn mã lấy từ backend, màu cú pháp, tab, sao chép, file JSON nguồn và hai ZIP tải được. Nếu không tải được mã, hiển thị lỗi và giữ liên kết ZIP.
- FAQ sáu câu hỏi mở/đóng được; CTA tạo tài khoản và footer ba nhóm liên kết sản phẩm/tài khoản/dữ liệu.
- Các anchor mới làm việc cả khi đi từ trang Điều khoản trở về Landing.

## Hình ảnh và nguồn dữ liệu

`public/landing/workspace.png` và `dashboard.png` chụp trực tiếp từ Layoutly đang chạy, dùng tài khoản kiểm thử và schema PostgreSQL riêng `layoutly_marketing_e2e`. Các dự án mẫu được import bằng Bridge và lưu qua API thật. Không sử dụng dự án cá nhân để chụp.

Bốn fixture Atlas, Sneaker, Forma và Serein được tự biên soạn trong `tests/support/marketing-fixtures.mjs`. Ảnh showcase là preview thật do backend render. SVG landscape/chart/arch là hình gốc; ảnh sneaker tái sử dụng asset gốc đã có, có provenance trong `public/brand/README.md`. Quyền sử dụng và cách cập nhật ảnh ghi tại [public/landing/README.md](../layoutly-fe/public/landing/README.md).

Trang không dùng ảnh tham chiếu/collage làm nền giao diện. Các section mới không thêm khách hàng, lời đánh giá, avatar chứng thực hay điểm fidelity chưa đo. Dữ liệu dashboard Forma được ghi là minh họa. Preview marketing không được dùng như Figma golden.

## Đối chiếu ảnh tham chiếu và hai vòng chỉnh

Đối chiếu trực quan với ảnh trong yêu cầu: dải workflow nền sáng bo góc, ảnh sản phẩm nghiêng nhẹ, chữ navy và nhãn teal, showcase nền chuyển màu nhẹ, kết thúc bằng CTA và footer. Các hàng tính năng dùng ảnh lớn xen kẽ theo yêu cầu thay cho cụm card lặp lại. Header/Hero được giữ nguyên theo phạm vi đã chỉ định. Không công bố tỷ lệ pixel match với ảnh mẫu.

| Lượt | Kết quả kiểm tra / chỉnh sửa |
|---|---|
| Round 1 | Chụp toàn trang ở 1440/1024/768/390; không tràn ngang, không ảnh lỗi. Nhận thấy code thiếu phân cấp màu và Atlas lặp nhiều ở showcase. |
| Chỉnh 1 → Round 2 | Thêm syntax highlighting/line numbers, tăng chữ nội dung mobile, mở ảnh đầy đủ, chọn Dashboard làm showcase mặc định. 5 browser tests pass. |
| Chỉnh 2 → Round 3 | Đưa code excerpt về đúng node headline trong thiết kế, tăng vùng bấm và cỡ chữ tab/FAQ/footer trên mobile; giữ thông báo lỗi code khi đổi tab. 5 browser tests pass. |

Ảnh toàn trang cuối:

- [1440px](../.local-build/landing-redesign/round-3/landing-1440.png)
- [1024px](../.local-build/landing-redesign/round-3/landing-1024.png)
- [768px](../.local-build/landing-redesign/round-3/landing-768.png)
- [390px](../.local-build/landing-redesign/round-3/landing-390.png)

Ảnh chi tiết: [Workflow](../.local-build/landing-redesign/round-3/workflow.png), [Features](../.local-build/landing-redesign/round-3/features.png), [Demo](../.local-build/landing-redesign/round-3/demo.png). Round 1 và Round 2 vẫn được giữ cạnh Round 3 để review. Metrics JSON từng width ghi viewport/scrollWidth, ảnh thiếu, controls bị cắt và runtime/network errors.

## Validation

- **53 frontend tests pass**, gồm keyboard navigation và trường hợp không tải được mã mẫu.
- **Vite production build pass**.
- **5 Landing Playwright tests pass ở mỗi lượt**: bốn viewport, ảnh/fonts, tab/keyboard, clipboard, FAQ, ZIP downloads và anchors/cross-route navigation.
- Asset capture **1 pass** sau khi dùng port 5174 đã có trong trusted origins; không sửa cơ chế CSRF/CORS để chụp ảnh.
- **6 luồng E2E tài khoản/saved-projects đã xác minh**: lifecycle register/profile/password/sessions/login, Dashboard responsive, CRUD/search/duplicate/delete, cross-user authorization, REST import/save/logout/login/reopen/ZIP và Bridge assets + revision preview. Lượt regression đầu có 5 pass; case navigation được sửa setup để đăng nhập thật và chờ logout hoàn tất, sau đó chạy lại 1/1 pass. Product authentication không thay đổi.
- **ZIP React mẫu build sạch**: giải nén `atlas-react.zip` vào thư mục riêng, `npm ci` theo lock và `npm run build`; 34 modules, Vite build pass. HTML/React ZIP và JSON được kiểm tra qua HTTP và download event.
- CSS được format lại bằng PostCSS và đối chiếu AST để xác nhận giá trị/selector không đổi.

Logs: `.local-build/landing-redesign/{round-1,round-2,round-3,frontend-tests,frontend-build-final,regression,regression-navigation,sample-build}.log`. Kết quả giữ Header/Hero tại `preservation-result.json`.

## File thay đổi trong đợt này

- `src/pages/LandingPage.jsx`: giữ nguyên Hero, thay các section cũ bằng `LandingSections`.
- `src/components/public/LandingSections.jsx`: nội dung, showcase, demo và FAQ.
- `src/styles/landing.css`: style riêng cho các section và footer.
- `src/components/public/PublicFooter.jsx`: footer đầy đủ.
- `src/pages/PublicSite.jsx`: import stylesheet và xử lý anchor mới.
- `public/landing/*`: screenshot sản phẩm, code/JSON/ZIP mẫu và provenance.
- `playwright.landing*.config.js`, `tests/e2e/landing-visual.spec.js`, `tests/marketing/landing-assets.spec.js`, `tests/support/marketing-fixtures.mjs`: QA và capture opt-in.
- `tooling/tests/specs/public-ui.test.jsx`, `tests/e2e/accounts.spec.js`: cập nhật assertion nội dung mới và setup authenticated Dashboard.

Không thay backend, migrations, login logic, Dashboard/Workspace implementation hoặc generators. Không commit/push. Các thay đổi có trước phiên này vẫn được giữ.

## Chạy và kiểm tra lại

Giao diện local: **http://localhost:5173**; backend thường dùng port 8080 và dữ liệu local đã có. Máy chủ dev phản ánh source mới qua HMR.

Từ `layoutly-fe`, khi backend kiểm thử riêng đang chạy trên 18082:

```powershell
$env:LAYOUTLY_API_TARGET='http://127.0.0.1:18082'
$env:LAYOUTLY_LANDING_ROUND='review-new'
npx playwright test --config playwright.landing.config.js
npx playwright test --config playwright.landing-regression.config.js
npm test
npm run build
```

Capture assets là bước chủ động riêng, không nằm trong bộ E2E mặc định; xem asset README trước khi chạy để không thay screenshot đã review trong quá trình regression.
