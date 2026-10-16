# Layoutly renderer — Phase A và quick wins Phase B

Ngày đo: 2026-10-16. Phạm vi đợt này: measurement/profiling, diagnostics, sửa CSS và thống nhất cách React tách trang qua flags. Giữ PostgreSQL, authentication, project ownership, AI workspace và giao diện hiện tại. Không có migration DB mới, commit/push, reset working tree hoặc thay golden.

## Kết quả và cách đọc

- Có pipeline chạy thật: Figma-shaped JSON → parser DesignNode → HTML/React ZIP → giải nén → npm install/ci độc lập → Vite build → Chromium Playwright → PNG, diff, overlay, JSON/HTML reports.
- 22 **synthetic probes tự biên soạn**, chia development/validation/holdout, bao phủ 20 nhóm yêu cầu. Đây là probes của từng capability, chưa phải bộ thiết kế Figma production phức tạp đã được xác nhận.
- 15 snapshot local hiện có đều thiếu Figma reference PNG. Corpus mới cũng không tự tạo reference. Điểm Figma fidelity của mọi fixture là `null / INSUFFICIENT_EVIDENCE`, không phải 99%.
- HTML/React parity là so hai đầu ra Layoutly; cùng có lỗi vẫn có thể khớp nhau. Geometry lấy bounds từ source; masks, rich text, rotation cần oracle riêng.
- Báo cáo cuối: [comparison.html](../.local-build/fidelity/verified/comparison.html), [comparison.json](../.local-build/fidelity/verified/comparison.json). Artifact local nằm trong `.local-build`, được gitignore.
- Snapshot gốc được kiểm kê SHA-256 trước/sau: 278 file không đổi. Danh sách thay đổi ban đầu và diff-stat lưu tại `.local-build/fidelity/initial-*`; mọi thay đổi trước phiên này được giữ lại.

## Audit cập nhật từ source hiện tại

Các tài liệu audit/roadmap cũ vẫn là tài liệu nền; các nhận định authentication/file-only trong đó đã được PostgreSQL/auth implementation thay thế. Đợt renderer này không sửa lại module đó.

| Đường đi | Hành vi đã có | Giới hạn được xác nhận |
|---|---|---|
| Figma REST / FigmaService | Lưu raw JSON, snapshot private; batch/retry/assets và cache theo token | Mạng Figma thật chưa được benchmark; E2E dùng upstream HTTP fixture |
| Bridge / BridgeImportService | Multipart metadata/design/assets, pairing token, root metadata, durable snapshots | Cùng đi qua IR parser; không tự có rich-text/grid/transform support |
| DesignNode / UINode / mapper | Giữ bounds, flex, constraints, sizing, base text style, linear gradient, radii, shadow, assets và prototype node links | Không có runs/paint stack/grid tracks/transforms/components đầy đủ; raw vẫn được giữ |
| Parser | HORIZONTAL/VERTICAL, wrap, padding/gap/alignment, HUG/FILL/FIXED, min/max, absolute overlays | AUTO_GRID chỉ là phân loại; lineHeight chủ yếu lineHeightPx; metadata chưa hỗ trợ không được biểu diễn trong legacy DTO |
| CSS | Absolute freeform, flex, constraints, gradient, corners, clipping, image render bounds | Negative gap không hợp lệ, fixed flex children bị shrink, SCALE parent zero; ba lỗi có opt-in corrections trong đợt này |
| Typography | Base family/weight/size, px line-height, spacing/alignment/case, Unicode | Chưa package fonts/font manifest; có fallback; text shadows còn dùng box-shadow; mixed runs/paragraph/truncation chưa đầy đủ |
| Assets | Durable files, concurrency download giới hạn 6, relative ZIP paths, embedded private preview bytes | Chưa có manifest provenance đầy đủ, progressive final-state, recovery toàn bộ missing assets; rasterized image-frame có thể double-paint children |
| Root/page | Preview chọn screen theo target width; HTML AUTO có policy tách screen/ghép continuous sections | React mặc định trước đây không xử lý CANVAS đầu vào như HTML; opt-in page consistency sửa lỗi này. Root/crop chứa overflow vẫn cần RenderPlan |
| Preview/cache | Sandbox iframe, owner-authorized embedding, cancel stale fetches; source/assets có persistence | Restore còn parse raw; HTML/CSS generation chưa cache versioned; embed asset map rộng hơn subtree; chưa SSE/progressive state |
| Frontend | Có AbortController, sandbox, private asset resolution; revision preview lazy module | Tree vẫn flatten/render toàn bộ, panels/code còn eager ở một số đường; chưa virtualization/lazy orchestration đầy đủ |
| Export/revisions | ZIP HTML/React, accepted revisions thuộc workspace; generator không sửa accepted files trong đợt này | Chưa có shared RenderPlan; font bundling và native fixed-root/crop oracle còn thiếu |
| PostgreSQL/security | Users/sessions/projects/versions/assets/exports/conversations, owner filtering, opaque HttpOnly sessions, CSRF | Giữ nguyên schema. Endpoint diagnostics mới chịu cùng snapshot-owner interceptor; cross-user integration test đã mở rộng |

