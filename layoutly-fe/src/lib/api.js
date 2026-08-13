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
    const message = parsed?.error || text || `HTTP ${res.status}`
    throw new Error(message)
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
