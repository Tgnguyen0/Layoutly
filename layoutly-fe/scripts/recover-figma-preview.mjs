// One-off local repair helper: the Figma token is accepted through hidden stdin only.
// No credentials are written to files, shell arguments, or console output.
import { stdin, stdout } from 'node:process'

async function readSecret() {
  stdin.setRawMode?.(true)
  stdin.resume()
  stdout.write('Ready for Figma token on stdin (input hidden).\n')
  return new Promise((resolve, reject) => {
    let value = ''
    function receive(chunk) {
      for (const character of chunk.toString()) {
        if (character === '\r' || character === '\n') {
          stdin.removeListener('data', receive)
          stdin.setRawMode?.(false)
          stdin.pause()
          resolve(value.trim())
          return
        }
        if (character === '\u0003') { stdin.pause(); reject(new Error('Cancelled')); return }
        if (character === '\u007f' || character === '\b') value = value.slice(0, -1)
        else value += character
      }
    }
    stdin.on('data', receive)
  })
}

const token = await readSecret()
if (!token || token.length > 512) throw new Error('Invalid token input')
for (const target of [
  { label: 'MNTN repair', fileKey: 'BSQn8vpxTnLeIl69e2rKTw', nodeId: '0:0' },
  { label: 'Requested design', fileKey: '3grnIUjEav6wZ6LHMukuMs', nodeId: '0:1' },
]) {
  try {
    const response = await fetch('http://127.0.0.1:8080/api/figma/import', {
      method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Figma-Token': token },
      body: JSON.stringify({ fileKey: target.fileKey, nodeId: target.nodeId }), signal: AbortSignal.timeout(240000),
    })
    const data = await response.json()
    if (!response.ok) { stdout.write(JSON.stringify({ label: target.label, status: response.status, message: data.message || data.error || 'Import failed' }) + '\n'); continue }
    stdout.write(JSON.stringify({ label: target.label, snapshotId: data.snapshotId, fileName: data.fileName, rootNodeId: data.rootNodeId }) + '\n')
    // The first import can restore raw JSON from disk; repeat once with the same
    // in-memory identity to retry assets omitted by older partial exports.
    const hydrate = await fetch('http://127.0.0.1:8080/api/figma/import', {
      method: 'POST', headers: { 'Content-Type': 'application/json', 'X-Figma-Token': token },
      body: JSON.stringify({ fileKey: target.fileKey, nodeId: target.nodeId }), signal: AbortSignal.timeout(240000),
    })
    if (!hydrate.ok) {
      const failure = await hydrate.json().catch(() => ({}))
      stdout.write(JSON.stringify({ label: target.label, hydrationStatus: hydrate.status, message: failure.error || failure.message || 'Image recovery failed' }) + '\n')
    } else await hydrate.text()
    // Await persisted image bytes, not merely an in-memory list of Figma URLs.
    const preview = await fetch(`http://127.0.0.1:8080/api/snapshots/${encodeURIComponent(data.snapshotId)}/preview?targetWidth=1440`, { signal: AbortSignal.timeout(240000) })
    await preview.text()
    stdout.write(JSON.stringify({ label: target.label, previewStatus: preview.status, missingAssets: preview.headers.get('X-Layoutly-Missing-Assets') }) + '\n')
  } catch (error) { stdout.write(JSON.stringify({ label: target.label, error: error.name === 'TimeoutError' ? 'Timed out while downloading design assets' : 'Could not complete the import' }) + '\n') }
}