Absolute cho freeform/overlay là chủ ý; chưa có bằng chứng để đổi hàng loạt sang flex/grid. Transform scale hiện tại được báo là `LEGACY_SCALED_FIXED`, không được gọi là responsive reflow.

## Measurement contract và gates

Source ở `layoutly-fe/tests/fidelity/fixtures`, manifest v1 ghi split, license/provenance, raw path, root, revision, viewport, expected pages/navigation/layout, assets, fontManifest và reference.

Browser: Playwright **1.64.0**, Chromium **156.0.8078.4 / revision 1248**, deviceScaleFactor 1, vi-VN, Asia/Bangkok, light/reduced-motion. Không dùng Chrome tự cập nhật. OS Windows 11 build 26300 được ghi lại; OS/font chưa được đóng image container. Không bundle hoặc tải font thương mại; thiếu loaded font family/weight/style được báo rõ. Arial fallback chỉ được hash để ghi môi trường, không phân phối lại.

Các metric được thu:

- PNG pixel diff, includeAA=true, pixel threshold 0.1; SSIM bezkrovny. Gate parity/reference: diff ratio ≤0.01 và SSIM ≥0.99. Threshold version 1, không nâng trong quá trình sửa.
- Geometry từng node so source bounds, scale/offset được ghi rõ; gate 1px. Root dimensions có metric riêng để tránh bounds con tràn làm sai crop mà vẫn được tính đúng.
- Text content, computed font/weight/size/line-height, số dòng; typography parity giữa HTML/React. Figma wrapping oracle được báo unavailable nếu thiếu reference/layout dữ liệu.
- Font availability không chỉ dựa vào `document.fonts.ready`; kiểm tra loaded faces và weight/style. Glyph coverage/font manifest hoàn chỉnh còn là công việc tiếp theo.
- Missing assets, source/HTML/React asset hashes và provenance cho asset fixture; runtime errors và request failures.
- HTML/React parity, expected page counts và navigation/back/forward khi bật page consistency.
- Các width 1440/1280/1024/768/430/390/375 được quan sát ở responsive probe; kết quả ghi scale, `reflowVerified=false`.
- Thời gian parse+normalize gộp, HTML+ZIP, React+ZIP; browser stable/paint nếu có entry, long tasks và observed heap. Observed heap **không phải peak memory**; peak ghi null.

PNG reference chỉ được đọc khi manifest có đúng root/revision/scale=1 và hash đã review. So sánh không resize reference; không có lệnh tự update golden. Run mới và fixture save đều từ chối ghi đè thư mục hiện hữu. Không dùng screenshot làm website background.

Playwright collection pass không đồng nghĩa fidelity pass. Missing reference/font/assets, unsupported diagnostics, runtime, geometry/root/parity lỗi đều ngăn eligibility. Full true-Figma visual acceptance vẫn chưa được xác minh vì không có reference hợp lệ.

## Quick wins có bằng chứng

| Lỗi | Baseline | Sau sửa |
|---|---|---|
| Negative spacing, nowrap | Child thứ 2 lệch 20px, child thứ 3 lệch 40px | Sai lệch tối đa 0px; dùng gap=0 và margin âm đúng trục, bỏ hidden/absolute siblings khỏi flow |
| Fixed flex children | Sai lệch tối đa khoảng 86.275px tại scale đang đo | Dưới 0.001px; fixed main-axis có flex-shrink:0, không đổi FILL |
| SCALE parent zero | Chia cho 0 khi xuất percentages | Fallback finite source pixels; partial parent start/size được guard trong capability này |
| CANVAS nhiều màn hình | Commerce parity pixel diff 65.826%; navigation 58.389%; React gộp screens | Pixel diff 0% và SSIM 1 ở hai probe; số trang, links và back/forward được assert thật |
| Lưu fixture trùng tên | Có thể ghi đè raw/IR/assets của case đã review | Từ chối tên đã tồn tại, kể cả collision sau sanitize; test giữ nguyên reference bytes |

