# Layoutly workspace redesign — báo cáo kiểm tra

Ngày: 15/10/2026. Thực hiện trực tiếp trên dự án hiện tại; chưa commit hoặc push.

Giao diện đã chuyển sang toolbar trên, sidebar Pages/Layers trái, preview giữa, code dưới và AI bên phải. Giữ pipeline snapshot/Figma hiện có. Không dùng ảnh tham chiếu làm background hoặc nội dung preview. MNTN trong screenshot lấy từ snapshot thật có sẵn trên máy.

## Đối chiếu ảnh tham chiếu

| Khu vực | Kết quả đo ở 1536 × 1024 |
|---|---|
| Toolbar | cao 58px |
| Sidebar | rộng 225px |
| Preview | x=233, y=68, rộng 862px, cao 660px |
| Code panel | x=233, y=734, rộng 862px, cao 286px; có thể resize/collapse |
| AI panel | x=1103, y=58, rộng 430px; resize 360–520px |
| Composer | x=1116, y≈855, rộng 404px, cao 122px |

Màu editor: nền #FAFAF7, surface trắng, navy #102638/#123B52, vàng #F5B71B, selected #FFF4D6, border #E5E9EA. Inter và Lucide; border mảnh, radius 8–12px, shadow nhẹ. Không đổi màu nội dung website Figma.

Giống cấu trúc tham chiếu: toolbar và breadcrumb, tabs gạch chân vàng; sidebar có search và selection; preview trên/code dưới; AI header/model/tabs/welcome/chips; bubble nhỏ; revision card; composer cố định cuối panel.

Khác tham chiếu: website MNTN gốc có màu tối, ảnh núi và nội dung khác với bản minh họa. Danh sách Pages và Components chỉ hiển thị dữ liệu thật. AI chưa cấu hình nên không có chat/draft giả trong workspace thật. Không thêm Share giả; Undo/Redo được disable và có tooltip. Preview có thêm thanh điều khiển inspect/interact, zoom, reload và full preview. Code explorer nằm trong code panel giữa. Chưa có phép đo pixel diff đối với toàn bộ ảnh tham chiếu; không tuyên bố pixel-perfect.

## Screenshot qua ba vòng

- [Vòng 1 — desktop](../layoutly-fe/visual-artifacts/workspace-redesign/round-1/workspace-ai-1536.png): dựng layout ban đầu; phát hiện capture trước khi background image tải xong và script QA chưa xử lý code tab ẩn trên mobile.
- [Vòng 2 — desktop](../layoutly-fe/visual-artifacts/workspace-redesign/round-2/workspace-ai-1536.png): đợi tài nguyên thật; sửa model đang cấu hình không có trong catalog và spacing sidebar.
- [Vòng 3 — desktop](../layoutly-fe/visual-artifacts/workspace-redesign/round-3/workspace-ai-1536.png): folder explorer, chip hai cột, ellipsis component, responsive và chiều cao canvas hoàn chỉnh.
- [Vòng 3 — mobile AI](../layoutly-fe/visual-artifacts/workspace-redesign/round-3/workspace-ai-390.png).
- [Vòng 3 — mobile code](../layoutly-fe/visual-artifacts/workspace-redesign/round-3/workspace-390.png).
- [Draft card với fixture kiểm thử](../layoutly-fe/visual-artifacts/workspace-redesign/draft-card-test-fixture-1536.png): screenshot từ giao diện thật trong E2E production, với API response fixture được ghi rõ. Không phải kết quả của provider AI đang chạy.
- [Metrics vòng 3](../layoutly-fe/visual-artifacts/workspace-redesign/round-3/metrics.json): 1536, 1280, 1024, 768 và 390px, không tràn ngang và không có pageerror.

Trình duyệt tích hợp không có browser kết nối. Visual QA dùng Chrome headless qua Playwright đã có trong dự án. Đã trực tiếp xem các ảnh desktop và mobile để kiểm tra bố cục.

## Hành vi và API

Đã nối API hiện có dưới `/api/snapshots/{snapshotId}/ai`: `status`, `chat`, `revisions`, `accepted`, `revisions/{revisionId}/diff`, `.../accept`, `.../reject`, `export`.

Bổ sung `ai/accepted` để khôi phục đúng revision được chấp nhận gần nhất ngay cả khi draft đó được tạo trước draft khác. Backend lưu con trỏ `accepted_revision.json`, không đổi thứ tự lịch sử. Bổ sung read-only `ai/files` và `ai/file?path=...`, có kiểm tra revision tồn tại và WorkspacePathGuard. Chỉ dùng để đọc workspace nguồn thật; không thay agent loop, provider hay patch engine. Bổ sung tham số `nodeId` cho preview snapshot để chọn page/root từ sidebar.

Accept cập nhật accepted revision, file explorer, code và preview. Export toolbar dùng AI export của accepted revision, không quay về code DesignNode gốc. Draft có Before/After, diff và reject. Khi reject bản đang preview, quay về accepted source. Các phản hồi cũ được bỏ qua khi chuyển snapshot/tab. Đóng panel giữ nguyên prompt/chat trong phiên đang mở.

Preview revision React dùng compiler JSX Sucrase được tải khi cần; runtime React/ReactDOM/Router được bundle cục bộ bằng esbuild trước dev/build. Mã project chỉ chạy trong iframe `sandbox="allow-scripts"`, không có `allow-same-origin`. Router dùng MemoryRouter trong iframe vì `about:srcdoc` không hỗ trợ BrowserRouter/HashRouter URL. Full preview tiếp tục bọc mã trong iframe sandbox. Preview lỗi hiển thị lỗi thay vì quay về thiết kế cũ. Runtime hỗ trợ React, ReactDOM và React Router hiện có; dependency bổ sung ngoài danh sách sẽ báo lỗi rõ, không tự tải package/CDN hoặc chạy lệnh từ AI. Có hỗ trợ đọc HTML/CSS revision hiện có; không bổ sung cơ chế chuyển đổi workspace giữa framework ở backend.

