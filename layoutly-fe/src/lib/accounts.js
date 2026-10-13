export const UNAVAILABLE_ACCOUNTS = Object.freeze({ login: false, registration: false, passwordReset: false })

export async function accountCapabilities(signal) {
  const response = await fetch('/api/auth/capabilities', { signal, credentials: 'same-origin' })
  if (response.status === 404 || response.status === 501) return UNAVAILABLE_ACCOUNTS
  if (!response.ok) throw new Error('Không thể kiểm tra dịch vụ tài khoản. Vui lòng thử lại.')
  const data = await response.json()
  if (data.contractVersion !== 1) throw new Error('Phiên bản dịch vụ tài khoản chưa được hỗ trợ.')
  return {
    login: data.login === true,
    registration: data.registration === true,
    passwordReset: data.passwordReset === true,
  }
}

export function validateAccountForm(mode, values) {
  const errors = {}
  if (mode === 'register' && (!values.name.trim() || values.name.trim().length > 100)) {
    errors.name = 'Nhập tên của bạn, tối đa 100 ký tự.'
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(values.email.trim()) || values.email.trim().length > 254) {
    errors.email = 'Nhập một địa chỉ email hợp lệ.'
  }
  if (mode !== 'forgot' && !values.password) errors.password = 'Nhập mật khẩu của bạn.'
  if (mode === 'register') {
    if (values.password.length < 12 || values.password.length > 128) {
      errors.password = 'Mật khẩu cần từ 12 đến 128 ký tự.'
    }
    if (values.confirmPassword !== values.password) errors.confirmPassword = 'Mật khẩu xác nhận không khớp.'
    if (!values.consent) errors.consent = 'Cần đồng ý với điều khoản để tạo tài khoản.'
  }
  return errors
}

// The capability endpoint and CSRF handshake must both exist before credentials are sent.
export async function submitAccountForm(mode, values, capabilities, signal) {
  const capability = { login: 'login', register: 'registration', forgot: 'passwordReset' }[mode]
  if (!capabilities?.[capability]) throw new Error('Dịch vụ tài khoản chưa khả dụng. Bạn vẫn có thể mở workspace cục bộ.')
  const csrfResponse = await fetch('/api/auth/csrf', { credentials: 'same-origin', signal })
  if (!csrfResponse.ok) throw new Error('Không thể khởi tạo phiên bảo mật. Vui lòng thử lại.')
  const csrf = await csrfResponse.json()
  if (csrf.headerName !== 'X-CSRF-TOKEN' || typeof csrf.token !== 'string' || !csrf.token) {
    throw new Error('Phiên bảo mật không hợp lệ.')
  }
  const body = mode === 'forgot' ? { email: values.email.trim() }
    : mode === 'register' ? { displayName: values.name.trim(), email: values.email.trim(), password: values.password, acceptedTermsVersion: '1' }
      : { email: values.email.trim(), password: values.password, rememberMe: values.remember };
  const response = await fetch(`/api/auth/${mode === 'forgot' ? 'forgot-password' : mode}`, {
    method: 'POST', credentials: 'same-origin', signal,
    headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrf.token }, body: JSON.stringify(body),
  })
  if (!response.ok) {
    if (response.status === 401) throw new Error('Email hoặc mật khẩu không đúng.')
    if (response.status === 409) throw new Error('Không thể đăng ký với email này.')
    if (response.status === 429) throw new Error('Bạn đã thử quá nhiều lần. Vui lòng thử lại sau.')
    throw new Error('Không thể hoàn tất yêu cầu. Vui lòng thử lại.')
  }
  return response.status === 204 ? null : response.json()
}
