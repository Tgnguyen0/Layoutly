import { act, renderHook, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { AccountProvider, useAccount } from '../../../src/contexts/AccountContext.jsx'
import { accountApi } from '../../../src/lib/accounts.js'

vi.mock('../../../src/lib/accounts.js', () => ({ accountApi: { me: vi.fn(), logout: vi.fn() } }))
const wrapper = ({ children }) => <AccountProvider>{children}</AccountProvider>

describe('account session state', () => {
  it('restores a real server session', async () => {
    accountApi.me.mockResolvedValue({ user: { id: 'one', displayName: 'Thanh' } })
    const { result } = renderHook(useAccount, { wrapper })
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.user.displayName).toBe('Thanh')
  })
  it('does not overwrite a new login with a stale guest response', async () => {
    let finish
    accountApi.me.mockImplementation(() => new Promise(resolve => { finish = resolve }))
    const { result } = renderHook(useAccount, { wrapper })
    act(() => result.current.setUser({ id: 'signed-in' }))
    await act(async () => finish({ user: null }))
    expect(result.current.user.id).toBe('signed-in')
  })
  it('retains the session when logout fails and clears it only after success', async () => {
    accountApi.me.mockResolvedValue({ user: { id: 'one' } })
    accountApi.logout.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce(null)
    const { result } = renderHook(useAccount, { wrapper })
    await waitFor(() => expect(result.current.loading).toBe(false))
    await expect(result.current.logout()).rejects.toThrow('offline')
    expect(result.current.user.id).toBe('one')
    await act(async () => result.current.logout())
    expect(result.current.user).toBeNull()
    expect(window.location.hash).toBe('#/login')
  })
})
