import { build } from 'esbuild'
await build({ entryPoints:['src/lib/preview-runtime.js'], outfile:'public/preview-runtime.js', bundle:true, format:'iife', platform:'browser', minify:true, define:{'process.env.NODE_ENV':'"production"'}, logLevel:'warning' })
