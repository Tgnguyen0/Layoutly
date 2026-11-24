import { useCallback, useEffect, useState, useSyncExternalStore } from 'react'
import { getAuthState, loadCurrentUser, signOut, subscribeAuth } from '../lib/auth.js'

/**
 * Trạng thái đăng nhập hiện tại + hành động đăng xuất.
 * user: undefined (đang tải) | null (chưa đăng nhập) | { id, displayName, email }.
 */
export default function useAuth() {
  const { user } = useSyncExternalStore(subscribeAuth, getAuthState, getAuthState)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => { loadCurrentUser() }, [])

  const logout = useCallback(async () => {
    setBusy(true)
    setError('')
    try {
      await signOut()
      window.location.assign('/#/login')
    } catch (caught) {
      setError(caught?.message || 'Không thể đăng xuất. Vui lòng thử lại.')
    } finally {
      setBusy(false)
    }
  }, [])

  return { user, logout, busy, error }
}
