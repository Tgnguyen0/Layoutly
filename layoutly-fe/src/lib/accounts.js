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

// Sessions use HttpOnly cookies; credentials and authentication tokens never enter browser storage.
async function accountRequest(path, { method = 'GET', body, signal } = {}) {
  const controller = new AbortController()
  const abort = () => controller.abort()
  signal?.addEventListener('abort', abort, { once: true })
  if (signal?.aborted) controller.abort()
  const timer = setTimeout(abort, 15000)
  try {
    const headers = {}
    if (method !== 'GET') {
      const csrfResponse = await fetch('/api/auth/csrf', { credentials: 'same-origin', signal: controller.signal })
      if (!csrfResponse.ok) throw new Error('Không thể khởi tạo phiên bảo mật. Vui lòng thử lại.')
      const csrf = await csrfResponse.json()
      if (csrf.headerName !== 'X-CSRF-TOKEN' || !csrf.token) throw new Error('Phiên bảo mật không hợp lệ.')
      headers['X-CSRF-TOKEN'] = csrf.token
      headers['Content-Type'] = 'application/json'
    }
    const response = await fetch(`/api/auth${path}`, {
      method, credentials: 'same-origin', cache: 'no-store', headers,
      signal: controller.signal, body: body === undefined ? undefined : JSON.stringify(body),
    })
    const data = response.status === 204 ? null : await response.json().catch(() => null)
    if (!response.ok) {
      const error = new Error(response.status < 500 && data?.message ? data.message
        : response.status === 401 ? 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.' : 'Không thể kết nối dịch vụ tài khoản. Vui lòng thử lại.')
      error.status = response.status
      throw error
    }
    return data
  } catch (error) {
    if (controller.signal.aborted && !signal?.aborted) throw new Error('Yêu cầu quá thời gian chờ. Vui lòng thử lại.')
    if (error instanceof TypeError) throw new Error('Không thể kết nối dịch vụ tài khoản. Vui lòng thử lại.')
    throw error
  } finally { clearTimeout(timer); signal?.removeEventListener('abort', abort) }
}

export const accountApi = {
  bridgeToken: () => accountRequest('/bridge-token', { method: 'POST' }),
  resetPassword: (token, password) => accountRequest('/reset-password', { method: 'POST', body: { token, password } }),
  me: signal => accountRequest('/me', { signal }),
  update: body => accountRequest('/profile', { method: 'PATCH', body }),
  password: body => accountRequest('/password', { method: 'POST', body }),
  sessions: () => accountRequest('/sessions'),
  revokeOthers: () => accountRequest('/sessions/revoke-others', { method: 'POST' }),
  logout: () => accountRequest('/logout', { method: 'POST' }),
  delete: currentPassword => accountRequest('/account', { method: 'DELETE', body: { currentPassword } }),
}
