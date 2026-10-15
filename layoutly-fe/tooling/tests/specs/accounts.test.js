import { describe, expect, it, vi } from 'vitest'
import { accountCapabilities, submitAccountForm, validateAccountForm } from '../../../src/lib/accounts.js'

const values = { name: 'Nguyen Van A', email: 'user@example.com', password: 'a-long-password', confirmPassword: 'a-long-password', consent: true, remember: true }
describe('account contract gates', () => {
  it('treats missing auth backend as unavailable', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ status: 404 }))
    expect(await accountCapabilities()).toEqual({ login: false, registration: false, passwordReset: false })
  })
  it('requires exact true capability values and contract version', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => ({ contractVersion: 1, login: 'true', registration: true }) }))
    expect(await accountCapabilities()).toEqual({ login: false, registration: true, passwordReset: false })
    fetch.mockResolvedValue({ ok: true, json: async () => ({ contractVersion: 2 }) })
    await expect(accountCapabilities()).rejects.toThrow('Phiên bản')
  })
  it('never sends credentials when auth is unavailable', async () => {
    vi.stubGlobal('fetch', vi.fn())
    await expect(submitAccountForm('login', values, { login: false })).rejects.toThrow('chưa khả dụng')
    expect(fetch).not.toHaveBeenCalled()
  })
  it('does not send credentials if CSRF initialization fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false }))
    await expect(submitAccountForm('login', values, { login: true })).rejects.toThrow('phiên bảo mật')
    expect(fetch).toHaveBeenCalledTimes(1)
    expect(fetch.mock.calls[0][0]).toBe('/api/auth/csrf')
  })
  it('rejects unexpected CSRF header names', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => ({ headerName: 'X-Host', token: 'secret' }) }))
    await expect(submitAccountForm('login', values, { login: true })).rejects.toThrow('không hợp lệ')
    expect(fetch).toHaveBeenCalledTimes(1)
  })
  it('uses same-origin cookies and a CSRF token for enabled services', async () => {
    const result = { user: { id: 'user-1' } }
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => ({ headerName: 'X-CSRF-TOKEN', token: 'csrf-token' }) })
      .mockResolvedValueOnce({ ok: true, status: 200, json: async () => result }))
    expect(await submitAccountForm('login', values, { login: true })).toEqual(result)
    const [url, options] = fetch.mock.calls[1]
    expect(url).toBe('/api/auth/login')
    expect(options.credentials).toBe('same-origin')
    expect(options.headers['X-CSRF-TOKEN']).toBe('csrf-token')
    expect(JSON.parse(options.body)).toEqual({ email: values.email, password: values.password, rememberMe: true })
  })
  it('does not reflect untrusted backend error HTML', async () => {
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce({ ok: true, json: async () => ({ headerName: 'X-CSRF-TOKEN', token: 't' }) })
      .mockResolvedValueOnce({ ok: false, status: 500, text: async () => '<script>bad()</script>' }))
    await expect(submitAccountForm('register', values, { registration: true })).rejects.toThrow('Không thể hoàn tất')
  })
})
describe('form validation', () => {
  it('accepts a valid registration', () => expect(validateAccountForm('register', values)).toEqual({}))
  it('validates name, email, length, confirmation and consent', () => {
    const errors = validateAccountForm('register', { ...values, name: '', email: 'invalid', password: 'short', confirmPassword: '', consent: false })
    expect(Object.keys(errors).sort()).toEqual(['confirmPassword', 'consent', 'email', 'name', 'password'])
  })
  it('does not apply new-password requirements to existing login passwords', () => {
    expect(validateAccountForm('login', { ...values, password: 'old' })).toEqual({})
  })
  it('validates forgot-password email without requiring a password', () => {
    expect(validateAccountForm('forgot', { ...values, password: '' })).toEqual({})
  })
})
