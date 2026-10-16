import { useEffect, useRef, useState } from 'react'
import { ArrowLeft, ArrowRight, Check, ChevronRight, LoaderCircle, LogOut, Monitor, RefreshCw, ShieldCheck, UserRound } from 'lucide-react'
import Brand from '../components/public/Brand.jsx'
import { initials, useAccount } from '../contexts/AccountContext.jsx'
import { accountApi } from '../lib/accounts.js'

const date = value => new Date(value).toLocaleDateString('vi-VN', { day: '2-digit', month: 'long', year: 'numeric' })

function Field({ label, hint, multiline, ...props }) {
  const id = `profile-${props.name}`
  return <div className="ac-field"><label htmlFor={id}>{label}</label>{multiline
    ? <textarea id={id} rows={4} aria-describedby={hint ? `${id}-hint` : undefined} {...props} />
    : <input id={id} aria-describedby={hint ? `${id}-hint` : undefined} {...props} />}
    {hint && <p id={`${id}-hint`}>{hint}</p>}</div>
}

function deviceName(agent) {
  const browser = /Edg\//.test(agent) ? 'Microsoft Edge' : /Firefox\//.test(agent) ? 'Firefox' : /Chrome\//.test(agent) ? 'Chrome' : /Safari\//.test(agent) ? 'Safari' : 'Trình duyệt'
  const platform = /Android/.test(agent) ? 'Android' : /iPhone|iPad/.test(agent) ? 'iOS' : /Windows/.test(agent) ? 'Windows' : /Macintosh/.test(agent) ? 'macOS' : /Linux/.test(agent) ? 'Linux' : ''
  return `${browser}${platform ? ` trên ${platform}` : ''}`
}

