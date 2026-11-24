import { expect, test } from '@playwright/test'

const snapshotId = process.env.LAYOUTLY_SMOKE_SNAPSHOT
async function openSnapshot(page) {
  await page.goto(`/?snapshotId=${encodeURIComponent(snapshotId)}`)
  await expect(page.locator('.we-project-title h1')).not.toHaveText('Thiết kế mới')
  await expect(page.frameLocator('iframe[title="Snapshot preview"]').locator('.figma-node').first()).toBeVisible({ timeout: 30000 })
}

for (const width of [1440, 1280, 1024, 768, 390]) {
  test(`dashboard and editor fit ${width}px with real snapshot`, async ({ page }, testInfo) => {
    test.skip(!snapshotId, 'Requires an existing local snapshot, not mock runtime data.')
    await page.setViewportSize({ width, height: 900 })
    await openSnapshot(page)
    await expect(page.locator('iframe[title="Snapshot preview"]')).toHaveAttribute('sandbox', 'allow-scripts')
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    await page.getByRole('button', { name: 'Mở hoặc đóng Layoutly AI' }).click()
    await expect(page.getByLabel('Yêu cầu AI', { exact: true })).toBeDisabled()
    await page.getByRole('button', { name: 'Đóng AI Chat' }).click()
    if (width < 900) {
      await page.getByRole('button', { name: 'Mở hoặc đóng layers' }).click()
      await expect(page.getByRole('complementary', { name: 'Pages và Layers' })).toBeVisible()
      await page.getByRole('button', { name: 'Đóng layers', exact: true }).click()
      await page.getByRole('button', { name: 'Mở hoặc đóng inspector' }).click()
      await expect(page.getByRole('region', { name: 'Mã nguồn sinh ra' })).toBeVisible()
      await page.getByRole('button', { name: 'Mở hoặc đóng inspector' }).click()
    }
    await page.screenshot({ path: testInfo.outputPath(`editor-${width}.png`) })
    await page.getByRole('link', { name: 'Về dashboard' }).click()
    await expect(page.locator('.db-card')).toHaveCount(1)
    await page.getByLabel('Tìm dự án').fill('unmatched-name')
    await expect(page.getByRole('heading', { name: 'Không tìm thấy dự án' })).toBeVisible()
    await page.getByLabel('Tìm dự án').clear()
    await page.getByRole('button', { name: 'Đã chia sẻ (0)' }).click()
    await expect(page.locator('.db-card')).toHaveCount(0)
    await page.getByRole('button', { name: 'Tất cả (1)' }).click()
    await expect(page.locator('.db-card')).toHaveCount(1)
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    if (width < 900) {
      await page.getByRole('button', { name: 'Mở sidebar', exact: true }).click()
      await expect(page.getByRole('complementary', { name: 'Điều hướng dự án' })).toBeVisible()
      await page.keyboard.press('Escape')
      await expect(page.getByRole('complementary', { name: 'Điều hướng dự án' })).not.toBeVisible()
    }
    await page.getByRole('link', { name: 'Tạo dự án mới', exact: true }).first().click()
    await expect(page.getByRole('dialog', { name: 'Nhập thiết kế', exact: true })).toBeVisible()
    await page.keyboard.press('Escape')
    await expect(page.getByRole('dialog')).not.toBeVisible()
  })
}

