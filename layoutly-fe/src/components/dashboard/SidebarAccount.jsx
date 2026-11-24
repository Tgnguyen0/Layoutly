import { LogOut } from 'lucide-react'
import useAuth from '../../hooks/useAuth.js'
import { userInitial } from '../../lib/auth.js'
import '../../styles/account.css'

/** Khối tài khoản trong sidebar dashboard (dashboard không có header công khai). */
export default function SidebarAccount() {
  const { user, logout, busy, error } = useAuth()
  if (user === undefined) return <div className="db-user db-user-loading" aria-hidden="true" />
  if (user === null) return <a href="/#/login" className="ly-button ly-button-quiet db-user-login">Đăng nhập</a>
  return <div className="db-user">
    <div className="db-user-info">
      <span className="ly-avatar" aria-hidden="true">{userInitial(user)}</span>
      <div><strong>{user.displayName}</strong><span>{user.email}</span></div>
    </div>
    <button type="button" className="db-logout" onClick={logout} disabled={busy}><LogOut size={14} />{busy ? 'Đang đăng xuất…' : 'Đăng xuất'}</button>
    {error && <p className="ly-account-error" role="alert">{error}</p>}
  </div>
}
