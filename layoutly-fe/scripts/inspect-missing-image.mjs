import { stdin, stdout } from 'node:process'
import { mkdir, writeFile } from 'node:fs/promises'
stdin.setRawMode?.(true); stdin.resume(); stdout.write('Ready for hidden token input.\n')
const token = await new Promise(resolve => {
  let value = ''
  function read(bytes) { for (const ch of bytes.toString()) {
    if (ch === '\r' || ch === '\n') { stdin.off('data', read); stdin.setRawMode?.(false); stdin.pause(); resolve(value.trim()); return }
    value += ch
  } }
  stdin.on('data', read)
})
try {
  const response = await fetch('http://127.0.0.1:8080/api/figma/file/BSQn8vpxTnLeIl69e2rKTw/images?ids=11:90&format=png', {
    headers: { 'X-Figma-Token': token }, signal: AbortSignal.timeout(150000),
  })
  const data = await response.json()
  const url = data.images?.['11:90']
  if (!response.ok || !url) { stdout.write(JSON.stringify({ stage: 'render', status: response.status, imageUrlAvailable: Boolean(url) }) + '\n') }
  else {
    const chunkSize = 128 * 1024
    const first = await fetch(url, { headers: { Range: `bytes=0-${chunkSize - 1}` }, signal: AbortSignal.timeout(45000) })
    const range = first.headers.get('content-range')
    stdout.write(JSON.stringify({ stage: 'ranged-download', host: new URL(url).hostname, status: first.status, range }) + '\n')
    if (first.status !== 206 || !range) throw new Error('Range download unavailable')
    const total = Number(range.split('/')[1])
    const bytes = new Uint8Array(total)
    bytes.set(new Uint8Array(await first.arrayBuffer()), 0)
    const starts = []
    for (let start = chunkSize; start < total; start += chunkSize) starts.push(start)
    for (let index = 0; index < starts.length; index += 4) {
      await Promise.all(starts.slice(index, index + 4).map(async start => {
        const end = Math.min(start + chunkSize - 1, total - 1)
        const part = await fetch(url, { headers: { Range: `bytes=${start}-${end}` }, signal: AbortSignal.timeout(45000) })
        if (part.status !== 206 || part.headers.get('content-range') !== `bytes ${start}-${end}/${total}`) throw new Error('Invalid range response')
        const data = new Uint8Array(await part.arrayBuffer())
        if (data.length !== end - start + 1) throw new Error('Incomplete image range')
        bytes.set(data, start)
      }))
      stdout.write(JSON.stringify({ completedChunks: Math.min(index + 5, starts.length + 1), totalChunks: starts.length + 1 }) + '\n')
    }
    if (bytes[0] !== 137 || bytes[1] !== 80) throw new Error('Image response is not a PNG')
    await mkdir('visual-artifacts/figma-preview-repair', { recursive: true })
    await writeFile('visual-artifacts/figma-preview-repair/mg-recovered.png', bytes)
    stdout.write(JSON.stringify({ savedBytes: bytes.length }) + '\n')
  }
} catch (error) { stdout.write(JSON.stringify({ stage: 'request', kind: error.name, code: error.cause?.code || '' }) + '\n') }