Wrapped negative spacing vẫn unsupported: opt-in xuất gap=0 hợp lệ, giữ diagnostic yêu cầu review; chưa suy luận overlap giữa nhiều flex lines. Chưa tuyên bố support toàn bộ Auto Layout.

Screenshot: [negative-gap trước](../.local-build/fidelity/before/negative-gap/measurement/html.png) / [sau](../.local-build/fidelity/verified/negative-gap/measurement/html.png), [fixed-flex trước](../.local-build/fidelity/before/fixed-flex/measurement/html.png) / [sau](../.local-build/fidelity/verified/fixed-flex/measurement/html.png). Mỗi case có report HTML riêng, parity diff/overlay và before/after diff.

Fidelity Figma của **từng fixture dưới đây đều chưa chấm được**; xem comparison JSON để có geometry/parity riêng: landing, commerce, dashboard, form, nested-auto, grid, negative-gap, overlays, mixed-text, effects, masks, vectors, image-children, responsive, navigation, components, medium, large, transforms, unicode, missing, fixed-flex. Grid/masks/transforms khớp bounding boxes không chứng minh appearance đúng.

## Profiling API thật

[API profile JSON](../.local-build/fidelity/api-preview-profile.json), 20 mẫu/fixture, PostgreSQL `layoutly_renderer_e2e`, Bridge import thật vào snapshot riêng, endpoint có authentication, sandbox iframe `allow-scripts` không `allow-same-origin`.

| Fixture | API p50 / p95 (ms) | Browser stable sau response p50 / p95 (ms) |
|---|---:|---:|
| 3 nodes | 14.7 / 20.7 | 83.6 / 86.6 |
| 500 nodes | 18.7 / 25.5 | 129.1 / 187.5 |
| 2.000 nodes | 26.8 / 30.1 | 235.9 / 271.3 |

Đây là cấu trúc local không có assets/fonts; có overhead Playwright transport. Không đại diện cho asset-heavy designs, cache HTML hay import qua mạng Figma. Hai percentile không cộng thành percentile end-to-end. Chưa dùng số này để nghiệm thu mục tiêu cached preview <1/3/8 giây trên thiết kế thực.

Endpoint `/api/snapshots/{id}/preview` nay trả `Server-Timing` thật cho select, assets, embed, html, css, total preview; root/node-count, missing-assets và trạng thái asset-cache present/miss. Assets stage có thể bao gồm network nếu chưa có dữ liệu; không suy diễn đó là generation cache hit. Không có timer tiến trình giả.

Browser-export before/after p50/p95 và stage data nằm trong comparison JSON của từng fixture. Chỉ công bố p95 khi có ≥20 mẫu; các fixture nhỏ khác chỉ có một mẫu nên p95=null. FCP có thể null nếu browser không cung cấp entry; stable được định nghĩa là fonts/images ready + hai animation frames, không giả thành FCP. p95 có dao động giữa các run, chưa chứng minh cải thiện latency; chưa triển khai renderer cache hoặc progressive preview.

| Local exported HTML, 20 mẫu | Baseline p50 / p95 (ms) | Verified p50 / p95 (ms) |
|---|---:|---:|
| fixed-flex | 47.7 / 49.2 | 47.3 / 48.7 |
| medium 500 nodes | 47.0 / 62.3 | 47.3 / 52.2 |
| large 2.000 nodes | 64.7 / 92.1 | 64.1 / 85.6 |

Các run trung gian có p95 cao hơn baseline; giữ kết quả trong thư mục `after` và `after-pages`. Không chọn riêng run nhanh để kết luận speedup. Mục tiêu của quick wins này là correctness.

## Validation và file thay đổi của đợt renderer

Production:

- `CssGeneratorService`: corrections opt-in.
- `ReactGeneratorService`, `DesignRootSelector`: page policy opt-in.
- `RenderDiagnosticsService`: sidecar schemaVersion 1, nodeId/code/reason/fallback, không sửa public IR/raw.
- `SnapshotController`: diagnostics endpoint + Server-Timing/counters, giữ API body và sandbox/authorization.
- `FixtureService`: bảo vệ fixture khỏi overwrite.
- `application.properties`: hai flags mặc định false.

Tooling/tests:

- `RendererCorpusExportTest`, `LegacyRendererAuditTest`, `RenderDiagnosticsServiceTest`, `FixtureServiceTest`; mở rộng CSS/React/controller/PostgreSQL authorization tests.
- `tests/fidelity/*`, 22 raw cases/manifest/SVG/license notes; `playwright.fidelity.config.js`, `playwright.renderer-api.config.js`, `preview-profiling.spec.js`.
- `scripts/Test-Renderer.ps1`, package.json/package-lock: pinned pngjs 7.0.0, pixelmatch 7.1.0, ssim.js 3.5.0.

