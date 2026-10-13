# Layoutly Phase 1 Visual Fix

Ngày: 2026-10-09. Phạm vi: frontend hiện có; không thay backend, schema, generators hoặc hai plugin. Đây là triển khai UI, không phải Layout Inference Engine hay tích hợp AI.

## 1. Xem Kết Quả

- Ứng dụng: http://127.0.0.1:5174/
- Dashboard: http://127.0.0.1:5174/#/dashboard
- Workspace: http://127.0.0.1:5174/#workspace
- Gallery reference/before/after: http://127.0.0.1:5174/visual-artifacts/index.html
- Gallery trên disk: [index.html](../layoutly-fe/visual-artifacts/index.html).
- Editor cũ được giữ nguyên logic ở `/#/workspace-legacy`; fullscreen `/preview/:snapshotId` vẫn dùng route hiện có.

Backend 8080 và Vite 5174 đã chạy sẵn, được giữ lại, không khởi động thêm hoặc dừng server của người dùng. Artifacts chỉ lưu cục bộ và được ignore trong Git. Hai tài liệu audit/roadmap trước đây không bị sửa.

## 2. Reference Và Crop

Đã đọc trực tiếp `design-reference/layoutly-ui.png`, kích thước 1536 × 1024. Script `layoutly-fe/scripts/crop-reference.mjs` dùng canvas của Chromium để crop chính xác tọa độ, không sinh lại ảnh tham chiếu bằng AI. Không dùng ảnh gốc hoặc các crop làm background/component của ứng dụng.

| Vùng | x, y, width, height | Artifact |
| --- | --- | --- |
| Landing | 12, 32, 786, 473 | `visual-artifacts/reference/landing.png` |
| Login/Register | 815, 32, 708, 450 | `visual-artifacts/reference/login.png` |
| Dashboard main | 14, 550, 785, 381 | `visual-artifacts/reference/dashboard.png` |
| Dashboard sidebar đầy đủ | 14, 550, 169, 463 | `visual-artifacts/reference/dashboard-sidebar.png` |
| Workspace | 813, 495, 711, 427 | `visual-artifacts/reference/workspace.png` |
| AI tray | 194, 941, 1331, 73 | `visual-artifacts/reference/ai.png` |

Dashboard được cắt trước vùng AI tray chồng bên dưới của collage, thay vì gộp nhầm AI vào ảnh dashboard. Workspace và AI được review riêng. Các crop là nguồn tham chiếu thị giác; collage không cung cấp viewport gốc, font files hoặc cùng snapshot nên không báo phần trăm pixel parity.

## 3. Thay Đổi UI

**Landing:** headline ba dòng, navy/yellow, CTA và checklist; browser sneaker + mobile + navy code editor + Figma badge; ảnh sneaker riêng, không có screenshot giả làm UI ứng dụng. Header Home/Work/Grow/Stay vẫn điều hướng thực; Work mở dashboard. Giữ Bridge readiness thực và đánh dấu AI/account storage chưa khả dụng.

**Auth:** layout riêng hai cột không có public header/footer; logo, welcome heading, benefit icons, minh họa công cụ bên trái; tabs/form bên phải. Giữ validation, password toggle, remember/consent, timeout, capabilities và CSRF contract đã có. Không thêm OAuth giả. Submit vẫn disabled khi backend không hỗ trợ.

**Dashboard:** sidebar, search, format filters, sort, project grid và empty state. Chỉ hiển thị snapshot hiện tại trong session khi backend thực sự trả dữ liệu, không biến sessionStorage thành hệ thống tài khoản. Thumbnail render snapshot thực trong iframe cô lập; kích thước theo card. Một snapshot có cả HTML và React nên xuất hiện trong cả hai filter. Không có thư viện dự án đa người dùng hoặc 8 dự án mẫu. CTA mới mở import dialog, không giả rằng đã tạo/lưu project.

