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
import AccountPage from './AccountPage.jsx'
import RequireAccount from '../components/RequireAccount.jsx'
import ResetPasswordPage from './ResetPasswordPage.jsx'
import '../styles/public.css'
import '../styles/visual.css'
import '../styles/accounts.css'
import '../styles/landing.css'

const authRoutes = { '/login': 'login', '/register': 'register', '/forgot-password': 'forgot' }
const titles = { '/': 'Layoutly - Figma to code', '/login': 'Đăng nhập - Layoutly', '/register': 'Đăng ký - Layoutly', '/forgot-password': 'Quên mật khẩu - Layoutly', '/terms': 'Điều khoản & dữ liệu - Layoutly', '/account': 'Tài khoản - Layoutly', '/settings': 'Bảo mật - Layoutly' }

export default function PublicSite({ route }) {
  const accountRoute = route === '/account' || route === '/settings'
  useEffect(() => {
    document.title = route === '/dashboard' ? 'Dự án của tôi - Layoutly' : titles[route] || 'Không tìm thấy trang - Layoutly'
    if (route === '/' && /^#(work|grow|showcase|demo|faq|stay)$/.test(window.location.hash)) document.querySelector(window.location.hash)?.scrollIntoView()
    else window.scrollTo(0, 0)
  }, [route])
  if (route === '/reset-password') return <div className="public-app"><ResetPasswordPage /></div>
  return <div className="public-app">
    <a className="ly-skip-link" href="#public-main">Bỏ qua điều hướng</a>
    {!authRoutes[route] && route !== '/dashboard' && !accountRoute && <PublicHeader route={route} />}
    {route === '/' ? <LandingPage /> : route === '/dashboard' ? <RequireAccount><DashboardPage /></RequireAccount> : accountRoute ? <AccountPage initialTab={route === '/settings' ? 'security' : 'profile'} /> : authRoutes[route] ? <AuthPage key={route} mode={authRoutes[route]} /> : route === '/terms' ? <main id="public-main" className="ly-container ly-document"><ShieldCheck size={28} /><h1>Điều khoản & dữ liệu</h1><p>Khi tạo tài khoản, bạn đồng ý cung cấp thông tin chính xác và bảo vệ thông tin đăng nhập của mình. Điều khoản phiên bản 1.</p><h2>Thông tin tài khoản</h2><p>Họ tên, email và các thông tin hồ sơ tự nguyện được lưu trên máy chủ Layoutly đang chạy. Mật khẩu chỉ được lưu dưới dạng băm có salt. Cookie được dùng để duy trì phiên đăng nhập; lựa chọn ghi nhớ có thời hạn tối đa 30 ngày. Email hiện chưa được xác minh bằng thư.</p><h2>Quản lý dữ liệu của bạn</h2><p>Bạn có thể chỉnh sửa hồ sơ, đổi mật khẩu và đăng xuất các thiết bị khác trong trang cài đặt. Xóa dự án hoặc tài khoản sẽ ẩn dữ liệu khỏi truy cập thông thường và giữ lại bản lưu trên máy chủ. Khôi phục mật khẩu qua email khả dụng khi quản trị viên cấu hình dịch vụ gửi thư.</p><h2>Dữ liệu thiết kế</h2><p>Bạn cần có quyền sử dụng thiết kế, hình ảnh và font trước khi import hoặc xuất mã nguồn. Dự án được liên kết với tài khoản của bạn; backend kiểm tra quyền truy cập snapshot, assets và các phiên bản. Khi sử dụng công cụ AI trong workspace, nội dung được xử lý theo nhà cung cấp cấu hình trên máy chủ.</p><a className="ly-text-link" href="/#/account">Quản lý tài khoản <ArrowRight size={16} /></a></main>
      : <main id="public-main" className="ly-container ly-not-found"><span className="ly-eyebrow">404</span><h1>Không tìm thấy trang.</h1><p>Liên kết này không tồn tại hoặc chưa được mở trong phiên bản hiện tại.</p><a href="/" className="ly-button ly-button-primary"><ArrowLeft size={17} />Về trang chủ</a></main>}
    {!authRoutes[route] && route !== '/dashboard' && !accountRoute && <PublicFooter />}
  </div>
}
