import { useEffect, useRef, useState } from 'react'
import { ArrowUpRight, Menu, X } from 'lucide-react'
import Brand from './Brand.jsx'
import { initials, useAccount } from '../../contexts/AccountContext.jsx'

const links = [['Trang chủ', '/'], ['Dự án', '/#/dashboard'], ['Tính năng', '/#grow']]

export default function PublicHeader({ route }) {
  const { user } = useAccount()
  const [open, setOpen] = useState(false)
  const trigger = useRef(null)
  const drawer = useRef(null)
  useEffect(() => {
    setOpen(false)
  }, [route])
  useEffect(() => {
    if (!open) return
    drawer.current?.querySelector('a')?.focus()
    const escape = (event) => {
      if (event.key === 'Escape') { setOpen(false); trigger.current?.focus() }
    }
    document.addEventListener('keydown', escape)
    return () => document.removeEventListener('keydown', escape)
  }, [open])
  return (
    <header className="ly-header">
      <div className="ly-header-inner">
        <Brand />
        <nav className="ly-desktop-nav" aria-label="Điều hướng chính">
          {links.map(([label, href]) => <a key={label} href={href}>{label}</a>)}
        </nav>
        <div className="ly-header-actions">
          {user ? <a href="/#/account" className="ly-header-account" aria-label="Quản lý tài khoản"><span className="ac-avatar">{initials(user.displayName)}</span><span>{user.displayName}</span></a> : <a href="/#/login" className="ly-button ly-button-quiet ly-login-link" aria-current={route === '/login' ? 'page' : undefined}>Đăng nhập</a>}
          <a href={user ? '/#/dashboard' : '/#/register'} className="ly-button ly-button-accent">{user ? 'Vào không gian làm việc' : 'Tạo tài khoản'} <ArrowUpRight size={16} /></a>
          <button className="ly-icon-button ly-menu-trigger" ref={trigger} type="button" aria-label={open ? 'Đóng menu' : 'Mở menu'} aria-expanded={open} aria-controls="public-mobile-menu" onClick={() => setOpen(!open)}>
            {open ? <X size={21} /> : <Menu size={21} />}
          </button>
        </div>
      </div>
      {open && <nav ref={drawer} id="public-mobile-menu" className="ly-mobile-nav" aria-label="Điều hướng di động">
        {links.map(([label, href]) => <a key={label} href={href} onClick={() => setOpen(false)}>{label}</a>)}
        {user ? <a href="/#/account" onClick={() => setOpen(false)}>Tài khoản của tôi</a> : <><a href="/#/login" onClick={() => setOpen(false)}>Đăng nhập</a><a href="/#/register" onClick={() => setOpen(false)}>Đăng ký</a></>}
      </nav>}
    </header>
  )
}
