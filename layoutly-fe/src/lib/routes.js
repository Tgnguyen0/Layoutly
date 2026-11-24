export function readRoute(location = window.location) {
  if (new URLSearchParams(location.search).get('snapshotId')) return '/workspace'
  if (location.pathname !== '/') return location.pathname.replace(/\/$/, '')
  const hashPath = location.hash.split('?')[0]
  if (hashPath.startsWith('#/')) return hashPath.slice(1)
  if (['#workspace', '#converter', '#figma-connection', '#exports', '#ai-review'].includes(hashPath)) {
    return '/workspace'
  }
  return '/'
}