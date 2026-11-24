import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { ChevronDown, LayoutDashboard, LogOut } from 'lucide-react'
import useAuth from '../hooks/useAuth.js'
import { userInitial } from '../lib/auth.js'
import '../styles/account.css'

/**
 * Chưa đăng nhập: liên kết "Đăng nhập". Đã đăng nhập: avatar + menu (dashboard, đăng xuất).
 * showName: hiện thêm tên cạnh avatar trên màn hình rộng (dùng ở header công khai).
 */
export default function UserMenu({ showName = false, loginClassName = 'ly-button ly-button-quiet ly-login-link' }) {
  const { user, logout, busy, error } = useAuth()
  const [open, setOpen] = useState(false)
  const root = useRef(null)
  const trigger = useRef(null)
  const menu = useRef(null)

  useEffect(() => { if (!user) setOpen(false) }, [user])

  // Menu căn theo mép phải avatar; trên màn hình hẹp avatar nằm giữa hàng nút nên menu có thể tràn mép.
  // Đo lại khi mở và dịch vào trong viewport (cách này đúng với mọi vị trí đặt UserMenu).
  useLayoutEffect(() => {
    const element = menu.current
    if (!open || !element) return
    element.style.transform = ''
    const { left, right } = element.getBoundingClientRect()
    const margin = 8
    const shift = left < margin ? margin - left : right > window.innerWidth - margin ? window.innerWidth - margin - right : 0
    if (shift) element.style.transform = `translateX(${shift}px)`
  }, [open])

  useEffect(() => {
    if (!open) return
    root.current?.querySelector('[role="menuitem"]')?.focus()
    const onMouseDown = event => { if (!root.current?.contains(event.target)) setOpen(false) }
    const onKeyDown = event => { if (event.key === 'Escape') { setOpen(false); trigger.current?.focus() } }
    document.addEventListener('mousedown', onMouseDown)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('mousedown', onMouseDown)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [open])

  function onMenuKeyDown(event) {
    if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') return
    const items = [...root.current.querySelectorAll('[role="menuitem"]:not(:disabled)')]
    if (!items.length) return
    event.preventDefault()
    const index = items.indexOf(document.activeElement)
    const down = event.key === 'ArrowDown'
    const next = index < 0 ? (down ? 0 : items.length - 1) : (index + (down ? 1 : -1) + items.length) % items.length
    items[next].focus()
  }

  if (user === undefined) return <span className="ly-avatar-skeleton" aria-hidden="true" />
  if (user === null) return <a href="/#/login" className={loginClassName}>Đăng nhập</a>

  const label = user.displayName || user.email
  return <div ref={root} className="ly-account">
    <button ref={trigger} type="button" className="ly-avatar-button" aria-haspopup="menu" aria-expanded={open} aria-label={`Tài khoản của ${label}`} title={user.email} onClick={() => setOpen(value => !value)}>
      <span className="ly-avatar" aria-hidden="true">{userInitial(user)}</span>
      {showName && <span className="ly-avatar-name">{label}</span>}
      <ChevronDown size={14} aria-hidden="true" />
    </button>
    {open && <div ref={menu} className="ly-account-menu" role="menu" aria-label="Tài khoản" onKeyDown={onMenuKeyDown}>
      <div className="ly-account-who" role="none"><strong>{user.displayName}</strong><span>{user.email}</span></div>
      <a role="menuitem" href="/#/dashboard" onClick={() => setOpen(false)}><LayoutDashboard size={15} />Dự án của tôi</a>
      <button type="button" role="menuitem" className="ly-account-logout" onClick={logout} disabled={busy}><LogOut size={15} />{busy ? 'Đang đăng xuất…' : 'Đăng xuất'}</button>
      {error && <p className="ly-account-error" role="alert">{error}</p>}
    </div>}
  </div>
}
