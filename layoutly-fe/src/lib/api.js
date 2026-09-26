const BASE = '/api'

async function request(path, { token, method = 'GET', body } = {}) {
  const headers = {}
  if (token) headers['X-Figma-Token'] = token
  if (body) headers['Content-Type'] = 'application/json'

  const res = await fetch(BASE + path, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  })

  const text = await res.text()
  let parsed = null
  try {
    parsed = JSON.parse(text)
  } catch {
    // response khong phai JSON (vi du file binary khi export) -> tra ve text tho
  }

  if (!res.ok) {
    const message = parsed?.message || parsed?.error || text || `HTTP ${res.status}`
    const error = new Error(message)
    error.code = parsed?.code
    error.retryAfterSeconds = parsed?.retryAfterSeconds
    error.retryAt = parsed?.retryAt
    error.planTier = parsed?.planTier
    error.rateLimitType = parsed?.rateLimitType
    error.upgradeUrl = parsed?.upgradeUrl
    throw error
  }

  return { raw: text, json: parsed, status: res.status }
}

export const figmaApi = {
  me: (token) => request('/figma/me', { token }),
  file: (token, fileKey) => request(`/figma/file/${fileKey}`, { token }),
  nodes: (token, fileKey, ids) =>
    request(`/figma/file/${fileKey}/nodes?ids=${encodeURIComponent(ids)}`, { token }),
  images: (token, fileKey, ids, format) =>
    request(`/figma/file/${fileKey}/images?ids=${encodeURIComponent(ids)}&format=${format}`, {
      token,
    }),
  components: (token, fileKey) => request(`/figma/file/${fileKey}/components`, { token }),
  styles: (token, fileKey) => request(`/figma/file/${fileKey}/styles`, { token }),
  tree: (token, fileKey) => request(`/figma/file/${fileKey}/tree`, { token }),
  html: (token, fileKey) => request(`/figma/file/${fileKey}/html`, { token }),
  react: (token, fileKey) => request(`/figma/file/${fileKey}/react`, { token }),
  preview: (token, fileKey) => request(`/figma/file/${fileKey}/preview`, { token }),
}

export async function exportFile(type, filename, content) {
  const res = await fetch(`${BASE}/export/${type}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ filename, content }),
  })
  if (!res.ok) {
    const errText = await res.text()
    throw new Error(errText || `HTTP ${res.status}`)
  }
  return res.blob()
}

export function downloadTextAsTxt(content, filename) {
  const blob = new Blob([content], { type: 'text/plain;charset=utf-8' })
  triggerDownload(blob, filename + '.txt')
}

export function downloadTextAsJson(content, filename) {
  const blob = new Blob([content], { type: 'text/plain;charset=utf-8'})
  triggerDownload(blob, filename + '.json')
}

export function triggerDownload(blob, filename) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}

export async function downloadZipExport(token, fileKey, type = 'AUTO') {
  const headers = {}
  if (token) headers['X-Figma-Token'] = token

  const res = await fetch(`${BASE}/figma/file/${fileKey}/export?type=${type}`, { headers })

  if (!res.ok) {
    const text = await res.text()
    let message = text || `HTTP ${res.status}`
    try {
      message = JSON.parse(text)?.error || message
    } catch {
      // Keep the plain-text backend error
    }
    throw new Error(message)
  }

  const blob = await res.blob()
  triggerDownload(blob, `${fileKey}-export.zip`)
}

export const snapshotApi = {
  bridgeStatus: () => request('/bridge/status'),
  importFigma: (token, fileKey) => request('/figma/import', {
    token,
    method: 'POST',
    body: { fileKey },
  }),
  refresh: (token, snapshotId) => request(`/snapshots/${snapshotId}/refresh`, {
    token,
    method: 'POST',
  }),
  get: (snapshotId) => request(`/snapshots/${snapshotId}`),
  tree: (snapshotId) => request(`/snapshots/${snapshotId}/tree`),
  structure: (snapshotId) => request(`/snapshots/${snapshotId}/structure`),
  html: (snapshotId) => request(`/snapshots/${snapshotId}/html`),
  css: (snapshotId) => request(`/snapshots/${snapshotId}/css`),
  react: (snapshotId) => request(`/snapshots/${snapshotId}/react`),
  saveFixture: (snapshotId, caseName) => request(`/snapshots/${snapshotId}/fixture`, {
    method: 'POST',
    body: { caseName },
  }),
}

export async function downloadReactZipExport(token, fileKey) {
  const headers = {}
  if (token) headers['X-Figma-Token'] = token

  const res = await fetch(`${BASE}/figma/file/${fileKey}/export/react`, { headers })
  if (!res.ok) {
    const text = await res.text()
    let message = text || `HTTP ${res.status}`
    try {
      message = JSON.parse(text)?.error || message
    } catch {
      // Keep the plain-text backend error.
    }
    throw new Error(message)
  }

  const blob = await res.blob()
  triggerDownload(blob, `${fileKey}-react.zip`)
}

export async function downloadSnapshotExport(snapshotId, format, type = 'AUTO') {
  const query = new URLSearchParams({ format, type })
  const res = await fetch(`${BASE}/snapshots/${snapshotId}/export?${query}`)
  if (!res.ok) {
    const text = await res.text()
    let message = text || `HTTP ${res.status}`
    try {
      const parsed = JSON.parse(text)
      message = parsed?.message || parsed?.error || message
    } catch {
      // Keep the plain-text backend error.
    }
    throw new Error(message)
  }

  const blob = await res.blob()
  triggerDownload(blob, `layoutly-${format.toLowerCase()}-export.zip`)
}