**Workspace:** toolbar, viewport/zoom chỉ trong canvas, cây Pages/Layers có tìm kiếm/expand/collapse, node selection và outline, code tabs, clipboard, readonly Style/Layout/Interaction. Panels mobile mở/đóng độc lập. AI tray mở/thu gọn, input/send disabled và không giả hội thoại. Undo/redo/history disabled rõ. Export dùng endpoint cũ, có HTML AUTO/CANVAS/FRAME và React ZIP; test fixture vẫn gọi backend hiện có.

Preview được fetch từ endpoint cũ, thêm controlled selection bridge vào srcDoc, sandbox chỉ `allow-scripts` (không `allow-same-origin`). Parent kiểm tra source window, nonce và ID thuộc tree; child kiểm tra parent/nonce. Không sửa mã HTML/CSS/React export. Đây không phải tuyên bố đã khắc phục toàn bộ security findings của audit hoặc legacy fullscreen.

## 4. Ba Vòng Visual Review

Đã chụp trước khi đổi UI: 20 PNG, 4 routes × 5 widths. Dashboard trước thay đổi là 404, không được giả thành màn hình đã có.

| Vòng | Screenshots thực | Quan sát và thay đổi trước vòng tiếp theo |
| --- | --- | --- |
| Round 1 | 20 PNG trong `round-1/` | Mockup còn phẳng; login illustration quá thấp; Pages tab chưa hiển thị cây layers. Thêm perspective browser, nâng illustration, tách danh sách pages khỏi cây layers, thumbnail đo theo card. |
| Round 2 | 20 PNG chính + 10 PNG AI/local snapshot trong `round-2/` | Cây layer còn thưa, CTA mới chưa mở import ngay, disabled auth button quá nhạt. Giảm row padding, thêm import route intent, giữ nút navy với disabled semantics, bổ sung drawer focus handling. |
| Round 3 | 20 PNG chính + 10 PNG bổ sung trong `round-3/` | Chụp node Contact Now được chọn thực, readonly properties, AI tray và dashboard local snapshot; kiểm tra mobile. Giữ cụm “Nhanh hơn” không tách giữa hai từ, sửa drawer mở ngay từ click đầu, thu gọn hero/checklist/mockup mobile để thấy phần tiếp theo; chạy lại screenshots/test sau sửa. |

Widths: **1440, 1280, 1024, 768, 390**; test public pages thêm 320. Mỗi `metrics.json` chính ghi overflow, image loading và page errors; các capture chính không báo overflow/missing image/pageerror. Ảnh AI/dashboard có snapshot là evidence bổ sung, không được tính nhầm thành toàn bộ baseline fixtures của roadmap.

Screenshot tiêu biểu:

- [Landing trước](../layoutly-fe/visual-artifacts/before/landing-1440.png), [sau](../layoutly-fe/visual-artifacts/round-3/landing-1440.png).
- [Login trước](../layoutly-fe/visual-artifacts/before/login-1440.png), [sau](../layoutly-fe/visual-artifacts/round-3/login-1440.png).
- [Dashboard sau, snapshot thực](../layoutly-fe/visual-artifacts/round-3/dashboard-snapshot-1440.png), [empty state](../layoutly-fe/visual-artifacts/round-3/dashboard-1440.png).
- [Workspace trước](../layoutly-fe/visual-artifacts/before/workspace-1440.png), [sau + AI](../layoutly-fe/visual-artifacts/round-3/workspace-ai-1440.png).
- [Landing mobile](../layoutly-fe/visual-artifacts/round-3/landing-390.png), [workspace mobile](../layoutly-fe/visual-artifacts/round-3/workspace-ai-390.png).

## 5. Kiểm Tra Đã Chạy

