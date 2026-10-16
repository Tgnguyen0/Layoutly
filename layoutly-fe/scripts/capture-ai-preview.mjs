import { chromium } from '@playwright/test'
import { resolve, relative, dirname } from 'node:path'
import { mkdir, stat } from 'node:fs/promises'

const [snapshotId, revisionId, framework, destination] = process.argv.slice(2)
const root = resolve('..', '.local-ai', 'screenshots')
let browser
try {
  if (!/^[a-zA-Z0-9_-]{8,80}$/.test(snapshotId) || !/^[a-zA-Z0-9_-]{1,80}$/.test(revisionId) || !['REACT', 'HTML'].includes(framework)) throw new Error('Invalid capture target')
  const output = resolve(destination)
  if (relative(root, output).startsWith('..') || !output.endsWith('.png')) throw new Error('Capture path outside permitted directory')
  await mkdir(dirname(output), { recursive: true })
  browser = await chromium.launch({ channel: 'chrome', headless: true })
  const context = await browser.newContext({ viewport: { width: 1536, height: 1024 }, reducedMotion: 'reduce', serviceWorkers: 'block' })
  if (!process.env.LAYOUTLY_CAPTURE_SESSION) throw new Error('Missing internal preview session')
  await context.addCookies([{ name: 'layoutly_session', value: process.env.LAYOUTLY_CAPTURE_SESSION, domain: 'localhost', path: '/api', httpOnly: true, sameSite: 'Lax' }])
  await context.route('**/*', route => {
    const url = new URL(route.request().url())
    const allowed = ['http:', 'ws:'].includes(url.protocol) && ['localhost', '127.0.0.1', '[::1]'].includes(url.hostname) && ['5173', '8080'].includes(url.port)
    return allowed || ['data:', 'blob:', 'about:'].includes(url.protocol) ? route.continue() : route.abort('blockedbyclient')
  })
  const page = await context.newPage()
  const url = new URL('http://localhost:5173/')
  url.search = new URLSearchParams({ snapshotId, revisionId, framework }).toString()
  await page.goto(url.href)
  await page.locator('.we-preview-source').filter({ hasText: revisionId }).waitFor({ timeout: 18000 })
  const frame = page.frameLocator('iframe[title="Snapshot preview"]')
  await frame.locator('body').waitFor({ timeout: 18000 })
  await frame.locator('.figma-node, #root > *, body > main').first().waitFor({ timeout: 10000 })
  await frame.locator('body').evaluate(async () => { await document.fonts.ready; await Promise.all([...document.images].map(image => image.decode().catch(() => {}))) })
  await page.locator('.we-canvas-scroll').screenshot({ path: output })
  console.log(JSON.stringify({ status: 'CAPTURED', bytes: (await stat(output)).size, revisionId, externalUploads: false }))
} catch (error) {
  console.log(JSON.stringify({ status: 'ERROR', error: error.message }))
  process.exitCode = 1
} finally { await browser?.close() }