export default function AccountPage({ initialTab = 'profile' }) {
  const { user, loading, error, refresh, setUser, logout } = useAccount()
  const [tab, setTab] = useState(initialTab)
  const [profile, setProfile] = useState({ displayName: '', email: '', company: '', bio: '', currentPassword: '' })
  const [password, setPassword] = useState({ currentPassword: '', newPassword: '', confirm: '' })
  const [sessions, setSessions] = useState([])
  const [sessionsError, setSessionsError] = useState('')
  const [sessionsLoading, setSessionsLoading] = useState(false)
  const [busy, setBusy] = useState('')
  const [notice, setNotice] = useState(null)
  const [deletePassword, setDeletePassword] = useState('')
  const [deleteError, setDeleteError] = useState('')
  const dialog = useRef(null)

  useEffect(() => {
    if (user) setProfile({ displayName: user.displayName, email: user.email, company: user.company || '', bio: user.bio || '', currentPassword: '' })
  }, [user?.id, user?.updatedAt])
  useEffect(() => { setTab(initialTab); setNotice(null) }, [initialTab])
  useEffect(() => { if (user && tab === 'security') loadSessions() }, [user?.id, tab])

  async function loadSessions() {
    setSessionsLoading(true)
    setSessionsError('')
    try { const result = await accountApi.sessions(); setSessions(result.sessions) }
    catch (caught) { setSessionsError(caught.message); if (caught.status === 401) setUser(null) }
    finally { setSessionsLoading(false) }
  }
  async function perform(action, task, success) {
    if (busy) return
    setBusy(action); setNotice(null)
    try { await task(); if (success) setNotice({ text: success }) }
    catch (caught) { setNotice({ error: true, text: caught.message }); if (caught.status === 401) setUser(null) }
    finally { setBusy('') }
  }
  function updateProfile(event) { setProfile(current => ({ ...current, [event.target.name]: event.target.value })); setNotice(null) }
  function updatePassword(event) { setPassword(current => ({ ...current, [event.target.name]: event.target.value })); setNotice(null) }
  async function saveProfile(event) {
    event.preventDefault()
    if (!profile.displayName.trim()) { setNotice({ error: true, text: 'Nhập họ và tên của bạn.' }); return }
    await perform('profile', async () => {
      const result = await accountApi.update(profile)
      setUser(result.user)
      setProfile(current => ({ ...current, currentPassword: '' }))
    }, 'Đã lưu thông tin tài khoản.')
  }
  async function changePassword(event) {
    event.preventDefault()
    if (password.newPassword !== password.confirm) { setNotice({ error: true, text: 'Mật khẩu xác nhận không khớp.' }); return }
    await perform('password', async () => {
      const result = await accountApi.password({ currentPassword: password.currentPassword, newPassword: password.newPassword })
      setUser(result.user); setPassword({ currentPassword: '', newPassword: '', confirm: '' }); await loadSessions()
    }, 'Đã đổi mật khẩu. Các thiết bị khác đã được đăng xuất.')
  }
  async function removeAccount(event) {
    event.preventDefault()
    if (busy) return
    setBusy('delete'); setDeleteError('')
    try { await accountApi.delete(deletePassword); dialog.current.close(); setUser(null); window.location.hash = '#/login' }
    catch (caught) { setDeleteError(caught.message) }
    finally { setBusy('') }
  }
  const emailChanged = user && profile.email.trim().toLowerCase() !== user.email
  const changed = user && ['displayName', 'email', 'company', 'bio'].some(key => profile[key] !== (user[key] || ''))

  return <main id="public-main" className="account-page">
    <header className="ac-header"><Brand /><a className="ly-text-link" href="/#/dashboard"><ArrowLeft size={16} />Về dự án</a></header>
    {loading && !user ? <div className="ac-gate" role="status"><LoaderCircle className="ly-spinner" size={26} /><p>Đang tải tài khoản…</p></div>
      : !user ? <section className="ac-gate"><ShieldCheck size={36} /><h1>{error ? 'Chưa thể tải tài khoản' : 'Đăng nhập để quản lý tài khoản'}</h1><p>{error || 'Xem và cập nhật thông tin cá nhân, mật khẩu và các phiên đăng nhập của bạn.'}</p>{error && <button className="ly-button ly-button-quiet" onClick={() => refresh()}>Thử lại</button>}<a className="ly-button ly-button-primary" href="/#/login">Đăng nhập <ArrowRight size={16} /></a><a className="ly-text-link" href="/#/register">Tạo tài khoản mới</a></section>
        : <div className="ac-layout">
          <aside className="ac-sidebar"><div className="ac-identity"><span className="ac-avatar">{initials(user.displayName)}</span><strong>{user.displayName}</strong><span>{user.email}</span></div>
            <p className="ac-nav-label">CÀI ĐẶT TÀI KHOẢN</p>
            <nav aria-label="Cài đặt tài khoản">{[['profile', UserRound, 'Thông tin cá nhân'], ['security', ShieldCheck, 'Bảo mật & đăng nhập']].map(([key, Icon, label]) => <button key={key} type="button" aria-pressed={tab === key} onClick={() => { setTab(key); setNotice(null) }}><Icon size={18} />{label}<ChevronRight size={14} /></button>)}</nav>
            <button className="ac-logout" type="button" disabled={Boolean(busy)} onClick={() => perform('logout', logout)}><LogOut size={17} />{busy === 'logout' ? 'Đang đăng xuất…' : 'Đăng xuất'}</button>
            <div className="ac-sidebar-note"><ShieldCheck size={18} /><p>Thông tin của bạn chỉ hiển thị trong tài khoản này.</p></div>
          </aside>
          <section className="ac-content" aria-labelledby="ac-title"><div className="ac-breadcrumb">Tài khoản <ChevronRight size={13} /><span>{tab === 'profile' ? 'Thông tin cá nhân' : 'Bảo mật'}</span></div>
            <div className="ac-title"><h1 id="ac-title">{tab === 'profile' ? 'Thông tin cá nhân' : 'Bảo mật & đăng nhập'}</h1><p>{tab === 'profile' ? 'Một vài thông tin để không gian làm việc mang dấu ấn của bạn.' : 'Quản lý mật khẩu và những thiết bị đang đăng nhập.'}</p></div>
            {notice && <div className={`ly-form-notice ${notice.error ? 'ly-notice-error' : ''}`} role={notice.error ? 'alert' : 'status'}>{!notice.error && <Check size={16} />}{notice.text}</div>}
            {tab === 'profile' ? <form className="ac-card" onSubmit={saveProfile} aria-busy={busy === 'profile'}><div className="ac-card-heading"><h2>Hồ sơ của bạn</h2><p>Cập nhật tên và các thông tin liên hệ.</p></div>
              <fieldset disabled={Boolean(busy)}><div className="ac-profile-summary"><span className="ac-avatar ac-avatar-large">{initials(profile.displayName)}</span><div><strong>{profile.displayName || 'Tên của bạn'}</strong><p>Thành viên từ {date(user.createdAt)}</p></div></div>
                <div className="ac-two-columns"><Field label="Họ và tên" name="displayName" value={profile.displayName} onChange={updateProfile} required maxLength={100} autoComplete="name" /><Field label="Email" name="email" type="email" value={profile.email} onChange={updateProfile} required maxLength={254} autoComplete="email" /></div>
                {emailChanged && <Field label="Mật khẩu hiện tại để đổi email" name="currentPassword" type="password" value={profile.currentPassword} onChange={updateProfile} required maxLength={128} autoComplete="current-password" hint="Đổi email sẽ đăng xuất các thiết bị khác. Email mới chưa được xác minh qua thư." />}
                <Field label="Công ty / Tổ chức" name="company" value={profile.company} onChange={updateProfile} maxLength={120} autoComplete="organization" placeholder="Bạn đang làm việc ở đâu? (không bắt buộc)" />
                <Field label="Giới thiệu" name="bio" multiline value={profile.bio} onChange={updateProfile} maxLength={500} placeholder="Một vài dòng về bạn và công việc của bạn…" hint={`${profile.bio.length}/500 ký tự · Không bắt buộc`} />
              </fieldset><div className="ac-card-footer"><span>Chỉ bạn có thể xem thông tin này.</span><button className="ly-button ly-button-primary" disabled={Boolean(busy) || !changed} type="submit">{busy === 'profile' && <LoaderCircle className="ly-spinner" size={16} />}{busy === 'profile' ? 'Đang lưu…' : 'Lưu thay đổi'}</button></div>
            </form> : <>
              <form className="ac-card" onSubmit={changePassword} aria-busy={busy === 'password'}><div className="ac-card-heading"><h2>Đổi mật khẩu</h2><p>Sử dụng mật khẩu riêng cho tài khoản Layoutly của bạn.</p></div><fieldset disabled={Boolean(busy)}>
                <Field label="Mật khẩu hiện tại" name="currentPassword" type="password" value={password.currentPassword} onChange={updatePassword} required maxLength={128} autoComplete="current-password" />
                <div className="ac-two-columns"><Field label="Mật khẩu mới" name="newPassword" type="password" value={password.newPassword} onChange={updatePassword} required minLength={12} maxLength={128} autoComplete="new-password" hint="Từ 12 đến 128 ký tự. Có thể sử dụng một cụm từ dễ nhớ." /><Field label="Xác nhận mật khẩu mới" name="confirm" type="password" value={password.confirm} onChange={updatePassword} required maxLength={128} autoComplete="new-password" /></div>
              </fieldset><div className="ac-card-footer"><span>Các thiết bị khác sẽ được đăng xuất.</span><button className="ly-button ly-button-primary" disabled={Boolean(busy)} type="submit">{busy === 'password' ? 'Đang cập nhật…' : 'Cập nhật mật khẩu'}</button></div></form>
              <section className="ac-card" aria-labelledby="sessions-title"><div className="ac-card-heading ac-sessions-heading"><div><h2 id="sessions-title">Phiên đăng nhập</h2><p>Những trình duyệt đang có quyền truy cập tài khoản.</p></div><button className="ly-icon-button" aria-label="Tải lại phiên đăng nhập" type="button" disabled={sessionsLoading || Boolean(busy)} onClick={loadSessions}><RefreshCw size={17} /></button></div>
                {sessionsLoading ? <p className="ac-session-message" role="status">Đang tải phiên đăng nhập…</p> : sessionsError ? <p className="ac-session-message" role="alert">{sessionsError}</p> : <ul className="ac-sessions">{sessions.map(session => <li key={session.id}><span className="ac-device"><Monitor size={21} /></span><div><strong>{deviceName(session.device)} {session.current && <span className="ac-current">Thiết bị này</span>}</strong><p>Đăng nhập {date(session.createdAt)} · Hết hạn {date(session.expiresAt)}</p></div></li>)}</ul>}
                <div className="ac-card-footer"><span>Không nhận ra một thiết bị?</span><button className="ly-button ly-button-quiet" type="button" disabled={Boolean(busy) || sessionsLoading || !sessions.some(s => !s.current)} onClick={() => perform('sessions', async () => { await accountApi.revokeOthers(); await loadSessions() }, 'Đã đăng xuất các thiết bị khác.')}>Đăng xuất thiết bị khác</button></div>
              </section>
              <section className="ac-card ac-danger"><div className="ac-card-heading"><h2>Xóa tài khoản</h2><p>Xóa vĩnh viễn hồ sơ và đăng xuất khỏi tất cả thiết bị. Thao tác này không thể hoàn tác.</p></div><div className="ac-card-footer"><span>Các snapshot cục bộ không bị xóa.</span><button className="ly-button ac-danger-button" type="button" disabled={Boolean(busy)} onClick={() => { setDeleteError(''); dialog.current.showModal() }}>Xóa tài khoản</button></div></section>
            </>}
            <p className="ac-bottom-note"><ShieldCheck size={14} />Mật khẩu được bảo vệ và không lưu trong trình duyệt.</p>
          </section>
        </div>}
    <dialog className="ac-delete-dialog" ref={dialog} onClose={() => { setDeletePassword(''); setDeleteError('') }} aria-labelledby="delete-title"><form onSubmit={removeAccount}><h2 id="delete-title">Xóa tài khoản của bạn?</h2><p>Hồ sơ sẽ bị xóa vĩnh viễn và mọi phiên đăng nhập sẽ kết thúc. Nhập mật khẩu để xác nhận.</p><Field label="Mật khẩu xác nhận xóa" name="deletePassword" type="password" autoComplete="current-password" required maxLength={128} value={deletePassword} onChange={e => setDeletePassword(e.target.value)} disabled={busy === 'delete'} />{deleteError && <p role="alert" className="ly-field-error">{deleteError}</p>}<div className="ac-dialog-actions"><button className="ly-button ly-button-quiet" type="button" onClick={() => dialog.current.close()} disabled={busy === 'delete'}>Hủy</button><button className="ly-button ac-danger-button" type="submit" disabled={busy === 'delete'}>{busy === 'delete' ? 'Đang xóa…' : 'Xóa vĩnh viễn'}</button></div></form></dialog>
  </main>
}
