import { defineConfig } from '@playwright/test'
export default defineConfig({
  testDir: './tests/e2e',
  testMatch: 'saved-projects.spec.js',
  outputDir: './test-results-saved-projects',
  workers: 1,
  timeout: 90000,
  reporter: [['list'], ['html', { outputFolder: 'playwright-report', open: 'never' }]],
  use: {
    baseURL: process.env.LAYOUTLY_TEST_URL || 'http://127.0.0.1:5174',
    channel: 'chrome', headless: true, locale: 'vi-VN', timezoneId: 'Asia/Bangkok',
    trace: 'retain-on-failure', screenshot: 'only-on-failure',
  },
  webServer: [
    { command: 'node tests/support/figma-server.mjs', url: 'http://127.0.0.1:59990/health', reuseExistingServer: false },
    { command: 'npm run dev -- --host 127.0.0.1 --port 5174 --strictPort', url: 'http://127.0.0.1:5174', reuseExistingServer: !process.env.CI },
  ],
})