Môi trường: Windows, Node **22.17.1**, npm **10.9.2**, Vite **5.4.21**, Playwright **1.64.0**, Chrome channel headless, Java **17.0.20**, Maven **3.9.15**. Snapshot smoke thực: `e3f67b94-7edd-4880-9782-ab7d562fb90d` (Studio), REST source, 55 cached assets. Không có PAT thật trong tests.

| Lệnh thực sự chạy | Kết quả |
| --- | --- |
| `node scripts/crop-reference.mjs` (FE) | exit 0, 6 vùng tách riêng |
| `node scripts/visual-review.mjs before` | exit 0, 20 PNG trước sửa |
| `node scripts/visual-review.mjs round-1` | exit 0, 20 PNG |
| `node scripts/visual-review.mjs round-2` | exit 0, 30 PNG gồm evidence bổ sung |
| `node scripts/visual-review.mjs round-3` | exit 0, 30 PNG; chạy lại sau chỉnh cuối |
| `npm run build` (FE) | exit 0, 1868 modules; build lại sau sửa UI cuối |
| `npm test` (FE) | exit 0, **34/34**, 4 files |
| `$env:LAYOUTLY_SMOKE_SNAPSHOT='e3f67b94-7edd-4880-9782-ab7d562fb90d'; npm run test:e2e` | exit 0, **16/16**, không skip; lần cuối 36.1s |
| `node node_modules/@playwright/test/cli.js test tests/e2e/editor.spec.js` | exit 0, 7/7 ở focused pass trước các assertions mở rộng |
| `node scripts/inspect-preview.mjs` cùng smoke env | exit 0, click chọn `A+ Studio · 2:325`, 1 outline |
| `mvn -o test` lần đầu | exit 1 vì JAVA_HOME của shell không hợp lệ; chưa chạy test |
| `$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.8-hotspot'; mvn -o test` (BE) | exit 0, **28/28**, 0 failures/errors/skips |
| HTTP export + `.NET ZipFile.OpenRead` | HTML 1,580,993 bytes / 57 entries; React 1,584,472 bytes / 66 entries |

Các lần chạy trung gian có fail thật: unit assertion headline cũ; e2e locator không exact bị trùng nhãn; thao tác CDP trong iframe zoom dùng tọa độ chưa scale. Đã sửa assertions, scoped dialog selectors và chuyển tọa độ click về vị trí chuột thực, không `force: true` hay skip test. Lần mở rộng drawer assertions tiếp theo cũng phát hiện locator Đóng layers trùng với toolbar; đã dùng exact và chạy lại full suite thành công. Lệnh thử truyền `--grep` qua npm/PowerShell trả “No tests found”, không tính là pass.

E2E kiểm tra snapshot deep link/reload, code HTML/CSS/React/JSON, selected outline, parent DOM access bị chặn, real ZIP download/header/size, Bridge readiness thực, dashboard filters/search/drawer, editor drawer click đầu, AI disabled, auth validation và mobile overflow. REST import contract probe/token validation dùng test doubles được ghi rõ, không được báo thành live Figma REST import. Backend Bridge/parser/generator tests đã chạy thực.

**Chưa chạy trong đợt này:** live Figma Desktop plugin sync, live REST import bằng PAT, clean install/build project React trong ZIP, plugin build/lint, full multi-page browser navigation fixture, font-pinned pixel mismatch/holdout/noise suite. Các endpoint/packaging/generator không bị đổi nhưng không suy diễn rằng mọi workflow ngoài phạm vi smoke đã được chứng minh.

## 6. Files Và Components

Files có trước đã sửa trong lần này: `.gitignore`; FE `src/App.jsx`, `src/components/public/PublicHeader.jsx`, `src/pages/LandingPage.jsx`, `src/pages/AuthPage.jsx`, `src/pages/PublicSite.jsx`, `tests/e2e/public-site.spec.js`, `tooling/tests/specs/public-ui.test.jsx`, `public/brand/README.md`.

Files/components mới (paths tương đối từ `layoutly-fe/`):

