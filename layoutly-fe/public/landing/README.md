# Layoutly product imagery and samples

`workspace.png` and `dashboard.png` are screenshots of the actual Layoutly application. An isolated local PostgreSQL schema and a real test account were used to import and save the four original fixture projects. No personal account or private Figma project was used.

`showcase-atlas.png`, `showcase-commerce.png`, `showcase-dashboard.png`, and `showcase-forms.png` are screenshots of actual backend-generated previews after Bridge import. The Figma-shaped inputs are authored in `tests/support/marketing-fixtures.mjs`; they are interface examples, not customer projects. Forma dashboard values are explicitly marked as illustrative data.

The mountain landscape, chart and architectural SVG illustrations and JSON fixtures are original authored test material released under CC0-1.0. The sneaker image is the existing original illustration documented in `../brand/README.md`. No third-party collage or supplied reference image is embedded as a page background. The Layoutly UI itself remains subject to the project's existing rights.

`atlas-figma.json` is the authored input fixture. `atlas-code.json` contains the actual generated HTML/CSS/React. The page displays formatted excerpts without changing the downloaded source. `atlas-html.zip` and `atlas-react.zip` are actual export responses from Layoutly, not hand-built replacement projects. The React ZIP was independently unpacked, installed with a dependency lock and successfully built with Vite.

These previews are not PNG reference exports from Figma and must not be used as Figma fidelity goldens. The landing page makes no fidelity score, customer-count or testimonial claims from these examples.

To deliberately refresh product imagery, run a separate test backend on port 18082 with an isolated data/schema configuration, then from `layoutly-fe`:

```powershell
$env:LAYOUTLY_API_TARGET='http://127.0.0.1:18082'
$env:LAYOUTLY_CAPTURE_MARKETING_ASSETS='1'
npx playwright test --config playwright.landing.config.js
Remove-Item Env:LAYOUTLY_CAPTURE_MARKETING_ASSETS
```

The capture helper is outside the ordinary E2E directory so normal regression runs do not replace product screenshots. Review refreshed files before using them. Browser dependencies are pinned by the existing Playwright lock.
