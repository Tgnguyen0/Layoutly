import { useEffect, useRef, useState } from 'react'
import { ArrowLeft, ArrowRight, Check, Eye, EyeOff, Info, Layers3, LoaderCircle, RefreshCw, ShieldCheck } from 'lucide-react'
import Brand from '../components/public/Brand.jsx'
import { useAccount } from '../contexts/AccountContext.jsx'
import { accountCapabilities, submitAccountForm, UNAVAILABLE_ACCOUNTS, validateAccountForm } from '../lib/accounts.js'

const texts = {
  login: { title: 'Chào mừng bạn trở lại.', subtitle: 'Tiếp tục hành trình từ thiết kế đến mã nguồn.', action: 'Đăng nhập' },
  register: { title: 'Bắt đầu với Layoutly.', subtitle: 'Một nơi dành cho thiết kế và những bước tiếp theo.', action: 'Tạo tài khoản' },
  forgot: { title: 'Quên mật khẩu?', subtitle: 'Nhập email gắn với tài khoản của bạn.', action: 'Gửi liên kết đặt lại' },
}
const emptyValues = { name: '', email: '', password: '', confirmPassword: '', remember: false, consent: false }

export default function AuthPage({ mode }) {
  const { setUser } = useAccount()
  const [values, setValues] = useState(emptyValues)
  const [errors, setErrors] = useState({})
  const [showPassword, setShowPassword] = useState(false)
  const [showConfirmation, setShowConfirmation] = useState(false)
  const [capabilities, setCapabilities] = useState(UNAVAILABLE_ACCOUNTS)
  const [checking, setChecking] = useState(true)
  const [capabilityError, setCapabilityError] = useState('')
  const [attempt, setAttempt] = useState(0)
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState(null)
  const submission = useRef(null)
  const form = useRef(null)
  const content = texts[mode]
  const enabled = capabilities[{ login: 'login', register: 'registration', forgot: 'passwordReset' }[mode]]

  useEffect(() => {
    const controller = new AbortController()
    let active = true
    const timer = setTimeout(() => controller.abort(), 8000)
    setChecking(true)
    setCapabilityError('')
    setCapabilities(UNAVAILABLE_ACCOUNTS)
    accountCapabilities(controller.signal)
      .then(data => { if (active) setCapabilities(data) })
      .catch(() => { if (active) setCapabilityError('Không thể kết nối dịch vụ tài khoản.') })
      .finally(() => { clearTimeout(timer); if (active) setChecking(false) })
    return () => { active = false; controller.abort(); clearTimeout(timer) }
  }, [attempt])
  useEffect(() => () => { submission.current?.abort(); submission.current = null }, [])

  function update(name, value) {
    setValues(current => ({ ...current, [name]: value }))
    setErrors(current => ({ ...current, [name]: undefined }))
    setNotice(null)
  }
  function validateField(name) {
    const validation = validateAccountForm(mode, values)
    setErrors(current => ({ ...current, [name]: validation[name] }))
  }
  async function submit(event) {
    event.preventDefault()
    if (busy || checking) return
    const validation = validateAccountForm(mode, values)
    setErrors(validation)
    if (Object.keys(validation).length) {
      form.current?.querySelector(`[name="${Object.keys(validation)[0]}"]`)?.focus()
      return
    }
    if (!enabled) { setNotice({ error: true, text: 'Dịch vụ tài khoản chưa khả dụng. Không có thông tin đăng nhập nào được gửi.' }); return }
    const controller = new AbortController()
    submission.current = controller
    const timer = setTimeout(() => controller.abort(), 15000)
    setBusy(true)
    setNotice(null)
    try {
      const result = await submitAccountForm(mode, values, capabilities, controller.signal)
      if (mode !== 'forgot' && !result?.user?.id) throw new Error('Phản hồi tài khoản không hợp lệ.')
      if (controller.signal.aborted) return
      setValues(current => ({ ...current, password: '', confirmPassword: '' }))
      if (mode === 'forgot') setNotice({ error: false, text: 'Nếu email đã được đăng ký, bạn sẽ nhận được liên kết đặt lại mật khẩu.' })
      else { setUser(result.user); window.location.hash = '#/dashboard' }
    } catch (error) {
      if (submission.current === controller) setNotice({ error: true, text: error.name === 'AbortError' ? 'Yêu cầu quá thời gian chờ. Vui lòng thử lại.' : error.message })
    } finally {
      clearTimeout(timer)
      if (submission.current === controller) setBusy(false)
    }
  }

  function field(name, label, { type = 'text', autoComplete, placeholder, toggle, visible } = {}) {
    const error = errors[name]
    return <div className="ly-field" key={name}>
      <label htmlFor={`account-${name}`}>{label}</label>
      <div className={`ly-input-wrap ${error ? 'ly-input-invalid' : ''}`}>
        <input id={`account-${name}`} name={name} type={type} autoComplete={autoComplete} value={values[name]} placeholder={placeholder} disabled={busy} aria-invalid={Boolean(error)} aria-describedby={error ? `${name}-error` : undefined} onChange={e => update(name, e.target.value)} onBlur={() => validateField(name)} maxLength={name === 'email' ? 254 : name === 'name' ? 100 : 128} />
        {toggle && <button type="button" className="ly-icon-button" aria-label={visible ? `Ẩn ${label.toLowerCase()}` : `Hiện ${label.toLowerCase()}`} aria-pressed={visible} onClick={toggle} title={visible ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}>{visible ? <EyeOff size={18} /> : <Eye size={18} />}</button>}
      </div>
      {error && <p className="ly-field-error" id={`${name}-error`}>{error}</p>}
    </div>
  }

  return <main id="public-main" className="ly-auth-main">
    <div className="ly-container ly-auth-layout">
      <section className="ly-auth-intro" aria-labelledby="auth-intro-title">
        <Brand />
        <div className="ly-auth-story"><span className="ly-eyebrow">THIẾT KẾ CỦA BẠN. MÃ NGUỒN CỦA BẠN.</span>
          <h1 id="auth-intro-title">Ý tưởng tốt xứng đáng được thành hình.</h1>
          <p>Đưa thiết kế Figma vào một không gian làm việc, xem giao diện và xuất mã nguồn để tiếp tục xây dựng.</p>
          <div className="ly-auth-workflow" aria-label="Quy trình thiết kế"><div><Layers3 size={25} /><span>Thiết kế</span><small>Figma</small></div><ArrowRight size={20} /><div><span className="ly-code-symbol">&lt;/&gt;</span><span>Phát triển</span><small>HTML · React</small></div><ArrowRight size={20} /><div><Check size={25} /><span>Sẵn sàng</span><small>Mã nguồn của bạn</small></div></div>
          <p className="ly-auth-caption">Từ những chi tiết nhỏ, đến một sản phẩm hoàn chỉnh.</p>
        </div>
        <a href="/" className="ly-text-link"><ArrowLeft size={16} />Quay lại trang chủ</a>
      </section>

      <section className="ly-auth-panel" aria-labelledby="auth-form-title">
        {mode !== 'forgot' ? <nav className="ly-auth-tabs" aria-label="Tài khoản"><a href="/#/login" aria-current={mode === 'login' ? 'page' : undefined}>Đăng nhập</a><a href="/#/register" aria-current={mode === 'register' ? 'page' : undefined}>Đăng ký</a></nav>
          : <a className="ly-text-link ly-back-link" href="/#/login">Quay lại đăng nhập</a>}
        <div className="ly-auth-heading"><h2 id="auth-form-title">{content.title}</h2><p>{content.subtitle}</p></div>
        <form ref={form} onSubmit={submit} noValidate aria-busy={busy}>
          {mode === 'register' && field('name', 'Họ và tên', { autoComplete: 'name', placeholder: 'Tên của bạn' })}
          {field('email', 'Email', { type: 'email', autoComplete: 'email', placeholder: 'example@domain.com' })}
          {mode !== 'forgot' && field('password', 'Mật khẩu', { type: showPassword ? 'text' : 'password', autoComplete: mode === 'login' ? 'current-password' : 'new-password', placeholder: mode === 'register' ? 'Tối thiểu 12 ký tự' : 'Nhập mật khẩu', visible: showPassword, toggle: () => setShowPassword(x => !x) })}
          {mode === 'register' && field('confirmPassword', 'Xác nhận mật khẩu', { type: showConfirmation ? 'text' : 'password', autoComplete: 'new-password', placeholder: 'Nhập lại mật khẩu', visible: showConfirmation, toggle: () => setShowConfirmation(x => !x) })}
          {mode === 'login' && <div className="ly-form-options"><label className="ly-checkbox"><input name="remember" type="checkbox" checked={values.remember} onChange={e => update('remember', e.target.checked)} disabled={busy} />Ghi nhớ đăng nhập</label><a href="/#/forgot-password">Quên mật khẩu?</a></div>}
          {mode === 'register' && <div className="ly-consent"><label className="ly-checkbox"><input name="consent" type="checkbox" checked={values.consent} onChange={e => update('consent', e.target.checked)} disabled={busy} aria-invalid={Boolean(errors.consent)} aria-describedby={errors.consent ? 'consent-error' : undefined} /><span>Tôi đồng ý với <a href="/#/terms">điều khoản & dữ liệu</a>.</span></label>{errors.consent && <p className="ly-field-error" id="consent-error">{errors.consent}</p>}</div>}
          {notice && <div className={`ly-form-notice ${notice.error ? 'ly-notice-error' : ''}`} role={notice.error ? 'alert' : 'status'}>{notice.text}</div>}
          <button className="ly-button ly-button-primary ly-submit" type="submit" disabled={busy || checking || !enabled}>{busy || checking ? <LoaderCircle size={17} className="ly-spinner" /> : null}{busy ? 'Đang xử lý…' : content.action}</button>
        </form>
        {!checking && !enabled && <div className="ly-availability" role="status"><Info size={18} /><div><strong>{capabilityError || (mode === 'forgot' ? 'Khôi phục mật khẩu qua email chưa khả dụng' : 'Dịch vụ tài khoản chưa khả dụng')}</strong><p>{mode === 'forgot' ? 'Chức năng gửi email khôi phục chưa được thiết lập. Nếu còn đăng nhập ở thiết bị khác, bạn có thể đổi mật khẩu trong mục Bảo mật.' : 'Kiểm tra backend rồi thử kết nối lại.'}</p></div><button type="button" className="ly-icon-button" title="Thử kết nối lại" aria-label="Thử kết nối lại" onClick={() => setAttempt(x => x + 1)}><RefreshCw size={16} /></button></div>}
        <p className="ly-auth-switch">{mode === 'login' ? <>Chưa có tài khoản? <a href="/#/register">Đăng ký ngay</a></> : <>Đã có tài khoản? <a href="/#/login">Đăng nhập</a></>}</p>
        <div className="ly-auth-security"><ShieldCheck size={15} /><span>Không lưu mật khẩu trên trình duyệt.</span></div>
      </section>
    </div>
  </main>
}
