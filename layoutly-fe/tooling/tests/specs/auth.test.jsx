import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getAuthState, loadCurrentUser, resetAuth, setCurrentUser, signOut, userInitial } from '../../../src/lib/auth.js'
import UserMenu from '../../../src/components/UserMenu.jsx'

const user = { id: 'user-1', displayName: 'Nguyễn Văn A', email: 'a@example.com' }
const json = body => ({ ok: true, status: 200, json: async () => body })
const csrf = json({ headerName: 'X-CSRF-TOKEN', token: 'csrf-token' })

function backend({ signedIn = true, logoutOk = true } = {}) {
  return vi.fn(async url => {
    if (url === '/api/auth/me') return signedIn ? json({ user }) : { ok: false, status: 401 }
    if (url === '/api/auth/csrf') return csrf
    if (url === '/api/auth/logout') return logoutOk ? { ok: true, status: 204 } : { ok: false, status: 500 }
    throw new Error(`unexpected request ${url}`)
  })
}

beforeEach(() => { resetAuth() })

describe('auth store', () => {
  it('loads the signed-in user from /api/auth/me with cookies', async () => {
    vi.stubGlobal('fetch', backend())
    await loadCurrentUser()
    expect(getAuthState()).toEqual({ user, status: 'ready' })
    expect(fetch.mock.calls[0][1].credentials).toBe('same-origin')
  })
  it('treats 401 as signed out, not as an error', async () => {
    vi.stubGlobal('fetch', backend({ signedIn: false }))
    await loadCurrentUser()
    expect(getAuthState()).toEqual({ user: null, status: 'ready' })
  })
  it('sends a single /me request for concurrent callers and none once ready', async () => {
    vi.stubGlobal('fetch', backend())
    await Promise.all([loadCurrentUser(), loadCurrentUser(), loadCurrentUser()])
    await loadCurrentUser()
    expect(fetch).toHaveBeenCalledTimes(1)
  })
  it('falls back to signed out when the backend is unreachable and retries later', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValueOnce(new Error('offline')).mockImplementation(backend()))
    await loadCurrentUser()
    expect(getAuthState()).toEqual({ user: null, status: 'error' })
    await loadCurrentUser()
    expect(getAuthState().user).toEqual(user)
  })
  it('ignores a /me response that arrives after a login set the user', async () => {
    let finish
    vi.stubGlobal('fetch', vi.fn(() => new Promise(resolve => { finish = resolve })))
    const loading = loadCurrentUser()
    setCurrentUser(user)
    finish({ ok: false, status: 401 })
    await loading
    expect(getAuthState().user).toEqual(user)
  })
  it('signs out with a CSRF header and clears the user only after the backend confirms', async () => {
    vi.stubGlobal('fetch', backend())
    setCurrentUser(user)
    await signOut()
    const [url, options] = fetch.mock.calls.find(([target]) => target === '/api/auth/logout')
    expect(url).toBe('/api/auth/logout')
    expect(options.method).toBe('POST')
    expect(options.headers['X-CSRF-TOKEN']).toBe('csrf-token')
    expect(getAuthState().user).toBeNull()
  })
  it('keeps the user signed in when logout fails', async () => {
    vi.stubGlobal('fetch', backend({ logoutOk: false }))
    setCurrentUser(user)
    await expect(signOut()).rejects.toThrow('Không thể đăng xuất')
    expect(getAuthState().user).toEqual(user)
  })
  it('builds an avatar initial from the display name or email', () => {
    expect(userInitial(user)).toBe('N')
    expect(userInitial({ email: 'bob@example.com' })).toBe('B')
    expect(userInitial(null)).toBe('?')
  })
})

describe('UserMenu', () => {
  it('links to the login page when signed out', async () => {
    vi.stubGlobal('fetch', backend({ signedIn: false }))
    render(<UserMenu />)
    expect(await screen.findByRole('link', { name: 'Đăng nhập' })).toHaveAttribute('href', '/#/login')
  })
  it('shows the signed-in user and signs out from the menu', async () => {
    vi.stubGlobal('fetch', backend())
    const person = userEvent.setup()
    render(<UserMenu />)
    await person.click(await screen.findByRole('button', { name: /Tài khoản của Nguyễn Văn A/ }))
    expect(screen.getByText('a@example.com')).toBeInTheDocument()
    expect(screen.getByRole('menuitem', { name: 'Dự án của tôi' })).toHaveAttribute('href', '/#/dashboard')
    await person.click(screen.getByRole('menuitem', { name: 'Đăng xuất' }))
    await waitFor(() => expect(fetch).toHaveBeenCalledWith('/api/auth/logout', expect.objectContaining({ method: 'POST' })))
    expect(await screen.findByRole('link', { name: 'Đăng nhập' })).toBeInTheDocument()
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  })
  it('closes with Escape and returns focus to the trigger', async () => {
    vi.stubGlobal('fetch', backend())
    const person = userEvent.setup()
    render(<UserMenu />)
    const trigger = await screen.findByRole('button', { name: /Tài khoản của/ })
    await person.click(trigger)
    expect(screen.getByRole('menu')).toBeInTheDocument()
    await person.keyboard('{Escape}')
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
    expect(trigger).toHaveFocus()
  })
  it('shows an error and stays signed in when logout fails', async () => {
    vi.stubGlobal('fetch', backend({ logoutOk: false }))
    const person = userEvent.setup()
    render(<UserMenu />)
    await person.click(await screen.findByRole('button', { name: /Tài khoản của/ }))
    await person.click(screen.getByRole('menuitem', { name: 'Đăng xuất' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('Không thể đăng xuất')
    expect(screen.getByRole('menu')).toBeInTheDocument()
  })
})
