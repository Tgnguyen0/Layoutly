// Deterministic upstream Figma HTTP fixture. Never used by production code.
import { createServer } from 'node:http'
import { readFile } from 'node:fs/promises'
const fixture = await readFile(new URL('../../../layoutly-be/src/test/resources/figma-fixtures/horizontal/figma-raw.json', import.meta.url))
createServer((request, response) => {
  if (request.url === '/health') { response.writeHead(200); response.end('ok'); return }
  if (request.url.startsWith('/v1/files/')) {
    if (!request.headers['x-figma-token']) { response.writeHead(401); response.end('{}'); return }
    response.writeHead(200, { 'Content-Type': 'application/json' }); response.end(fixture); return
  }
  response.writeHead(404); response.end('{}')
}).listen(59990, '127.0.0.1', () => console.log('Figma fixture listening on http://127.0.0.1:59990'))
