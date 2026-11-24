import { ArrowUpRight } from 'lucide-react'
import Brand from './Brand.jsx'
import useAuth from '../../hooks/useAuth.js'

export default function PublicFooter() {
  const { user } = useAuth()
  return <footer className="ly-footer" id="stay">
    <div className="ly-container ly-footer-top">
      <div><Brand /><p>Từ ý tưởng thiết kế đến mã nguồn của bạn.</p></div>
      <nav aria-label="Liên kết cuối trang">
        <a href="/#workspace">Workspace <ArrowUpRight size={14} /></a>
        {user === null && <>
          <a href="/#/login">Đăng nhập</a>
          <a href="/#/register">Đăng ký</a>
        </>}
        {user && <a href="/#/dashboard">Dự án của tôi</a>}
        <a href="/#/terms">Điều khoản & dữ liệu</a>
      </nav>
    </div>
    <div className="ly-container ly-footer-bottom"><span>© {new Date().getFullYear()} Layoutly</span><span>Figma to code. Your design, your code.</span></div>
  </footer>
}
