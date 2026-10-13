import { useEffect, useRef, useState } from 'react'
import { ArrowUpRight, Menu, X } from 'lucide-react'
import Brand from './Brand.jsx'

const links = [['Home', '/'], ['Work', '/#/dashboard'], ['Grow', '/#grow'], ['Stay', '/#stay']]

export default function PublicHeader({ route }) {
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
          <a href="/#/login" className="ly-button ly-button-quiet ly-login-link" aria-current={route === '/login' ? 'page' : undefined}>Đăng nhập</a>
          <a href="/#workspace" className="ly-button ly-button-accent">Bắt đầu miễn phí <ArrowUpRight size={16} /></a>
          <button className="ly-icon-button ly-menu-trigger" ref={trigger} type="button" aria-label={open ? 'Đóng menu' : 'Mở menu'} aria-expanded={open} aria-controls="public-mobile-menu" onClick={() => setOpen(!open)}>
            {open ? <X size={21} /> : <Menu size={21} />}
          </button>
        </div>
      </div>
      {open && <nav ref={drawer} id="public-mobile-menu" className="ly-mobile-nav" aria-label="Điều hướng di động">
        {links.map(([label, href]) => <a key={label} href={href} onClick={() => setOpen(false)}>{label}</a>)}
        <a href="/#/login" onClick={() => setOpen(false)}>Đăng nhập</a>
        <a href="/#/register" onClick={() => setOpen(false)}>Đăng ký</a>
      </nav>}
    </header>
  )
}
