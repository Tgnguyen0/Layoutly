import { defineConfig } from '@playwright/test'
import config from './playwright.config.js'
export default defineConfig({
  ...config,
  testMatch: '**/ai-workspace.spec.js',
  outputDir: './test-results-production',
  use: { ...config.use, baseURL:'http://127.0.0.1:5175' },
  webServer: { command:'npm run preview -- --host 127.0.0.1 --port 5175 --strictPort',url:'http://127.0.0.1:5175',reuseExistingServer:false },
})