Markdown chat được xử lý thành React text/code/strong, không render HTML từ AI. Suggested prompts điền nội dung vào textarea, không tự gửi. Enter gửi; Shift+Enter xuống dòng; có context node, busy, retry. Composer không có attachment giả.

## Trạng thái AI và phần backend chưa có

Backend hiện trả `available=false`; chưa có provider key cấu hình. Không gửi yêu cầu model thật hoặc thêm cấu hình key. Live provider test tự bỏ qua vì thiếu OPENROUTER_API_KEY. Model catalog và active model lấy trực tiếp từ backend; model/provider không khả dụng bị disable.

API status hiện chỉ chứng minh cấu hình, không probe provider/model. UI vì vậy dùng Not Configured hoặc Configured · unverified; chỉ dùng Provider Reachable/Model Ready nếu backend cung cấp bằng chứng tương ứng. Không hiển thị Connected chỉ vì có key. Validation hiện tại là syntax/structure checks, nên card ghi Static checks passed, không ghi Build passed.

Backend chưa cung cấp streaming, cancellation hay tool-execution events. UI chỉ hiển thị trạng thái đang chờ request thực tế, không mô phỏng năm bước bằng timeout. Chưa có Ollama provider trong backend hiện tại; UI đọc provider/model theo API và không hardcode OpenRouter hay trạng thái local AI giả. Không thêm Share khi backend chưa hỗ trợ.

## Kiểm thử

| Kiểm tra | Kết quả |
|---|---|
| `npm run build` | Pass; bao gồm runtime preview cục bộ |
| `npm test` | 48/48 pass, 5 test files |
| Toàn bộ Playwright dev với MNTN snapshot thật | 19/19 pass |
| Playwright production | 3/3 pass |
| `mvn -B -q test` với Java 17 | 53/53 pass, 0 failure/error |
| Visual QA | 3 vòng; vòng 2/3 đủ 5 kích thước |

E2E kiểm tra source React thật, iframe isolation, reload, node selection/highlight, code tabs, snapshot/import, tải ZIP thật, dashboard, responsive, panel open/close, giữ prompt sau close, resize bằng bàn phím và kéo chuột, composer trên viewport 390×568. Các test API fixture riêng kiểm tra model/context gửi chat, draft/diff, Before/After, accept, refresh code/preview, restore accepted revision và export đúng revision. Reject/error/retry/Markdown được kiểm tra trong unit tests. Không thay golden screenshots.

Logs nằm trong `layoutly-fe/visual-artifacts/workspace-redesign/`: `build.log`, `unit-tests.log`, `e2e-tests.log`, `production-e2e.log`, `backend-tests.log`.

## File thay đổi

Frontend đã sửa:

- `src/pages/WorkspaceEditor.jsx`
- `src/components/workspace/WorkspaceToolbar.jsx`
- `src/components/workspace/AIChatPanel.jsx`
- `src/components/workspace/CodePanel.jsx`
- `src/components/workspace/LayersTree.jsx`
- `src/components/workspace/PreviewCanvas.jsx`
- `src/hooks/useSnapshotWorkspace.js`
- `src/lib/api.js`
- `package.json`, `package-lock.json`
- `tests/e2e/editor.spec.js`, `tooling/tests/specs/ai-agent.test.jsx`

Frontend đã tạo:

- `src/components/workspace/WorkspaceFileExplorer.jsx`: hierarchy từ danh sách file thật, expand/collapse và selection.
- `src/styles/workspace.css`: design tokens/layout/responsive chỉ áp dụng editor.
- `src/lib/revisionPreview.js`: đọc và chuẩn bị nguồn revision thật cho iframe.
- `src/lib/preview-runtime.js`: runtime React trong sandbox.
- `scripts/build-preview-runtime.mjs`: bundle runtime cục bộ cho dev/production.
- `scripts/workspace-review.mjs`: chụp và đo layout ở năm kích thước.
- `tests/e2e/ai-workspace.spec.js`, `playwright.production.config.js`.

Component mới trong các file hiện có: `DraftRevisionCard`, `DiffDialog`, `MessageContent`, `HighlightedCode`. CSS không đi vào iframe. `PropertiesPanel` giữ nguyên component hiện có, được chuyển sang drawer theo toolbar tab.

Backend đã sửa:

- `controller/AiWorkspaceController.java`: API read-only source.
- `service/ai/ProjectWorkspaceService.java`: đọc asset bytes qua path guard.
- `service/ai/RevisionManager.java`: lưu và đọc con trỏ accepted revision, giữ tương thích dữ liệu cũ.
- `src/test/java/.../service/ai/RevisionManagerTest.java`: regression test accept draft cũ sau draft mới rồi tạo lại RevisionManager.
- `controller/SnapshotController.java`: chọn page/root preview, giữ overload cũ cho tương thích.
- `src/test/java/.../controller/AiWorkspaceControllerTest.java`: đọc đúng revision và từ chối truy cập nguồn không hợp lệ.

Root: `.gitignore` bỏ qua kết quả `layoutly-fe/test-results-production/` và bundle `layoutly-fe/public/preview-runtime.js` được tự tạo trước dev/build. Báo cáo này: `docs/workspace-redesign-review.md`.
