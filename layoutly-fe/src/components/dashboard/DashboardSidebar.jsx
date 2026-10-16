import { useState } from 'react'
import { File, FolderPlus, LogOut, Settings, User, X } from 'lucide-react'
import Brand from '../public/Brand.jsx'
import { initials, useAccount } from '../../contexts/AccountContext.jsx'

export default function DashboardSidebar({ onClose }) {
  const { user, logout } = useAccount()
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  async function signOut() {
    setBusy(true); setError('')
    try { await logout() } catch (caught) { setError(caught.message) } finally { setBusy(false) }
  }
  return <aside className="db-sidebar" aria-label="Điều hướng dự án">
    <div className="db-brand"><Brand /><button className="we-icon db-close" type="button" aria-label="Đóng sidebar" onClick={onClose}><X size={17} /></button></div>
    <div className="db-nav-group"><span className="db-nav-label">KHÔNG GIAN LÀM VIỆC</span><nav><a href="/#/dashboard" aria-current="page"><File size={17} />Dự án của tôi</a><a href="/#/workspace?import=1"><FolderPlus size={17} />Nhập thiết kế</a></nav></div>
    <div className="db-account"><span>TÀI KHOẢN</span><a href="/#/account"><User size={17} />Thông tin cá nhân</a><a href="/#/settings"><Settings size={17} />Bảo mật & đăng nhập</a>{user && <button type="button" disabled={busy} onClick={signOut}><LogOut size={17} />{busy ? 'Đang đăng xuất…' : 'Đăng xuất'}</button>}{error && <p className="ly-field-error" role="alert">{error}</p>}</div>
    <div className="db-local">{user ? <a className="db-user" href="/#/account"><span className="ac-avatar">{initials(user.displayName)}</span><div><strong>{user.displayName}</strong><p>{user.email}</p></div></a> : <><strong>Không gian của bạn</strong><p>Đăng nhập để quản lý thông tin cá nhân và bảo mật tài khoản.</p><a href="/#/login" className="ly-button ly-button-primary">Đăng nhập <User size={14} /></a></>}</div>
  </aside>
}
