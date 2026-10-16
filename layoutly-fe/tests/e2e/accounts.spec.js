import { expect, test } from '@playwright/test'

test('dashboard account navigation fits desktop, tablet and mobile', async ({ page }, testInfo) => {
  // Dashboard now requires a real session. Keep both authenticated navigation and the logged-out gate covered.
  const csrf=(await (await page.request.get('/api/auth/csrf')).json()).token
  const registered=await page.request.post('/api/auth/register',{headers:{'X-CSRF-TOKEN':csrf},data:{displayName:'Responsive Tester',email:`responsive-${Date.now()}@example.com`,password:'ResponsiveTest123!',acceptedTermsVersion:'1'}})
  expect(registered.status()).toBe(201)
  for (const width of [1440, 1024, 850, 768, 390, 320]) {
    await page.setViewportSize({ width, height: 900 })
    await page.goto('/#/dashboard')
    await expect(page.getByRole('heading', { name: 'Dự án của tôi', exact: true })).toBeVisible()
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
    if (width <= 900) {
      await page.getByRole('button', { name: 'Mở sidebar', exact: true }).click()
      await expect(page.getByRole('link', { name: 'Thông tin cá nhân', exact: true })).toBeVisible()
      await page.keyboard.press('Escape')
      await expect(page.getByRole('button', { name: 'Mở sidebar', exact: true })).toBeFocused()
    }
    await page.screenshot({ path: testInfo.outputPath(`dashboard-${width}.png`), fullPage: true })
  }
  await page.setViewportSize({ width: 1440, height: 900 })
  await page.getByRole('link', { name: 'Thông tin cá nhân', exact: true }).click()
  await expect(page.getByRole('heading',{level:1,name:'Thông tin cá nhân',exact:true})).toBeVisible()
  await page.getByRole('button',{name:'Đăng xuất',exact:true}).click()
  await expect(page).toHaveURL(/#\/login$/)
  await page.goto('/#/account')
  await expect(page.getByRole('heading', { name: 'Đăng nhập để quản lý tài khoản' })).toBeVisible()
})

test('real account lifecycle: registration, profile, sessions, password, login and deletion', async ({ page, browser }, testInfo) => {
  test.setTimeout(120000)
  const email = `layoutly-qa-${Date.now()}@example.com`
  const newEmail = email.replace('@', '-updated@')
  const originalPassword = 'layoutly-test-password-123'
  const newPassword = 'layoutly-new-password-456'
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  const secondContext = await browser.newContext({ baseURL: testInfo.project.use.baseURL })
  const other = await secondContext.newPage()

  async function login(target, emailValue, passwordValue) {
    await target.goto('/#/login')
    await target.getByLabel('Email', { exact: true }).fill(emailValue)
    await target.getByLabel('Mật khẩu', { exact: true }).fill(passwordValue)
    await target.getByRole('button', { name: 'Đăng nhập', exact: true }).click()
  }
  try {
    await page.goto('/#/register')
    await expect(page.getByRole('button', { name: 'Tạo tài khoản', exact: true })).toBeEnabled()
    await page.getByLabel('Họ và tên', { exact: true }).fill('Layoutly QA')
    await page.getByLabel('Email', { exact: true }).fill(email)
    await page.getByLabel('Mật khẩu', { exact: true }).fill(originalPassword)
    await page.getByLabel('Xác nhận mật khẩu', { exact: true }).fill(originalPassword)
    await page.getByRole('checkbox', { name: /Tôi đồng ý/ }).check()
    await page.getByRole('button', { name: 'Tạo tài khoản', exact: true }).click()
    await expect(page).toHaveURL(/#\/dashboard$/)
    await expect(page.getByText('Xin chào, QA', { exact: true })).toBeVisible()
    await page.getByRole('link', { name: 'Thông tin cá nhân', exact: true }).click()
    await expect(page.getByLabel('Email', { exact: true })).toHaveValue(email)
    await page.getByLabel('Họ và tên', { exact: true }).fill('Thanh Designer')
    await page.getByLabel('Công ty / Tổ chức').fill('Layoutly Studio')
    await page.getByLabel('Giới thiệu', { exact: true }).fill('Thiết kế và phát triển sản phẩm số.')
    await page.getByRole('button', { name: 'Lưu thay đổi', exact: true }).click()
    await expect(page.getByRole('status')).toContainText('Đã lưu thông tin tài khoản.')
    await page.reload()
    await expect(page.getByLabel('Công ty / Tổ chức')).toHaveValue('Layoutly Studio')

    await login(other, email, originalPassword)
    await expect(other).toHaveURL(/#\/dashboard$/)
    await page.getByLabel('Email', { exact: true }).fill(newEmail)
    await page.getByLabel('Mật khẩu hiện tại để đổi email').fill('wrong-password')
    await page.getByRole('button', { name: 'Lưu thay đổi', exact: true }).click()
    await expect(page.getByRole('alert')).toContainText('Mật khẩu hiện tại không đúng.')
    await page.getByLabel('Mật khẩu hiện tại để đổi email').fill(originalPassword)
    await page.getByRole('button', { name: 'Lưu thay đổi', exact: true }).click()
    await expect(page.getByRole('status')).toContainText('Đã lưu thông tin tài khoản.')
    expect((await (await secondContext.request.get('/api/auth/me')).json()).user).toBeNull()

    await login(other, newEmail, originalPassword)
    await expect(other).toHaveURL(/#\/dashboard$/)
    await page.getByRole('button', { name: 'Bảo mật & đăng nhập', exact: true }).click()
    await expect(page.locator('.ac-sessions li')).toHaveCount(2)
    await page.getByRole('button', { name: 'Đăng xuất thiết bị khác', exact: true }).click()
    await expect(page.locator('.ac-sessions li')).toHaveCount(1)
    expect((await (await secondContext.request.get('/api/auth/me')).json()).user).toBeNull()

    await login(other, newEmail, originalPassword)
    await expect(other).toHaveURL(/#\/dashboard$/)
    await page.getByLabel('Mật khẩu hiện tại', { exact: true }).fill(originalPassword)
    await page.getByLabel('Mật khẩu mới', { exact: true }).fill(newPassword)
    await page.getByLabel('Xác nhận mật khẩu mới', { exact: true }).fill('a-different-password')
    await page.getByRole('button', { name: 'Cập nhật mật khẩu', exact: true }).click()
    await expect(page.getByRole('alert')).toHaveText('Mật khẩu xác nhận không khớp.')
    await page.getByLabel('Xác nhận mật khẩu mới', { exact: true }).fill(newPassword)
    await page.getByRole('button', { name: 'Cập nhật mật khẩu', exact: true }).click()
    await expect(page.getByRole('status')).toContainText('Đã đổi mật khẩu.')
    expect((await (await secondContext.request.get('/api/auth/me')).json()).user).toBeNull()

    for (const width of [1440, 1024, 768, 390, 320]) {
      await page.setViewportSize({ width, height: 900 })
      for (const [tab, label] of [['profile', 'Thông tin cá nhân'], ['security', 'Bảo mật & đăng nhập']]) {
        await page.getByRole('button', { name: label, exact: true }).click()
        await expect(page.getByRole('heading', { level: 1, name: label, exact: true })).toBeVisible()
        await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
        const screenshot = testInfo.outputPath(`account-${tab}-${width}.png`)
        await page.screenshot({ path: screenshot, fullPage: true })
        await testInfo.attach(`account-${tab}-${width}`, { path: screenshot, contentType: 'image/png' })
      }
    }
    await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click()
    await expect(page).toHaveURL(/#\/login$/)
    expect((await (await page.request.get('/api/auth/me')).json()).user).toBeNull()
    await login(page, newEmail, originalPassword)
    await expect(page.getByRole('alert')).toContainText('Email hoặc mật khẩu không đúng.')
    await page.getByLabel('Mật khẩu', { exact: true }).fill(newPassword)
    await page.getByRole('checkbox', { name: 'Ghi nhớ đăng nhập' }).check()
    await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click()
    await expect(page).toHaveURL(/#\/dashboard$/)
    await page.reload()
    await expect(page.getByText('Xin chào, Designer', { exact: true })).toBeVisible()
    await page.goto('/#/settings')
    await expect(page.getByRole('heading', { level: 1, name: 'Bảo mật & đăng nhập' })).toBeVisible()
    await page.getByRole('button', { name: 'Xóa tài khoản', exact: true }).click()
    await page.getByLabel('Mật khẩu xác nhận xóa').fill('wrong-password')
    await page.getByRole('button', { name: 'Xóa vĩnh viễn' }).click()
    await expect(page.getByRole('dialog').getByRole('alert')).toContainText('Mật khẩu hiện tại không đúng.')
    await page.getByRole('button', { name: 'Hủy', exact: true }).click()
    await expect(page.getByRole('dialog')).not.toBeVisible()
    await page.getByRole('button', { name: 'Xóa tài khoản', exact: true }).click()
    await page.getByLabel('Mật khẩu xác nhận xóa').fill(newPassword)
    await page.getByRole('button', { name: 'Xóa vĩnh viễn' }).click()
    await expect(page).toHaveURL(/#\/login$/)
    await page.goto('/#/account')
    await expect(page.getByRole('heading', { name: 'Đăng nhập để quản lý tài khoản' })).toBeVisible()
    expect(errors).toEqual([])
  } finally {
    testInfo.setTimeout(testInfo.timeout + 15000)
    // Delete only this test's account if an assertion failed before UI cleanup.
    try {
      const me = await (await page.request.get('/api/auth/me', { timeout: 5000 })).json()
      if (me.user?.email === email || me.user?.email === newEmail) {
        const csrf = await (await page.request.get('/api/auth/csrf', { timeout: 5000 })).json()
        for (const currentPassword of [newPassword, originalPassword]) {
          const result = await page.request.delete('/api/auth/account', { timeout: 5000, headers: { 'X-CSRF-TOKEN': csrf.token }, data: { currentPassword } })
          if (result.ok()) break
        }
      }
    } catch { /* Cleanup must not hide the original assertion failure. */ }
    await secondContext.close()
  }
})
