import {defineConfig} from '@playwright/test'
import base from './playwright.saved-projects.config.js'
import path from 'node:path'
process.env.PLAYWRIGHT_BROWSERS_PATH ||= path.resolve('../.local-build/playwright-browsers')
export default defineConfig({...base,
  testMatch:/saved-projects\.spec\.js|preview-profiling\.spec\.js/,
  outputDir:'../.local-build/fidelity/api-results',
  use:{...base.use,channel:undefined,browserName:'chromium'},
  reporter:[['list']],
})
