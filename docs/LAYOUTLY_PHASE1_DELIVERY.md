# Layoutly Phase 1 Delivery

> PostgreSQL đã được chọn chính thức và triển khai sau Phase 1. Xem [PostgreSQL, Authentication và Saved Projects](LAYOUTLY_POSTGRESQL_AUTH_PROJECTS.md). Đề xuất MySQL trong báo cáo lịch sử này đã bị thay thế.

Ngày: 2026-10-09. **Phase 1 đã triển khai; Phase 2 chưa bắt đầu, chờ duyệt.**

## 1. Phạm Vi Đã Làm

- Public design system theo reference: nền trung tính, navy primary, vàng ấm, teal tiết chế, borders/shadow nhẹ, font Inter local, Lucide icons. Component có focus/hover/disabled/loading states; CSS namespace `.public-app` không thay CSS workspace cũ.
- Landing thật trong ứng dụng hiện tại: header Home/Work/Grow/Stay, CTA tới workspace đang hoạt động, sections Figma Import/Live Preview/Navigation/Export và các capability chưa mở được đánh dấu rõ. Trạng thái Bridge lấy từ backend thật, có timeout/retry.
- Login/Register/Forgot Password UI: email/name/password validation, show/hide, confirmation/consent, remember checkbox, pending/error/retry states và keyboard navigation. Không seeded users/projects, không fake session, không OAuth buttons giả.
- Capability client V1 và CSRF handshake chuẩn bị cho Phase 2. Backend hiện không có `/api/auth/capabilities`;404/501 => unavailable. Submit disabled, không gửi password khi chưa có capability. Positive tests là mock contract, không tuyên bố có authentication backend.
- Giữ workspace/import/generator/export cũ. `#workspace` và legacy anchors tiếp tục mở workspace; Bridge `?snapshotId=` được mở đúng và canonicalize về `/#workspace` để reload không rơi vào landing; preview back link trở lại workspace.
- Chuẩn bị [contracts và migration Phase 2](LAYOUTLY_PHASE2_CONTRACTS.md): session/CSRF, MySQL metadata boundaries, owner-filtered APIs, immutable revisions, Bridge pairing và migration snapshot cũ.

Không triển khai Dashboard/persistence theo account, OAuth, reset email delivery, AI chat/provider, editable revisions hoặc inference engine trong Phase 1. Không mở website public/multi-user; A01/A11 và các findings audit vẫn là gates của backend hiện tại.

## 2. Đường Dẫn Chạy Thử

Server mới được khởi động trên loopback, PID21776 tại thời điểm bàn giao:

- Landing: <http://127.0.0.1:5174/>
- Login UI: <http://127.0.0.1:5174/#/login>
- Register UI: <http://127.0.0.1:5174/#/register>
- Forgot UI: <http://127.0.0.1:5174/#/forgot-password>
- Workspace hiện tại: <http://127.0.0.1:5174/#workspace>

Backend8080 và frontend5173 có sẵn vẫn chạy, không bị stop/restart trong Phase 1. Server5174 dùng Vite proxy tới backend8080. Auth buttons hiện disabled là trạng thái đúng khi backend tài khoản chưa được triển khai, không phải lỗi giả lập.

Hash routes không cần thêm server routes. Direct `/login`/`register` cũng được nhận trong Vite dev; production direct links cần SPA fallback config, chưa deploy/verify hosted mode.

## 3. Files Và Bảo Toàn Working Tree

Trước Phase 1, source first-party117file được đối chiếu với audit: không có thay đổi thêm giữa hai lượt. SHA-256 cuối xác nhận chỉ4file đã có từ trước đổi trong phạm vi Phase 1:

| File đã có | Thay đổi của Phase 1 |
| --- | --- |
| `.gitignore` | Ignore test tool node_modules/reports/artifacts, không ignore source |
| `layoutly-fe/package.json` | Font và test dependencies/scripts; app vẫn React18/Vite5/Tailwind3 |
| `layoutly-fe/package-lock.json` | Lock dependencies tương ứng |
| `layoutly-fe/src/App.jsx` | Public router entry + hai compatibility URL fixes; không rewrite WorkspaceApp |

