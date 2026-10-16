import { chromium } from '@playwright/test'
import { mkdir, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'
const round = process.argv[2] || 'round-1'
const out = resolve('visual-artifacts', 'workspace-redesign', round)
await mkdir(out, { recursive: true })
const browser = await chromium.launch({ channel: 'chrome', headless: true })
const results = []
try {
  for (const width of [1536, 1280, 1024, 768, 390]) {
    const page = await browser.newPage({ viewport: { width, height: width === 390 ? 844 : 1024 }, locale: 'vi-VN', reducedMotion: 'reduce' })
    const errors = []
    page.on('pageerror', err => errors.push(err.message))
    await page.goto('http://localhost:5173/?snapshotId=a93ff7f5-c887-48cf-8990-2c3b735fa794')
    await page.locator('iframe[title="Snapshot preview"]').waitFor()
    await page.frameLocator('iframe[title="Snapshot preview"]').locator('.figma-node').first().waitFor({ timeout: 30000 })
    await page.evaluate(() => document.fonts.ready)
    await page.locator('.we-file-explorer button').first().waitFor({ state:'attached' })
    await page.frameLocator('iframe[title="Snapshot preview"]').locator('body').evaluate(async () => { const urls=[...new Set([...document.querySelectorAll('*')].flatMap(el=>[...getComputedStyle(el).backgroundImage.matchAll(/url\(["']?([^"')]+)["']?\)/g)].map(m=>m[1])))]; await Promise.all([...document.images].map(image=>image.decode().catch(()=>{}))); await Promise.all(urls.map(src=>new Promise(resolve=>{const image=new Image();image.onload=image.onerror=resolve;image.src=src}))) })
    if (width < 1440) await page.getByRole('button', { name: 'Mở hoặc đóng Layoutly AI', exact: true }).click()
    await page.getByRole('heading', { name: 'Xin chào! Tôi là Layoutly AI.' }).waitFor()
    await page.screenshot({ path: resolve(out, `workspace-ai-${width}.png`) })
    const metrics = await page.evaluate(() => {
      const box = selector => { const r = document.querySelector(selector)?.getBoundingClientRect(); return r ? { x:r.x,y:r.y,width:r.width,height:r.height } : null }
      return { overflow:document.documentElement.scrollWidth>innerWidth, toolbar:box('.we-toolbar'), sidebar:box('.we-layers'), preview:box('.we-canvas'), code:box('.we-code-panel'), ai:box('.we-ai'), composer:box('.we-composer') }
    })
    results.push({ width, errors, ...metrics })
    if (width < 1440) { await page.getByRole('button', { name: 'Đóng AI Chat', exact:true }).click(); if (width < 900) await page.getByTitle('Mở hoặc thu gọn code', { exact:true }).click(); await page.screenshot({ path:resolve(out, `workspace-${width}.png`) }) }
    await page.close()
  }
} finally { await browser.close() }
await writeFile(resolve(out,'metrics.json'), JSON.stringify(results,null,2))
console.log(JSON.stringify({round, results},null,2))
