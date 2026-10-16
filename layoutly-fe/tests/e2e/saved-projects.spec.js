import { test, expect } from '@playwright/test'
import { readFile } from 'node:fs/promises'

const password = 'layoutly-e2e-password-123'
async function register(page) {
  const email = 'layoutly-e2e-' + Date.now() + '-' + Math.random().toString(36).slice(2) + '@example.com'
  await page.goto('/#/register')
  await page.getByLabel('Họ và tên', { exact: true }).fill('Layoutly Tester')
  await page.getByLabel('Email', { exact: true }).fill(email)
  await page.getByLabel('Mật khẩu', { exact: true }).fill(password)
  await page.getByLabel('Xác nhận mật khẩu', { exact: true }).fill(password)
  await page.getByRole('checkbox', { name: /Tôi đồng ý/ }).check()
  await page.getByRole('button', { name: 'Tạo tài khoản', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Dự án của tôi', exact: true })).toBeVisible()
  return email
}
async function login(page, email) {
  await page.goto('/#/login')
  await page.getByLabel('Email', { exact: true }).fill(email)
  await page.getByLabel('Mật khẩu', { exact: true }).fill(password)
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Dự án của tôi', exact: true })).toBeVisible()
}
async function signOut(page) {
  await page.goto('/#/dashboard')
  await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click()
  await expect(page).toHaveURL(/#\/login$/)
}

test('Register → Login → Import Figma → Save → Logout → Login → Reopen → Export ZIP', async ({ page }, testInfo) => {
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  const email = await register(page)
  await signOut(page)
  await login(page, email)
  page.once('dialog', dialog => dialog.accept('E2E Saved Project'))
  await page.getByRole('button', { name: 'Tạo dự án mới', exact: true }).first().click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await page.getByRole('button', { name: 'Figma URL', exact: true }).click()
  await page.getByLabel('Figma URL hoặc File Key', { exact: true }).fill('https://www.figma.com/design/e2e-fixture/Fixture')
  await page.getByLabel('Figma personal access token', { exact: true }).fill('e2e-upstream-token')
  await page.getByRole('dialog').getByRole('button', { name: 'Nhập thiết kế', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await expect(page.locator('.we-project-title h1')).toHaveText('E2E Saved Project')
  await page.getByRole('button', { name: 'Lưu dự án', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('Đã lưu dự án')
  const projectUrl = page.url()
  await page.screenshot({ path: testInfo.outputPath('saved-workspace.png'), fullPage: true })
  await signOut(page)
  await login(page, email)
  await page.getByRole('link', { name: 'Mở E2E Saved Project', exact: true }).click()
  await expect(page.locator('.we-project-title h1')).toHaveText('E2E Saved Project')
  await expect(page.locator('.we-save-state')).toHaveText('Saved')
  await page.getByRole('button', { name: 'Xuất dự án', exact: true }).click()
  const downloading = page.waitForEvent('download')
  await page.getByRole('button', { name: 'React ZIP', exact: true }).click()
  const download = await downloading
  expect(download.suggestedFilename()).toMatch(/react.*\.zip$/)
  const bytes = await readFile(await download.path())
  expect(bytes.subarray(0, 2).toString()).toBe('PK')
  await page.goto('/#/dashboard')
  await page.getByRole('button', { name: 'Menu E2E Saved Project', exact: true }).click()
  await page.getByRole('button', { name: 'Lịch sử xuất', exact: true }).click()
  await expect(page.getByRole('region', { name: 'Lịch sử xuất', exact: true })).toContainText('REACT ZIP')
  await page.goto(projectUrl)
  await expect(page.locator('.we-project-title h1')).toHaveText('E2E Saved Project')
  expect(errors).toEqual([])
})

test('dashboard search, rename, duplicate, delete and responsive account navigation', async ({ page }, testInfo) => {
  await register(page)
  page.once('dialog', dialog => dialog.accept('Original project'))
  await page.getByRole('button', { name: 'Tạo dự án mới', exact: true }).first().click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await page.goto('/#/dashboard')
  await page.getByRole('button', { name: 'Menu Original project', exact: true }).click()
  page.once('dialog', dialog => dialog.accept('Renamed project'))
  await page.getByRole('button', { name: 'Đổi tên', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Renamed project', exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Menu Renamed project', exact: true }).click()
  await page.getByRole('button', { name: 'Nhân bản', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Renamed project (bản sao)', exact: true })).toBeVisible()
  await page.getByLabel('Tìm dự án', { exact: true }).fill('no matches')
  await expect(page.getByRole('heading', { name: 'Không tìm thấy dự án', exact: true })).toBeVisible()
  await page.getByLabel('Tìm dự án', { exact: true }).fill('')
  await page.getByRole('button', { name: 'Menu Renamed project (bản sao)', exact: true }).click()
  page.once('dialog', dialog => dialog.accept())
  await page.getByRole('button', { name: 'Xóa dự án', exact: true }).click()
  await expect(page.getByRole('heading', { name: 'Renamed project (bản sao)', exact: true })).toHaveCount(0)
  await expect(page.getByRole('heading', { name: 'Renamed project', exact: true })).toBeVisible()
  for (const width of [1440, 1024, 768, 390, 320]) {
    await page.setViewportSize({ width, height: 900 })
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    if (width <= 900) {
      await page.getByRole('button', { name: 'Mở sidebar', exact: true }).click()
      await expect(page.getByRole('link', { name: 'Thông tin cá nhân', exact: true })).toBeVisible()
      await page.keyboard.press('Escape')
    }
    await page.screenshot({ path: testInfo.outputPath('dashboard-' + width + '.png'), fullPage: true })
  }
})

test('a second account cannot reopen the first account project', async ({ page, browser }, testInfo) => {
  await register(page)
  page.once('dialog', dialog => dialog.accept('Private project'))
  await page.getByRole('button', { name: 'Tạo dự án mới', exact: true }).first().click()
  await expect(page.getByRole('dialog')).toBeVisible()
  const privateUrl = page.url()
  const second = await browser.newContext({ baseURL: testInfo.project.use.baseURL })
  try {
    const other = await second.newPage()
    await register(other)
    await other.goto(privateUrl)
    await expect(other.getByRole('alert').first()).toContainText('Không tìm thấy')
    await other.goto('/#/dashboard')
    await expect(other.getByRole('heading', { name: 'Private project', exact: true })).toHaveCount(0)
  } finally { await second.close() }
})

test('Figma Bridge pairing imports real assets, saves and previews after reopening', async ({ page }) => {
  await register(page)
  const csrf = await (await page.request.get('/api/auth/csrf')).json()
  const pairingResponse = await page.request.post('/api/auth/bridge-token', { headers: { 'X-CSRF-TOKEN': csrf.token } })
  expect(pairingResponse.ok()).toBe(true)
  const pairing = await pairingResponse.json()
  const design = { name: 'Bridge assets', version: 'bridge-v1', document: {
    id: '0:0', name: 'Document', type: 'DOCUMENT', children: [{ id: '1:0', name: 'Page', type: 'CANVAS', children: [{
      id: '2:1', name: 'Bridge Frame', type: 'FRAME', absoluteBoundingBox: { x: 0, y: 0, width: 1440, height: 900 },
      children: [{ id: '3:1', name: 'Icon', type: 'VECTOR', absoluteBoundingBox: { x: 20, y: 20, width: 32, height: 32 } }],
    }] }],
  } }
  const metadata = { source: 'FIGMA_PLUGIN', fileKey: 'bridge-e2e', fileName: 'Bridge assets', pageName: 'Page', nodeId: '2:1', nodeName: 'Bridge Frame', nodeType: 'FRAME', exportedAt: new Date().toISOString(), assets: [{ nodeId: '3:1', fileName: 'icon.svg', contentType: 'image/svg+xml' }], warnings: [] }
  const imported = await page.request.post('/api/bridge/import', {
    headers: { 'X-Layoutly-Bridge-Token': pairing.token },
    multipart: {
      metadata: { name: 'metadata.json', mimeType: 'application/json', buffer: Buffer.from(JSON.stringify(metadata)) },
      designJson: { name: 'design.json', mimeType: 'application/json', buffer: Buffer.from(JSON.stringify(design)) },
      assets: { name: 'icon.svg', mimeType: 'image/svg+xml', buffer: Buffer.from('<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32"><rect width="32" height="32" fill="#ffb31a"/></svg>') },
    },
  })
  expect(imported.ok()).toBe(true)
  const snapshot = await imported.json()
  await page.goto('/?snapshotId=' + snapshot.snapshotId)
  const preview = page.frameLocator('iframe[title="Snapshot preview"]')
  const icon = preview.locator('[data-figma-name="Icon"]')
  await expect(icon).toBeVisible()
  const loaded = await icon.evaluate(async element => {
    const url = getComputedStyle(element).backgroundImage.slice(5, -2)
    const image = new Image()
    return new Promise(resolve => { image.onload = () => resolve(image.naturalWidth > 0); image.onerror = () => resolve(false); image.src = url })
  })
  expect(loaded).toBe(true)
  await page.getByRole('button', { name: 'Lưu dự án', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('Đã lưu dự án')
  const project = new URL(page.url()).searchParams.get('projectId')
  const assetResponse = await page.request.get('/api/projects/' + project + '/assets')
  expect((await assetResponse.json()).assets).toHaveLength(1)
  await page.goto('/#/dashboard')
  await page.getByRole('link', { name: 'Mở Bridge assets', exact: true }).click()
  await expect(page.frameLocator('iframe[title="Snapshot preview"]').locator('[data-figma-name="Icon"]')).toBeVisible()
  await page.goto('/?projectId=' + project + '&revisionId=rev_000_baseline&framework=REACT')
  await expect(page.locator('.we-preview-source')).toContainText('rev_000_baseline')
  const revisionIcon = page.frameLocator('iframe[title="Snapshot preview"]').locator('[data-figma-name="Icon"]')
  await expect(revisionIcon).toBeVisible()
  expect(await revisionIcon.evaluate(async element => {
    const url = getComputedStyle(element).backgroundImage.slice(5, -2)
    return new Promise(resolve => { const image = new Image(); image.onload = () => resolve(image.naturalWidth > 0); image.onerror = () => resolve(false); image.src = url })
  })).toBe(true)
})
