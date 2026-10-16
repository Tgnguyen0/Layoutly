import { expect, test } from '@playwright/test'

const snapshotId = process.env.LAYOUTLY_PREVIEW_SNAPSHOT
test.skip(!snapshotId, 'Set LAYOUTLY_PREVIEW_SNAPSHOT to the existing full-file MNTN regression snapshot.')

async function imageMetrics(page) {
  return page.evaluate(async () => {
    const urls = new Set()
    for (const node of document.querySelectorAll('.figma-node')) {
      for (const pseudo of [null, '::before']) {
        const image = getComputedStyle(node, pseudo).backgroundImage
        for (const match of image.matchAll(/url\(["']?([^"')]+)["']?\)/g)) urls.add(match[1])
      }
    }
    const results = await Promise.all([...urls].map(url => new Promise(resolve => {
      const image = new Image()
      image.onload = () => resolve({ url, loaded: image.naturalWidth > 0 })
      image.onerror = () => resolve({ url, loaded: false })
      image.src = url
    })))
    return { count: results.length, failed: results.filter(item => !item.loaded).map(item => item.url) }
  })
}

test('full-file preview shows the complete MNTN screen and durable images', async ({ page }, testInfo) => {
  const errors = []
  page.on('pageerror', error => errors.push(error.message))
  for (const width of [1440, 390]) {
    await page.setViewportSize({ width, height: 1000 })
    await page.goto(`/api/snapshots/${snapshotId}/preview?targetWidth=${width}`)
    await expect(page).toHaveTitle('MNTN')
    await expect(page.locator('#node-1-2')).toHaveCSS('background-color', 'rgb(11, 29, 38)')
    await expect(page.getByText(/Be prepared for the mountains and beyond/i)).toBeVisible()
    await expect(page.getByText('What level of hiker are you?', { exact: true })).toBeVisible()
    await expect(page.locator('#node-11-90')).not.toHaveCSS('background-image', 'none')
    await expect(page.locator('#node-11-94')).toHaveCSS('background-image', /linear-gradient/)
    await expect(page.locator('#node-1-9')).toHaveCSS('background-image', /linear-gradient/)
    const images = await imageMetrics(page)
    expect(images.count).toBeGreaterThanOrEqual(6)
    expect(images.failed).toEqual([])
    await page.screenshot({ path: testInfo.outputPath(`mntn-preview-${width}.png`), fullPage: true })
  }
  expect(errors).toEqual([])
})

test('workspace keeps the same full screen after selecting its Figma page and reloading', async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 1536, height: 1024 })
  await page.goto(`/?snapshotId=${snapshotId}`)
  const preview = page.frameLocator('iframe[title="Snapshot preview"]')
  await expect(preview.locator('#node-1-2')).toHaveCSS('background-color', 'rgb(11, 29, 38)')
  await expect(preview.getByText(/Be prepared for the mountains and beyond/i)).toBeVisible()
  await page.locator('.we-pages-list').getByRole('button', { name: 'MNTN', exact: true }).click()
  await expect(preview.locator('#node-1-2')).toHaveCSS('background-color', 'rgb(11, 29, 38)')
  await expect(preview.getByText(/Be prepared for the mountains and beyond/i)).toBeVisible()
  await page.reload()
  await expect(preview.getByText(/Be prepared for the mountains and beyond/i)).toBeVisible()
  expect((await imageMetrics(preview.locator('body'))).failed).toEqual([])
  await page.screenshot({ path: testInfo.outputPath('mntn-workspace-fixed.png'), fullPage: true })
})

test('adjacent sections from the supplied landing page stay together', async ({ page }, testInfo) => {
  const id = process.env.LAYOUTLY_SECTIONS_SNAPSHOT
  test.skip(!id, 'Set LAYOUTLY_SECTIONS_SNAPSHOT to the supplied section-based landing design.')
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto(`/api/snapshots/${id}/preview?targetWidth=1440`)
  for (const node of ['1-3', '1-14', '1-19', '19-30']) await expect(page.locator(`#node-${node}`)).toHaveCount(1)
  await expect(page.getByText('VỀ CHÚNG TÔI', { exact: true })).toBeVisible()
  await expect(page.getByText('One App, All Things Money', { exact: true })).toBeVisible()
  const metrics = await imageMetrics(page)
  expect(metrics.count).toBeGreaterThanOrEqual(10)
  expect(metrics.failed).toEqual([])
  await page.screenshot({ path: testInfo.outputPath('supplied-landing-preview.png'), fullPage: true })
})
