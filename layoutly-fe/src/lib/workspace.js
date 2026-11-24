export function parseDesignTarget(input) {
  const value = input.trim()
  if (!value) return { fileKey: '', nodeId: '' }
  try {
    const url = new URL(value)
    if (url.hostname !== 'www.figma.com' && url.hostname !== 'figma.com') return { fileKey: '', nodeId: '' }
    return { fileKey: url.pathname.match(/\/(?:file|design|proto|board)\/([^/]+)/)?.[1] || '', nodeId: (url.searchParams.get('node-id') || '').replace(/-/g, ':') }
  } catch {
    return /^[a-zA-Z0-9]+$/.test(value) ? { fileKey: value, nodeId: '' } : { fileKey: '', nodeId: '' }
  }
}

export function flattenNodes(root) {
  if (!root) return []
  const rows = []
  const visit = (node, depth) => { rows.push({ node, depth }); for (const child of node.children || []) visit(child, depth + 1) }
  visit(root, 0)
  return rows
}

export function nodeAnchor(id) { return `node-${String(id).toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '')}` }
export function snapshotDate(value) {
  const date = new Date(value)
  return value && Number.isFinite(date.getTime()) ? new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(date) : 'Chưa xác định'
}
