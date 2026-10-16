const BASE = '/api'

async function request(path, { token, method = 'GET', body } = {}) {
  const headers = {}
  if (!['GET', 'HEAD'].includes(method)) Object.assign(headers, await csrfHeaders())
  if (token) headers['X-Figma-Token'] = token
  if (body) headers['Content-Type'] = 'application/json'

  const res = await fetch(BASE + path, {
    method,
    credentials: 'same-origin',
    cache: 'no-store',
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
    error.status = res.status
    error.retryAfterSeconds = parsed?.retryAfterSeconds
    error.retryAt = parsed?.retryAt
    error.planTier = parsed?.planTier
    error.rateLimitType = parsed?.rateLimitType
    error.upgradeUrl = parsed?.upgradeUrl
    throw error
  }

  return { raw: text, json: parsed, status: res.status }
}

export { request as apiRequest }
let csrfFlight = null
export async function csrfHeaders() {
  // Concurrent first mutations must share one handshake while the browser receives JSESSIONID.
  if (!csrfFlight) csrfFlight = (async () => {
    const response = await fetch('/api/auth/csrf', { credentials: 'same-origin', cache: 'no-store' })
    if (!response.ok) throw new Error('Không thể khởi tạo phiên bảo mật.')
    const csrf = await response.json()
    if (csrf.headerName !== 'X-CSRF-TOKEN' || !csrf.token) throw new Error('Phiên bảo mật không hợp lệ.')
    return { 'X-CSRF-TOKEN': csrf.token }
  })().finally(() => { csrfFlight = null })
  return csrfFlight
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
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json', ...await csrfHeaders() },
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
  importFigma: (token, target) => request('/figma/import', {
    token,
    method: 'POST',
    body: typeof target === 'string' ? { fileKey: target } : target,
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
  aiStatus: (snapshotId) => request(`/snapshots/${snapshotId}/ai/status`),
  aiConversation: (snapshotId) => request(`/snapshots/${snapshotId}/ai/conversation`),
  aiAccepted: (snapshotId) => request(`/snapshots/${snapshotId}/ai/accepted`),
  aiFiles: (snapshotId, revisionId, framework = 'REACT') => request(`/snapshots/${snapshotId}/ai/files?${new URLSearchParams({ ...(revisionId ? { revisionId } : {}), framework })}`),
  aiFile: (snapshotId, path, revisionId, framework = 'REACT') => request(aiFilePath(snapshotId, path, revisionId, framework)),
  aiChat: (snapshotId, data) => request(`/snapshots/${snapshotId}/ai/chat`, { method: 'POST', body: data }),
  aiRevisions: (snapshotId) => request(`/snapshots/${snapshotId}/ai/revisions`),
  aiDiff: (snapshotId, revisionId, framework = 'REACT') => request(`/snapshots/${snapshotId}/ai/revisions/${revisionId}/diff?framework=${framework}`),
  aiAccept: (snapshotId, revisionId, framework = 'REACT') => request(`/snapshots/${snapshotId}/ai/revisions/${revisionId}/accept?framework=${framework}`, { method: 'POST' }),
  aiReject: (snapshotId, revisionId) => request(`/snapshots/${snapshotId}/ai/revisions/${revisionId}/reject`, { method: 'POST' }),
}

export function aiFilePath(snapshotId, path, revisionId, framework = 'REACT') {
  return `/snapshots/${snapshotId}/ai/file?${new URLSearchParams({ path, framework, ...(revisionId ? { revisionId } : {}) })}`
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

export async function downloadAiExport(snapshotId, revisionId, framework = 'REACT') {
  const query = new URLSearchParams()
  if (revisionId) query.set('revisionId', revisionId)
  if (framework) query.set('framework', framework)
  const res = await fetch(`${BASE}/snapshots/${snapshotId}/ai/export?${query.toString()}`)
  if (!res.ok) {
    const text = await res.text()
    throw new Error(text || `HTTP ${res.status}`)
  }
  const blob = await res.blob()
  triggerDownload(blob, `layoutly-ai-${framework.toLowerCase()}-export.zip`)
}
