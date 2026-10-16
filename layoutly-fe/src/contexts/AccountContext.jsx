import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'
import { accountApi } from '../lib/accounts.js'

const AccountContext = createContext({ user: null, loading: false, error: '', setUser: () => {}, refresh: () => {}, logout: async () => {} })

export function AccountProvider({ children }) {
  const [user, commitUser] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const generation = useRef(0)
  const setUser = useCallback(value => {
    generation.current += 1
    commitUser(value)
    setLoading(false)
    setError('')
  }, [])
  const refresh = useCallback(async (signal) => {
    const request = ++generation.current
    setLoading(true)
    setError('')
    try {
      const result = await accountApi.me(signal)
      if (request === generation.current && !signal?.aborted) commitUser(result.user)
    } catch (caught) {
      if (request === generation.current && caught.name !== 'AbortError') {
        if (caught.status === 401) commitUser(null)
        setError(caught.message)
      }
    } finally { if (request === generation.current && !signal?.aborted) setLoading(false) }
  }, [])
  useEffect(() => {
    const controller = new AbortController()
    refresh(controller.signal)
    // Recheck when returning to a tab, including a sign-out in another tab.
    const onFocus = () => refresh(controller.signal)
    window.addEventListener('focus', onFocus)
    return () => { controller.abort(); window.removeEventListener('focus', onFocus) }
  }, [refresh])
  async function logout() {
    await accountApi.logout()
    sessionStorage.removeItem('layoutly_snapshot_id')
    sessionStorage.removeItem('layoutly_project_id')
    setUser(null)
    window.location.hash = '#/login'
  }
  return <AccountContext.Provider value={{ user, loading, error, setUser, refresh, logout }}>{children}</AccountContext.Provider>
}

export const useAccount = () => useContext(AccountContext)
export function initials(name = '') { return name.trim().split(/\s+/).slice(-2).map(part => Array.from(part)[0] || '').join('').toUpperCase() || 'L' }
