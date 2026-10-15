import { chromium } from '@playwright/test'
const browser = await chromium.launch({ channel: 'chrome', headless: true })
try {
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })
  await page.goto(`http://127.0.0.1:5174/?snapshotId=${encodeURIComponent(process.env.LAYOUTLY_SMOKE_SNAPSHOT)}`)
  const iframe = page.frameLocator('iframe[title="Snapshot preview"]')
  const text = iframe.locator('[data-figma-type="TEXT"]').first()
  await text.waitFor()
  await page.waitForTimeout(1000)
  const box = await text.boundingBox()
  const local = await text.evaluate(el => { const b = el.getBoundingClientRect(); return { x: b.x, y: b.y, width: b.width, height: b.height, hit: document.elementFromPoint(b.x + b.width / 2, b.y + b.height / 2)?.id } })
  console.log({ box, local, iframe: await page.locator('iframe').boundingBox() })
  const bounds = await page.locator('iframe').boundingBox()
  const scale = await page.locator('iframe').evaluate(element => new DOMMatrix(getComputedStyle(element).transform).a)
  await page.mouse.click(bounds.x + (local.x + local.width / 2) * scale, bounds.y + (local.y + local.height / 2) * scale)
  await page.waitForTimeout(500)
  console.log({ selection: await page.locator('.we-selected-name').allTextContents(), outline: await iframe.locator('.layoutly-selected').count() })
} finally { await browser.close() }
