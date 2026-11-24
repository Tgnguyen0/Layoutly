import { useEffect } from 'react'
import { ArrowLeft, ArrowRight, ShieldCheck } from 'lucide-react'
import '@fontsource/inter/400.css'
import '@fontsource/inter/500.css'
import '@fontsource/inter/600.css'
import '@fontsource/inter/700.css'
import '@fontsource/inter/800.css'
import PublicHeader from '../components/public/PublicHeader.jsx'
import PublicFooter from '../components/public/PublicFooter.jsx'
import LandingPage from './LandingPage.jsx'
import AuthPage from './AuthPage.jsx'
import DashboardPage from './DashboardPage.jsx'
import '../styles/public.css'
import '../styles/visual.css'

const authRoutes = { '/login': 'login', '/register': 'register', '/forgot-password': 'forgot' }
const titles = { '/': 'Layoutly - Figma to code', '/login': 'Đăng nhập - Layoutly', '/register': 'Đăng ký - Layoutly', '/forgot-password': 'Quên mật khẩu - Layoutly', '/terms': 'Điều khoản & dữ liệu - Layoutly' }

export default function PublicSite({ route }) {
  useEffect(() => {
    document.title = route === '/dashboard' ? 'Dự án của tôi - Layoutly' : titles[route] || 'Không tìm thấy trang - Layoutly'
    if (route === '/' && /^#(work|grow|stay)$/.test(window.location.hash)) document.querySelector(window.location.hash)?.scrollIntoView()
    else window.scrollTo(0, 0)
  }, [route])
  return <div className="public-app">
    <a className="ly-skip-link" href="#public-main">Bỏ qua điều hướng</a>
    {!authRoutes[route] && route !== '/dashboard' && <PublicHeader route={route} />}
    {route === '/' ? <LandingPage /> : route === '/dashboard' ? <DashboardPage /> : authRoutes[route] ? <AuthPage key={route} mode={authRoutes[route]} /> : route === '/terms' ? <main id="public-main" className="ly-container ly-document"><ShieldCheck size={28} /><h1>Điều khoản & dữ liệu</h1><p>Layoutly hiện là công cụ chuyển đổi chạy cục bộ. Dịch vụ tài khoản và lưu dự án theo người dùng chưa được mở.</p><h2>Dữ liệu thiết kế</h2><p>Bạn cần có quyền sử dụng thiết kế, hình ảnh và font trước khi import hoặc xuất mã nguồn. Snapshot và assets được backend hiện tại lưu trên máy chạy dịch vụ.</p><h2>Tài khoản và AI</h2><p>Chưa có tài khoản được tạo trong phiên bản này. Không có prompt hay thiết kế nào được gửi tới AI từ các trang này. Điều khoản tài khoản chính thức sẽ được cung cấp trước khi mở đăng ký.</p><a className="ly-text-link" href="/#workspace">Mở workspace <ArrowRight size={16} /></a></main>
      : <main id="public-main" className="ly-container ly-not-found"><span className="ly-eyebrow">404</span><h1>Không tìm thấy trang.</h1><p>Liên kết này không tồn tại hoặc chưa được mở trong phiên bản hiện tại.</p><a href="/" className="ly-button ly-button-primary"><ArrowLeft size={17} />Về trang chủ</a></main>}
    {!authRoutes[route] && route !== '/dashboard' && <PublicFooter />}
  </div>
}
