export function readRoute(location = window.location) {
  if (new URLSearchParams(location.search).get('snapshotId')) return '/workspace'
  if (location.pathname !== '/') return location.pathname.replace(/\/$/, '')
  if (location.hash.startsWith('#/')) return location.hash.slice(1).split('?')[0]
  if (['#workspace', '#converter', '#figma-connection', '#exports', '#ai-review'].includes(location.hash)) {
    return '/workspace'
  }
  return '/'
}