Các file mới:

| Nhóm | Đường dẫn |
| --- | --- |
| Brand/header/footer/status | `layoutly-fe/src/components/public/` |
| Landing/auth/public routes | `layoutly-fe/src/pages/` |
| Public tokens/styles | `layoutly-fe/src/styles/public.css` |
| Routing/auth contract client | `layoutly-fe/src/lib/routes.js`, `accounts.js` |
| Brand illustration/license | `layoutly-fe/public/brand/design-to-code.png`, `Inter-OFL.txt`, `README.md` |
| Component/contract tooling | `layoutly-fe/tooling/tests/` gồm package/lock/config/setup/specs |
| Playwright smoke | `layoutly-fe/playwright.config.js`, `tests/e2e/public-site.spec.js` |
| Phase docs | Tài liệu này và `docs/LAYOUTLY_PHASE2_CONTRACTS.md` |

Không file cũ nào bị xóa. Backend, hai plugin, `src/index.css`, `src/lib/api.js` và các source/test chưa commit của người dùng không đổi SHA-256. Không reset, formatter/lint fix, tự commit hay merge. Các thay đổi người dùng có từ trước vẫn ở working tree.

Minh họa được tạo bằng built-in ImageGen và lưu tại `layoutly-fe/public/brand/design-to-code.png`. [Asset notes và prompt cuối](../layoutly-fe/public/brand/README.md) ghi rõ đây là illustration, không phải screenshot chức năng AI/tài khoản đã triển khai.

## 4. Build Và Tests Đã Chạy

Môi trường: Windows; Java17.0.20/Maven3.9.15; Node22.17.1/npm10.9.2. App Vite5.4.21. Component tooling riêng Vitest5.0.3/Vite8.3.4; Playwright1.64.0 với Chrome channel cài trên máy.

| CWD | Lệnh thực sự chạy | Kết quả cuối |
| --- | --- | --- |
| `layoutly-fe` | `npm.cmd run build` | Exit0,1852modules; app Vite5.4.21; build sau compatibility/spacing fixes |
| `layoutly-fe` | `npm.cmd test` | Exit0,29tests/3files pass; routing10, account validation/client11, public UI8 |
| `layoutly-fe` | `$env:LAYOUTLY_SMOKE_SNAPSHOT='e3f67b94-7edd-4880-9782-ab7d562fb90d'; npm.cmd run test:e2e` | Exit0,8tests pass; final run10.0s, gồm Bridge deep link + reload |
| `layoutly-be` | `mvn.cmd -o test` với JAVA_HOME override | Exit0,28tests,0failures/0errors/0skipped,11classes |
| `layoutly-be` | `mvn.cmd -o -DskipTests package` | Exit0,Spring Boot JAR repackage; incremental, không clean; test skipped **ở lệnh package**, đã chạy riêng trước đó |
| `layoutly-fe` | `npm.cmd audit --json` | Exit1,10advisories:3moderate/7high/0critical thuộc existing build-tool dependency graph |
| `layoutly-fe` | `npm.cmd audit --omit=dev --json` | Exit0,0advisories production dependency graph theo npm lúc chạy |
| `layoutly-fe/tooling/tests` | `npm.cmd audit --json` | Exit0,0advisories |
| Root | `git diff --check`, SHA-256 before/after | Exit0; LF/CRLF warnings hiện có;4file đúng phạm vi đổi,0file mất |

Executable Maven dùng ở máy này:

```powershell
$env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent (Get-Command java.exe).Source)
& 'C:\Users\thanh\Downloads\apache-maven-3.9.15-bin\apache-maven-3.9.15\bin\mvn.cmd' -o test
```

**Nhật ký trung gian:** test runner Vitest3 tương thích Vite5 ban đầu có advisories, đã bỏ khỏi app và chuyển sang tooling riêng bản vá; không để lại critical dependency đó. Có lượt Playwright7pass/1skip khi không truyền snapshot env; đã rerun với env và final8pass. Không auto `npm audit fix --force` hoặc nâng main Vite/Tailwind. Plugin build/lint không rerun trong Phase 1 vì source không đổi; kết quả lần audit không được ghi thành kết quả mới.

