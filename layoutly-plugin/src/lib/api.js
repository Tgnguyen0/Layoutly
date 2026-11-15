export const API_ORIGIN = 'http://localhost:8080'
const BASE = API_ORIGIN + '/api/figma'

async function request(path, { token } = {}) {
  const headers = {}
  if (token) headers['X-Figma-Token'] = token

  const res = await fetch(BASE + path, { headers })
  const text = await res.text()
  let parsed = null
  try { parsed = JSON.parse(text) } catch { /* response không phải JSON (mã nguồn thô) */ }

  if (!res.ok) {
    const error = new Error(parsed?.message || parsed?.error || text || `HTTP ${res.status}`)
    error.code = parsed?.code
    error.retryAfterSeconds = parsed?.retryAfterSeconds
    throw error
  }
  return { raw: text, json: parsed }
}

export const fileApi = {
  structure: (token, fileKey) => request(`/file/${fileKey}/structure`, { token }),
  html: (token, fileKey) => request(`/file/${fileKey}/html`, { token }),
  css: (token, fileKey) => request(`/file/${fileKey}/css`, { token }),
}

export async function fetchPreviewHtml(token, fileKey) {
  const headers = token ? { 'X-Figma-Token': token } : {}
  const res = await fetch(`${BASE}/file/${fileKey}/preview`, { headers })
  if (!res.ok) throw new Error((await res.text()) || `HTTP ${res.status}`)
  const html = await res.text()
  const base = `<base href="${API_ORIGIN}/">`
  return /<head[^>]*>/i.test(html) ? html.replace(/<head[^>]*>/i, (m) => m + base) : base + html
}

function triggerDownload(blob, filename) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

// Xuất zip gồm HTML + CSS (index.html, styles.css, assets...)
export async function downloadExport(token, fileKey, type = 'AUTO') {
  const res = await fetch(`${BASE}/file/${fileKey}/export?type=${encodeURIComponent(type)}`, {
    headers: token ? { 'X-Figma-Token': token } : {},
  })
  if (!res.ok) {
    const text = await res.text()
    let message = text || `HTTP ${res.status}`
    try { const p = JSON.parse(text); message = p?.message || p?.error || message } catch { /* giữ lỗi dạng text */ }
    throw new Error(message)
  }
  triggerDownload(await res.blob(), 'layoutly-html-css-export.zip')
}

async function readError(res) {
  const text = await res.text()
  let message = text || `HTTP ${res.status}`
  try { const p = JSON.parse(text); message = p?.message || p?.error || message } catch { /* giữ text */ }
  return new Error(message)
}

const API = API_ORIGIN + '/api'

export async function importSelection({ meta, json, assets, reference }) {
  const metadata = {
    source: 'FIGMA_PLUGIN',
    ...meta,
    assets: assets.map((a) => ({ nodeId: a.nodeId, fileName: a.fileName, contentType: 'image/png' })),
    warnings: meta?.warnings ?? [],
  }
  const fd = new FormData()
  fd.append('metadata', new Blob([JSON.stringify(metadata)], { type: 'application/json' }), 'metadata.json')
  fd.append('designJson', new Blob([JSON.stringify(json)], { type: 'application/json' }), 'design.json')
  if (reference) fd.append('referenceImage', new Blob([reference], { type: 'image/png' }), 'reference.png')
  for (const a of assets) fd.append('assets', new Blob([a.bytes], { type: 'image/png' }), a.fileName)

  const res = await fetch(`${API}/bridge/import`, { method: 'POST', body: fd })
  if (!res.ok) throw await readError(res)
  return res.json()
}

async function getText(path) {
  const res = await fetch(API + path)
  if (!res.ok) throw await readError(res)
  return res.text()
}

export const snapshotApi = {
  structure: async (id) => JSON.parse(await getText(`/snapshots/${id}/structure`)),
  html: (id) => getText(`/snapshots/${id}/html`),
  css: (id) => getText(`/snapshots/${id}/css`),
}

export async function fetchSnapshotPreview(id) {
  const html = await getText(`/snapshots/${id}/preview`)
  const base = `<base href="${API_ORIGIN}/">` // asset trong preview là đường dẫn tương đối
  return /<head[^>]*>/i.test(html) ? html.replace(/<head[^>]*>/i, (m) => m + base) : base + html
}

export async function downloadSnapshotZip(id, type = 'AUTO') {
  const res = await fetch(`${API}/snapshots/${id}/export?format=HTML&type=${encodeURIComponent(type)}`)
  if (!res.ok) throw await readError(res)
  triggerDownload(await res.blob(), 'layoutly-html-css-export.zip')
}
