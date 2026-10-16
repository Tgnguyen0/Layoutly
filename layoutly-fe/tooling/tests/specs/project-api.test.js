import { describe, it, expect, vi, afterEach } from 'vitest'
import { apiRequest } from '../../../src/lib/api.js'
afterEach(() => vi.unstubAllGlobals())
describe('project API security', () => {
  it('shares the first CSRF handshake for concurrent project restores', async () => {
    const fetcher = vi.fn(async path => path === '/api/auth/csrf'
      ? { ok: true, json: async () => ({ headerName: 'X-CSRF-TOKEN', token: 'csrf' }) }
      : { ok: true, status: 200, text: async () => '{"project":{"id":"one"}}' })
    vi.stubGlobal('fetch', fetcher)
    await Promise.all([apiRequest('/projects/one/reopen', { method: 'POST' }), apiRequest('/projects/one/reopen', { method: 'POST' })])
    expect(fetcher.mock.calls.filter(([path]) => path === '/api/auth/csrf')).toHaveLength(1)
    for (const [path, options] of fetcher.mock.calls.filter(([path]) => path.includes('/projects/'))) {
      expect(options.headers['X-CSRF-TOKEN']).toBe('csrf')
      expect(options.credentials).toBe('same-origin')
    }
  })
})