Đã chạy frontend 52 tests và Vite build; comparator/evidence unit tests; toàn bộ backend JUnit với PostgreSQL native 16.4 thực (Docker không sẵn nên không gọi đó là Testcontainers run); opt-in exporter và read-only legacy audit riêng; Playwright corpus trước/sau và từng React ZIP isolated build. Bốn E2E saved-projects đã pass (Register/Login/REST import/Save/Logout/Login/Reopen/Export ZIP, CRUD, cross-user, Bridge asset + accepted revision preview), profiling API pass riêng. Kết quả/log đợt cuối ở `.local-build/fidelity/verified-run.log`, `backend-verified.log`, `frontend-*`, `api-e2e.log`, `api-profile.log`.

Đếm lần cuối: backend 93 discovered, **92 passed**, một exporter tooling opt-in không chạy trong full suite (đã chạy riêng trong pipeline); gồm **10 PostgreSQL integration tests**. Comparator/evidence **7 passed**. Corpus **22 passed** ở mỗi run before/after/after-pages/verified; mỗi run có **22 React ZIP isolated clean builds**. Gate comparison cuối: **0 geometry/parity regressions**; 22 case Figma fidelity vẫn không đủ evidence. Legacy audit đọc cả **15 snapshot thật**, 39–1.025 nodes/source, parse/generate smoke pass, không sửa file gốc: [legacy-audit.json](../.local-build/fidelity/legacy-audit.json).

Runner PowerShell được sửa để native stderr warning không bị hiểu thành build failure; exit code khác 0 vẫn fail. Run verified có Playwright `.last-run.json` passed và đủ 22 reports, comparison được chạy sau đó; log wrapper bị ngắt bởi warning trước bản sửa được giữ nguyên. Không xóa log fail hoặc đổi threshold để làm đẹp kết quả.

Các exporter/audit JUnit là tooling opt-in vì cần output mới hoặc dữ liệu local; không thay thế hay tắt domain/integration tests. E2E REST dùng HTTP fixture; chưa gọi live Figma PAT hoặc SMTP.

## Chạy lại và rollout

Từ root, sau `npm ci` trong `layoutly-fe`, Java 17/Maven đã sẵn:

```powershell
./scripts/Test-Renderer.ps1 -RunName my-baseline
./scripts/Test-Renderer.ps1 -RunName my-candidate -CssCorrections -PageConsistency -Baseline my-baseline
```

Runner tạo run mới, cài Chromium theo lock, giải nén ZIP bằng exporter, install/ci mỗi React project độc lập, dùng cùng export lockfile của baseline, build và đo. Cần mạng cho lần cài dependency/browser đầu. Không chạy lại cùng tên để tránh mất evidence.

Hai capability flags trên backend:

```properties
LAYOUTLY_RENDERER_CSS_CORRECTIONS=true
LAYOUTLY_RENDERER_PAGE_CONSISTENCY=true
```

Chỉ backend E2E 18081 bật trong đợt đo. Backend giao diện 8080 được mở lại với cùng PostgreSQL/schema/data local và flags false. Rollback bằng đặt flags false rồi restart; không có DB migration hay sửa saved revision. UI tại http://localhost:5173 vẫn hoạt động.

Comparison báo geometry/parity regressions; timings thô được giữ để review, chưa đặt performance regression gate có confidence. Hai root/multipage defects cũ được giữ nguyên trong baseline, không sửa golden để giảm diff. Legacy default chưa đổi; chưa đạt gate phát hành renderer mới.

## Tiếp theo

Hoàn thiện Phase B với font manifest/license/glyph coverage và export packaging, asset subtree provenance, root/crop oracle, generation cache scoped theo snapshot/root/revision/renderer/options, profiling asset-heavy input. Sau đó Phase C tạo shared RenderPlan shadow-mode cho flex/grid/geometry/text; Phase D mới mở rộng masks/runs/transforms/components. Responsive reflow và multipage hardening phải có oracle/gates riêng, không đổi tên transform scale thành reflow.

Tài liệu công cụ tham khảo: [Playwright browsers](https://playwright.dev/docs/browsers), [visual comparison environment](https://playwright.dev/docs/test-snapshots), [pixelmatch](https://github.com/mapbox/pixelmatch), [SSIM.js](https://github.com/obartra/ssim). Các giới hạn và kết quả bên trên đến từ source/test local, không phải tuyên bố của các thư viện.
