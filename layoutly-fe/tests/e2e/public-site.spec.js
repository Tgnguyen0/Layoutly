import { expect, test } from '@playwright/test'

for (const viewport of [{ width: 1440, height: 900 }, { width: 1280, height: 800 }, { width: 1024, height: 900 }, { width: 768, height: 1024 }, { width: 390, height: 844 }, { width: 320, height: 800 }]) {
  test(`public pages fit ${viewport.width}px and load local assets`, async ({ page }, testInfo) => {
    await page.setViewportSize(viewport)
    const errors = []
    page.on('pageerror', error => errors.push(error.message))
    for (const [name, path] of [['landing', '/'], ['login', '/#/login'], ['register', '/#/register'], ['forgot', '/#/forgot-password']]) {
      await page.goto(path)
      await expect(page.locator('.public-app')).toBeVisible()
      await page.evaluate(() => document.fonts.ready)
      if (name === 'forgot') await expect(page.getByText('Khôi phục mật khẩu qua email chưa khả dụng', { exact: true })).toBeVisible()
      else if (name !== 'landing') await expect(page.getByRole('button', { name: name === 'login' ? 'Đăng nhập' : 'Tạo tài khoản', exact: true })).toBeEnabled()
      const metrics = await page.evaluate(async () => {
        const images = [...document.querySelectorAll('.public-app img')]
        await Promise.all(images.map(image => image.decode()))
        return {
          overflow: document.documentElement.scrollWidth > innerWidth,
          interLoaded: document.fonts.check('400 14px Inter'),
          missingImages: images.filter(image => !image.complete || !image.naturalWidth).length,
          outsideViewport: [...document.querySelectorAll('.public-app input,.public-app button,.public-app h1,.public-app h2')]
            .filter(el => el.getBoundingClientRect().width && (el.getBoundingClientRect().right > innerWidth + 1 || el.getBoundingClientRect().left < -1)).map(el => el.tagName),
        }
      })
      expect(metrics).toEqual({ overflow: false, interLoaded: true, missingImages: 0, outsideViewport: [] })
      if (name === 'landing' && viewport.width <= 900) {
        const notes = await page.locator('.ly-hero-notes').boundingBox()
        const image = await page.locator('.ly-hero > .ly-hero-inner > .ly-product-scene').boundingBox()
        expect(notes.y + notes.height).toBeLessThanOrEqual(image.y)
        if (viewport.width === 390) {
          const next = await page.locator('#work .ly-eyebrow').boundingBox()
          expect(next.y).toBeLessThan(viewport.height)
        }
      }
      if (name !== 'landing' && viewport.width > 800) await expect(page.locator('.ly-auth-intro h1')).toContainText('Ý tưởng tốt')
      const screenshot = testInfo.outputPath(`${name}-${viewport.width}.png`)
      await page.screenshot({ path: screenshot, fullPage: true })
      await testInfo.attach(`${name}-${viewport.width}`, { path: screenshot, contentType: 'image/png' })
    }
    expect(errors).toEqual([])
  })
}

test('public links, validation, unavailable authentication and keyboard navigation', async ({ page }) => {
  await page.route('**/api/auth/capabilities', route => route.fulfill({ status: 404, body: '{}' }))
  const credentialRequests = []
  page.on('request', request => { if (request.method() === 'POST' && request.url().includes('/api/auth/')) credentialRequests.push(request.url()) })
  await page.goto('/#/login')
  await expect(page.getByRole('button', { name: 'Đăng nhập', exact: true })).toBeDisabled()
  await page.getByLabel('Email', { exact: true }).fill('invalid')
  await page.getByLabel('Email', { exact: true }).press('Tab')
  await expect(page.getByText('Nhập một địa chỉ email hợp lệ.')).toBeVisible()
  await page.getByRole('button', { name: 'Hiện mật khẩu', exact: true }).click()
  await expect(page.getByLabel('Mật khẩu', { exact: true })).toHaveAttribute('type', 'text')
  await page.getByRole('link', { name: 'Đăng ký ngay' }).click()
  await expect(page.getByLabel('Xác nhận mật khẩu', { exact: true })).toBeVisible()
  await page.getByLabel('Mật khẩu', { exact: true }).fill('short')
  await page.getByLabel('Mật khẩu', { exact: true }).press('Tab')
  await expect(page.getByText('Mật khẩu cần từ 12 đến 128 ký tự.')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Tạo tài khoản', exact: true })).toBeDisabled()
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/')
  await page.getByRole('button', { name: 'Mở menu' }).click()
  await expect(page.getByRole('navigation', { name: 'Điều hướng di động' })).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('navigation', { name: 'Điều hướng di động' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Mở menu' })).toBeFocused()
  await page.goto('/#/not-found')
  await expect(page.getByRole('heading', { name: 'Không tìm thấy trang.' })).toBeVisible()
  expect(credentialRequests).toEqual([])
})

test('landing integrates with the existing live Bridge backend', async ({ page, request }) => {
  const response = await request.get('/api/bridge/status')
  expect(response.ok()).toBe(true)
  expect((await response.json()).status).toBe('READY')
  await page.goto('/')
  await expect(page.getByText('Figma Bridge sẵn sàng', { exact: true })).toBeVisible()
  await page.getByRole('link', { name: 'Bắt đầu miễn phí', exact: true }).first().click()
  await page.getByRole('button', { name: 'Import Figma', exact: true }).click()
  await expect(page.getByText('Layoutly Bridge', { exact: true })).toBeVisible()
  await expect(page.locator('#workspace')).toBeVisible()
})

test('Bridge deep link still loads a real saved snapshot, preview and code', async ({ page }) => {
  const snapshotId = process.env.LAYOUTLY_SMOKE_SNAPSHOT
  test.skip(!snapshotId, 'Provide an existing local snapshot ID; no demo snapshot is created.')
  await page.setViewportSize({ width: 1440, height: 900 })
  const response = await page.request.get(`/api/snapshots/${snapshotId}`)
  expect(response.ok()).toBe(true)
  const snapshot = await response.json()
  await page.goto(`/?snapshotId=${encodeURIComponent(snapshotId)}`)
  await expect(page.getByRole('heading', { name: snapshot.fileName, exact: true })).toBeVisible()
  await expect(page.locator('pre code')).not.toHaveText('Import a design to see generated code.')
  await expect(page.frameLocator('iframe[title="Snapshot preview"]').locator('.figma-node').first()).toBeVisible({ timeout: 30000 })
  await expect(page).toHaveURL(/#workspace$/)
  await page.reload()
  await expect(page.getByRole('heading', { name: snapshot.fileName, exact: true })).toBeVisible()
})
