import { useAccount } from '../contexts/AccountContext.jsx'
export default function RequireAccount({ children }) {
  const { user, loading, error, refresh } = useAccount()
  if (loading && !user) return <main className="public-app ly-container"><p role="status">Đang kiểm tra phiên đăng nhập…</p></main>
  if (error && !user) return <main className="public-app ly-container"><p role="alert">{error}</p><button className="ly-button" onClick={() => refresh()}>Thử lại</button></main>
  if (!user) return <main className="public-app ly-container ly-not-found"><h1>Đăng nhập để mở dự án</h1><p>Lưu và mở lại thiết kế trong tài khoản Layoutly của bạn.</p><a className="ly-button ly-button-primary" href="/#/login">Đăng nhập</a><a className="ly-button" href="/#/register">Tạo tài khoản</a></main>
  return children
}
