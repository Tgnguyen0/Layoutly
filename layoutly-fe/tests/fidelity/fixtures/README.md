# Renderer corpus v1

22 original authored **synthetic probes**, CC0-1.0, spanning the 20 requested feature categories. These are not Figma-exported goldens or proof of production-design support. `create-corpus.mjs` creates source JSON only and refuses to replace existing cases. Split assignments are fixed in `manifest.json`; tune development cases, use validation and holdout for confirmation.

No Figma reference PNG or font redistribution was supplied. `reference: null` and `fontManifest: []` are deliberate. Missing reference/fonts/assets or unsupported features block fidelity eligibility. A successful measurement test means the collection/runtime gates passed. Inspect each report's eligibility and parity separately.

To introduce a reviewed reference, set `reference` to an object with `path` (relative to this folder), `sha256`, `rootId` (the displayed screen), `sourceRevision`, and `scale: 1`. PNG dimensions must match the viewport; comparisons never resize goldens. Use a fresh run directory after reviewing manifest changes. Never replace a reference to make a failing test pass.

Future font manifest entries should identify `family`, `weight`, `style`, `path`, `sha256`, `license`, and glyph coverage. Current exporters do not package fonts; the measurement runner reports missing loaded faces, including weight/style, and does not provision fonts behind the exporter. Browser fallback cannot count as verified font availability.

The two `triangle.svg` files are original test assets. The image-frame case intentionally records subtree provenance and a duplicate-children diagnostic. The missing-assets case intentionally omits a source image.

License applies only to the synthetic JSON/SVG data in this corpus: to the extent possible under law, the authors waive copyright and related rights under [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/). No third-party Figma designs or fonts are included.