test('node selection, isolation, viewport, code and real ZIP downloads', async ({ page }) => {
  test.skip(!snapshotId, 'Requires existing local snapshot.')
  await page.setViewportSize({ width: 1440, height: 900 })
  await openSnapshot(page)
  const frame = page.frameLocator('iframe[title="Snapshot preview"]')
  const text = frame.locator('.figma-node[data-figma-type="TEXT"]').first()
  const local = await text.evaluate(element => { const box = element.getBoundingClientRect(); return { x: box.x + box.width / 2, y: box.y + box.height / 2 } })
  const frameBox = await page.locator('iframe[title="Snapshot preview"]').boundingBox()
  const scale = await page.locator('iframe[title="Snapshot preview"]').evaluate(element => new DOMMatrix(getComputedStyle(element).transform).a)
  // CDP reports unscaled child coordinates for a CSS-transformed iframe.
  await page.mouse.click(frameBox.x + local.x * scale, frameBox.y + local.y * scale)
  await expect(page.locator('.we-selected-name')).toBeVisible()
  await expect(frame.locator('.layoutly-selected')).toHaveCount(1)
  const child = page.frames().find(value => value !== page.mainFrame() && value.url() === 'about:srcdoc')
  expect(await child.evaluate(() => { try { return Boolean(parent.document.body) } catch { return false } })).toBe(false)
  await page.getByRole('button', { name: 'CSS', exact: true }).click()
  await expect(page.locator('.we-code-panel code')).toContainText('.figma')
  await page.getByRole('button', { name: 'React', exact: true }).click()
  await expect(page.locator('.we-code-panel code')).toContainText('export')
  await page.getByRole('button', { name: 'JSON', exact: true }).click()
  await expect(page.locator('.we-code-panel code')).toContainText('"bounds"')
  await page.getByLabel('Viewport preview').selectOption('390')
  await expect(frame.locator('.figma-node').first()).toBeVisible()
  await expect(page.locator('iframe[title="Snapshot preview"]')).toHaveCSS('width', '390px')
  await page.getByRole('button', { name: 'Xuất dự án' }).click()
  for (const label of ['HTML/CSS ZIP', 'React ZIP']) {
    await expect(page.getByRole('button', { name: label, exact: true })).toBeEnabled()
    const downloading = page.waitForEvent('download')
    await page.getByRole('button', { name: label, exact: true }).click()
    const download = await downloading
    expect(download.suggestedFilename()).toMatch(/\.zip$/)
    expect(await download.failure()).toBeNull()
    const bytes = await (await import('node:fs/promises')).readFile(await download.path())
    expect(bytes.subarray(0, 2).toString()).toBe('PK')
    expect(bytes.length).toBeGreaterThan(1000)
  }
})

test('empty workspace opens Bridge and REST import uses the existing contract', async ({ page }) => {
  await page.route('**/api/figma/me', route => route.fulfill({ json: { id: 'test-validation-response' } }))
  let payload
  await page.route('**/api/figma/import', async route => {
    payload = { body: route.request().postDataJSON(), token: route.request().headers()['x-figma-token'] }
    await route.fulfill({ status: 400, json: { error: 'Contract probe only; no design imported.' } })
  })
  await page.goto('/#workspace')
  await page.getByRole('button', { name: 'Import Figma', exact: true }).click()
  await expect(page.getByText('Layoutly Bridge', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Figma URL', exact: true }).click()
  await page.getByLabel('Figma URL hoặc File Key').fill('https://www.figma.com/design/abc123/test?node-id=2-3')
  await page.getByLabel('Figma personal access token').fill('test-only-not-a-credential')
  await page.getByRole('button', { name: 'Kiểm tra token' }).click()
  await expect(page.getByText('Figma đã xác nhận token hợp lệ.')).toBeVisible()
  await page.getByRole('dialog').getByRole('button', { name: 'Nhập thiết kế', exact: true }).click()
  await expect(page.getByText('Contract probe only; no design imported.')).toBeVisible()
  expect(payload).toEqual({ body: { fileKey: 'abc123', nodeId: '2:3', figmaUrl: 'https://www.figma.com/design/abc123/test?node-id=2-3' }, token: 'test-only-not-a-credential' })
  expect(await page.evaluate(() => JSON.stringify({ ...localStorage, ...sessionStorage }))).not.toContain('test-only-not-a-credential')
  await page.getByRole('button', { name: 'Đóng nhập thiết kế' }).click()
  await expect(page.getByRole('dialog')).not.toBeVisible()
})