`npm audit` sạch không chứng minh ứng dụng đủ security. Dev graph còn advisory, backend preview/cache/auth issues trong audit chưa được sửa; chỉ bind loopback khi dùng giai đoạn này. Dependency remediation có scope/review riêng, không gộp major upgrades trong redesign.

## 5. Screenshot Và Browser Evidence

8Playwright tests gồm:

- Landing/Login/Register/Forgot tại1440,1280,768,390,320px: local Inter loaded, images decoded,0unexpected horizontal overflow, controls/headings trong viewport,0pageerror.
- Mobile hero image không chạm vào notes; auth title không dính chữ khi bỏ line break.
- Email/password validation, show/hide, unavailable auth submit disabled, không authPOST, mobile menu Escape/focus,404 page.
- `/api/bridge/status` thật READY -> landing status -> mở workspace cũ.
- Snapshot local có thật được GET/reopen, iframe render `.figma-node`, code load, canonical URL và reload. Không tạo fixture/project demo, không gọi REST Figma mới.

20screenshots final đã tạo, có HTML report/trace khi fail. Đã xem ảnh desktop/mobile/tablet và sửa hai lỗi spacing tìm thấy trước khi rerun:

- [Landing desktop](../layoutly-fe/test-results/public-site-public-pages-fit-1440px-and-load-local-assets/landing-1440.png)
- [Login desktop](../layoutly-fe/test-results/public-site-public-pages-fit-1440px-and-load-local-assets/login-1440.png)
- [Register mobile](../layoutly-fe/test-results/public-site-public-pages-fit-390px-and-load-local-assets/register-390.png)
- [Login tablet](../layoutly-fe/test-results/public-site-public-pages-fit-768px-and-load-local-assets/login-768.png)
- [Playwright report](../layoutly-fe/playwright-report/index.html)

Screenshots/report là output ignored, có thể bị thay khi rerun. Đây là **UI/layout smoke**, không phải baseline pixel so với Figma, không tính repeat-run noise hoặc golden regression và không đóng G1 của roadmap. Chưa test clean React ZIP install/build trong Phase 1; chưa Figma Desktop/plugin-host acceptance, MySQL persistence hoặc auth backend workflow.

Browser skill không có browser connection trong session; đã xác nhận discovery trống rồi dùng local Playwright fallback. Không điều khiển hoặc đọc session/cookie/profile trình duyệt người dùng.

## 6. Chạy Lại

```powershell
# CWD: layoutly-fe
npm.cmd ci
npm.cmd --prefix tooling/tests ci
npm.cmd test
npm.cmd run build
npm.cmd run test:e2e
```

Hai lệnh `ci` trên là hướng dẫn replay, **chưa chạy clean ci trong lần bàn giao này**; đã chạy npm install với lockfiles. Test tooling yêu cầu Node>=22.12.0. E2E cần Chrome cài local, backend8080 để live Bridge test pass. Không có snapshot env thì deep-link case được skip có lý do; truyền ID snapshot local được phép đọc để kiểm toàn bộ8tests. Script có thể khởi động dev server5174 nếu chưa có; không stop server do người dùng đang chạy.

Tooling riêng là có chủ đích: bản Vitest mới cần Vite>=6.4 và Node>=22.12; không nâng application Vite5 chỉ để thêm test. [Vitest requirements](https://vitest.dev/guide/). Tests qua user-facing forms với [React Testing Library](https://testing-library.com/docs/react-testing-library/intro/). Inter dùng license từ package, bảo toàn tại `public/brand/Inter-OFL.txt`; [Inter project](https://rsms.me/inter/).

## 7. Review Trước Phase 2

Xem [LAYOUTLY_PHASE2_CONTRACTS.md](LAYOUTLY_PHASE2_CONTRACTS.md) để duyệt MySQL/session/owner-isolation/storage migration. Phase 2 cần service thật trước khi bật auth UI và Dashboard; không thêm project demo để lấp màn hình. AI vẫn gated sau baseline.

**Dừng ở Phase 1. Không tự chuyển Phase 2 hoặc commit khi chưa có duyệt.**
