import { useState } from 'react'
import { accountApi } from '../lib/accounts.js'
import { useAccount } from '../contexts/AccountContext.jsx'
export default function ResetPasswordPage() {
  const { setUser } = useAccount()
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState('')
  const [done, setDone] = useState(false)
  async function submit(event) {
    event.preventDefault()
    if (password.length < 12 || password.length > 128) { setNotice('Mật khẩu cần từ 12 đến 128 ký tự.'); return }
    if (password !== confirmation) { setNotice('Mật khẩu xác nhận không khớp.'); return }
    const token = new URLSearchParams(window.location.hash.split('?')[1]).get('token')
    if (!token) { setNotice('Liên kết không hợp lệ.'); return }
    setBusy(true); setNotice('')
    try {
      await accountApi.resetPassword(token, password)
      setPassword(''); setConfirmation(''); setUser(null); setDone(true)
      window.history.replaceState(null, '', '/#/reset-password')
    } catch (error) { setNotice(error.message) } finally { setBusy(false) }
  }
  return <main id="public-main" className="ly-auth-main ly-container"><section className="ly-auth-panel"><h1>Đặt lại mật khẩu</h1>{done ? <p role="status">Đã đặt lại mật khẩu. <a href="/#/login">Đăng nhập lại</a></p> : <form onSubmit={submit}><label className="ly-field">Mật khẩu mới<input aria-label="Mật khẩu mới" type="password" autoComplete="new-password" minLength={12} maxLength={128} value={password} onChange={e => setPassword(e.target.value)} required /></label><label className="ly-field">Xác nhận mật khẩu<input aria-label="Xác nhận mật khẩu" type="password" autoComplete="new-password" value={confirmation} onChange={e => setConfirmation(e.target.value)} required /></label><button className="ly-button ly-button-primary" type="submit" disabled={busy}>{busy ? 'Đang xử lý…' : 'Đặt lại mật khẩu'}</button>{notice && <p role="alert">{notice}</p>}</form>}</section></main>
}
