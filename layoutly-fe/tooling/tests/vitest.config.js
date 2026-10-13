import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    include: ['specs/**/*.test.{js,jsx}'],
    setupFiles: ['./setup.js'],
    restoreMocks: true,
  },
})
