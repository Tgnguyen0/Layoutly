import {defineConfig} from '@playwright/test'
import path from 'node:path'
process.env.PLAYWRIGHT_BROWSERS_PATH ||= path.resolve('../.local-build/playwright-browsers')
export default defineConfig({
  testDir:'./tests',testMatch:process.env.LAYOUTLY_CAPTURE_MARKETING_ASSETS==='1'?'**/marketing/landing-assets.spec.js':'**/e2e/landing-visual.spec.js',workers:1,timeout:120000,
  outputDir:process.env.LAYOUTLY_LANDING_OUTPUT||'../.local-build/landing-redesign/tests',
  reporter:[['list']],
  use:{baseURL:'http://127.0.0.1:5174',browserName:'chromium',headless:true,locale:'vi-VN',timezoneId:'Asia/Bangkok',deviceScaleFactor:1,reducedMotion:'reduce',trace:'retain-on-failure'},
  webServer:{command:'npm run dev -- --host 127.0.0.1 --port 5174 --strictPort',url:'http://127.0.0.1:5174',reuseExistingServer:false},
})
