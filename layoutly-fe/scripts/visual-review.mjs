import { chromium } from '@playwright/test'
import { mkdir, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const round = process.argv[2] || 'before'
if (!/^(before|round-[1-3])$/.test(round)) throw new Error('Invalid review round')
const directory = resolve('visual-artifacts', round)
await mkdir(directory, { recursive: true })
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const report = []
try {
  for (const width of [1440, 1280, 1024, 768, 390]) {
    const context = await browser.newContext({ viewport: { width, height: width < 600 ? 844 : 900 }, locale: 'vi-VN', reducedMotion: 'reduce' })
    const page = await context.newPage()
    const errors = []
    page.on('pageerror', error => errors.push(error.message))
    for (const [name, route] of [['landing', '/'], ['login', '/#/login'], ['dashboard', '/#/dashboard'], ['workspace', process.env.LAYOUTLY_SMOKE_SNAPSHOT ? `/?snapshotId=${encodeURIComponent(process.env.LAYOUTLY_SMOKE_SNAPSHOT)}` : '/#workspace']]) {
      await page.goto(`http://127.0.0.1:5174${route}`)
      await page.evaluate(() => document.fonts.ready)
      await page.waitForTimeout(name === 'workspace' ? 3000 : 600)
      if (name === 'workspace' && round === 'round-3' && process.env.LAYOUTLY_SMOKE_SNAPSHOT) {
        const preview = page.frameLocator('iframe[title="Snapshot preview"]')
        const target = preview.locator('.figma-node[data-figma-name="Contact Now"]').first()
        if (await target.count()) {
          const point = await target.evaluate(element => { const b = element.getBoundingClientRect(); return { x: b.x + b.width / 2, y: b.y + b.height / 2 } })
          const bounds = await page.locator('iframe[title="Snapshot preview"]').boundingBox()
          const scale = await page.locator('iframe[title="Snapshot preview"]').evaluate(element => new DOMMatrix(getComputedStyle(element).transform).a)
          await page.mouse.click(bounds.x + point.x * scale, bounds.y + point.y * scale)
          await page.waitForTimeout(200)
        }
      }
      const metrics = await page.evaluate(() => ({ overflow: document.documentElement.scrollWidth > innerWidth, missingImages: [...document.images].filter(image => !image.complete || !image.naturalWidth).length }))
      await page.screenshot({ path: resolve(directory, `${name}-${width}.png`), fullPage: true })
      report.push({ name, width, route, ...metrics, errors: [...errors] })
      if (name === 'workspace' && round !== 'before' && process.env.LAYOUTLY_SMOKE_SNAPSHOT) {
        await page.getByRole('button', { name: 'Mở hoặc đóng Layoutly AI' }).click()
        await page.screenshot({ path: resolve(directory, `workspace-ai-${width}.png`), fullPage: true })
        await page.goto('http://127.0.0.1:5174/#/dashboard')
        await page.waitForTimeout(1500)
        await page.screenshot({ path: resolve(directory, `dashboard-snapshot-${width}.png`), fullPage: true })
      }
    }
    await context.close()
  }
} finally { await browser.close() }
await writeFile(resolve(directory, 'metrics.json'), JSON.stringify(report, null, 2))
console.log(JSON.stringify({ round, directory, captures: report.length, failures: report.filter(item => item.overflow || item.missingImages || item.errors.length) }, null, 2))
