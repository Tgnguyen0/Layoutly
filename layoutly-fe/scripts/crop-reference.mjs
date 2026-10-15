import { chromium } from '@playwright/test'
import { readFile, mkdir, writeFile } from 'node:fs/promises'
import { resolve } from 'node:path'

const source = resolve('../design-reference/layoutly-ui.png')
const directory = resolve('visual-artifacts/reference')
await mkdir(directory, { recursive: true })
const bytes = await readFile(source)
const regions = { landing: [12, 32, 786, 473], login: [815, 32, 708, 450], dashboard: [14, 550, 785, 381], 'dashboard-sidebar': [14, 550, 169, 463], workspace: [813, 495, 711, 427], ai: [194, 941, 1331, 73] }
const browser = await chromium.launch({ channel: 'chrome', headless: true })
try {
  const page = await browser.newPage()
  for (const [name, rect] of Object.entries(regions)) {
    const base64 = await page.evaluate(async ({ data, rect }) => {
      const image = new Image()
      image.src = `data:image/png;base64,${data}`
      await image.decode()
      if (image.width !== 1536 || image.height !== 1024) throw new Error('Reference dimensions changed; review crop coordinates')
      const [x, y, width, height] = rect
      const canvas = document.createElement('canvas')
      canvas.width = width; canvas.height = height
      canvas.getContext('2d').drawImage(image, x, y, width, height, 0, 0, width, height)
      return canvas.toDataURL('image/png').split(',')[1]
    }, { data: bytes.toString('base64'), rect })
    await writeFile(resolve(directory, `${name}.png`), Buffer.from(base64, 'base64'))
  }
} finally { await browser.close() }
await writeFile(resolve(directory, 'regions.json'), JSON.stringify({ source: '../design-reference/layoutly-ui.png', dimensions: [1536, 1024], regions }, null, 2))
console.log(`Cropped ${Object.keys(regions).length} reference regions to ${directory}`)
