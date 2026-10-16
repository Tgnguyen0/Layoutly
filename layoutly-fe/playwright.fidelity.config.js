import {defineConfig} from '@playwright/test'
import path from 'node:path'
// Browser revision comes from locked @playwright/test; never use auto-updating system Chrome.
process.env.PLAYWRIGHT_BROWSERS_PATH ||= path.resolve('../.local-build/playwright-browsers')
export default defineConfig({
  testDir:'./tests/fidelity',testMatch:'corpus.spec.js',workers:1,timeout:180000,
  outputDir:process.env.LAYOUTLY_FIDELITY_OUTPUT ? path.resolve(process.env.LAYOUTLY_FIDELITY_OUTPUT,'playwright-results') : '../.local-build/fidelity/before/playwright-results',
  reporter:[['list']],
  use:{browserName:'chromium',headless:true,deviceScaleFactor:1,locale:'vi-VN',timezoneId:'Asia/Bangkok',colorScheme:'light',reducedMotion:'reduce'},
  webServer:{command:'node tests/fidelity/serve-corpus.mjs',url:'http://127.0.0.1:59991/health',reuseExistingServer:false},
})
