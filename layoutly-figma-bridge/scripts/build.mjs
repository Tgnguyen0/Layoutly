import { build, context } from 'esbuild'
import { copyFile, mkdir } from 'node:fs/promises'

const watch = process.argv.includes('--watch')
await mkdir('dist', { recursive: true })
await copyFile('src/ui.html', 'dist/ui.html')

const options = {
  entryPoints: ['src/code.ts'],
  bundle: true,
  outfile: 'dist/code.js',
  platform: 'browser',
  target: 'es2020',
  format: 'iife',
  logLevel: 'info',
}

if (watch) {
  const buildContext = await context(options)
  await buildContext.watch()
  console.log('Layoutly Bridge is watching for changes.')
} else {
  await build(options)
}