- `src/components/public/ProductShowcase.jsx`: HTML browser/mobile/code illustration và sneaker asset.
- `src/components/dashboard/DashboardSidebar.jsx`, `ProjectCard.jsx`.
- `src/components/workspace/WorkspaceToolbar.jsx` (và ToolButton), `LayersTree.jsx`, `PreviewCanvas.jsx`, `CodePanel.jsx`, `PropertiesPanel.jsx`, `AIChatPanel.jsx`, `ImportDialog.jsx`.
- `src/pages/DashboardPage.jsx`, `src/pages/WorkspaceEditor.jsx`.
- `src/hooks/useSnapshotWorkspace.js`, `src/lib/workspace.js`.
- `src/styles/visual.css`, `src/styles/editor.css`.
- `public/brand/sneaker.png`: ImageGen generate, transparent background, no reference-image input; prompt/provenance trong asset README.
- `scripts/crop-reference.mjs`, `scripts/visual-review.mjs`, `scripts/inspect-preview.mjs`.
- `tests/e2e/editor.spec.js`, `tooling/tests/specs/workspace.test.jsx`.
- Ignored generated artifacts: `visual-artifacts/reference/`, `before/`, `round-1/`, `round-2/`, `round-3/`, `exports/`; QA-only `visual-artifacts/index.html`.

Báo cáo mới: `docs/LAYOUTLY_PHASE1_VISUAL_FIX.md`. Không thêm dependency hoặc đổi package/lockfiles trong visual fix. Hash inventory đầu lượt gồm 139 source/config files: không mất file nào; backend và plugin sources giữ nguyên hashes. Dirty changes có trước vẫn còn; không reset, xóa, tự commit hoặc fetch/merge Git.

## 7. Giống Ảnh Và Còn Khác

Đã làm theo cấu trúc hai cột landing/auth, navy/yellow hierarchy, mockup layering, dashboard sidebar/grid, toolbar và ba vùng editor, readonly inspector và AI tray. Responsive thay bằng grid/drawer, không scale toàn bộ ứng dụng. Canvas fit-to-width vẫn là cách xem bản thiết kế fixed, không được gọi là responsive inference mới.

Còn khác có chủ đích hoặc chưa đạt parity:

- Shoe asset là hình riêng, không đúng sản phẩm gốc; Figma badge dùng icon hệ thống, không phải nguyên logo bitmap nhiều màu. Không thêm orbs/blobs hoặc fake user avatars/social proof.
- Auth không có OAuth buttons vì backend chưa cấu hình. Dashboard không có 8 dự án, avatar tài khoản, free-plan quota/payment/upgrade giả. Một local snapshot hoặc empty state không thể có density giống bộ dự án mẫu trong ảnh.
- Preview giữ Studio thật thay vì đổi thành sneaker demo; font/render fidelity của Studio vẫn thuộc các findings trong audit. Inspector chưa edit/lưu được; không giả sliders/input ghi thay đổi.
- AI chỉ là unavailable UI, không có model, streaming, apply patch, undo/history hoặc conversation storage.
- Panel widths, mockup angles, text wrapping/shadows đã review bằng screenshot nhưng chưa có oracle cùng viewport/data để đo pixel accuracy. Không tuyên bố giống 100% hoặc vượt G1 baseline.
- Chrome screenshot run không thay thế kiểm tra Safari/Firefox/touch device thật. Legacy fonts/fullscreen và các security/cache/geometry findings của audit chưa được hoàn tất ở đây.

## 8. Bước Tiếp Theo

Review các screenshots/crop riêng trước khi chốt visual acceptance. Muốn dashboard có nhiều card thực cần backend project inventory/ownership theo API migration review; muốn chỉnh property/AI phải có versioned patch/persistence contract và baseline đo trước. Tiếp tục T01–T05 của roadmap cho fixture/font/reference/repeat noise trước khi đổi rendering engine hoặc bật AI.
