// Trạng thái đăng nhập dùng chung cho header, toolbar workspace và dashboard.
// user: undefined = chưa biết (đang tải), null = chưa đăng nhập, object = đã đăng nhập.
// Module này không phụ thuộc React để dễ test; hook nằm ở hooks/useAuth.js.

let state = { user: undefined, status: 'idle' } // status: idle | loading | ready | error
let pending = null
const listeners = new Set()

function emit(next) {
  state = next
  listeners.forEach(listener => listener())
}

export function getAuthState() { return state }

export function subscribeAuth(listener) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

/** Dùng cho test: đưa store về trạng thái ban đầu. */
export function resetAuth() {
  pending?.abort()
  pending = null
  emit({ user: undefined, status: 'idle' })
}

/** Đặt người dùng ngay (vd. sau khi đăng nhập/đăng ký thành công) và huỷ lần tải /me đang chạy. */
export function setCurrentUser(user) {
  pending?.abort()
  pending = null
  emit({ user: user?.id ? user : null, status: 'ready' })
}

/**
 * Hỏi backend ai đang đăng nhập. Chỉ gọi mạng khi chưa biết (idle) hoặc lần trước lỗi (error),
 * nên nhiều component mount cùng lúc vẫn chỉ gửi một request.
 */
export function loadCurrentUser() {
  if (state.status === 'loading' || state.status === 'ready') return Promise.resolve()
  const controller = new AbortController()
  pending = controller
  emit({ ...state, status: 'loading' })
  // Promise.resolve().then(...) để lỗi đồng bộ của fetch cũng thành rejection và được bắt bên dưới.
  return Promise.resolve()
    .then(() => fetch('/api/auth/me', { credentials: 'same-origin', signal: controller.signal }))
    .then(res => {
      if (res.ok) return res.json()
      if (res.status === 401) return null // chưa đăng nhập là trạng thái bình thường, không phải lỗi
      throw new Error(`HTTP ${res.status}`)
    })
    .then(data => {
      if (pending !== controller) return
      pending = null
      emit({ user: data?.user?.id ? data.user : null, status: 'ready' })
    })
    .catch(() => {
      if (pending !== controller) return
      pending = null
      // Backend không phản hồi: hiển thị như chưa đăng nhập nhưng cho phép thử lại ở lần mount sau.
      emit({ user: null, status: 'error' })
    })
}

/** Đăng xuất: lấy CSRF token rồi POST /api/auth/logout. Chỉ xoá user ở FE khi backend xác nhận. */
export async function signOut() {
  const network = () => { throw new Error('Không thể kết nối backend để đăng xuất. Vui lòng thử lại.') }
  const csrfResponse = await fetch('/api/auth/csrf', { credentials: 'same-origin' }).catch(network)
  if (!csrfResponse.ok) throw new Error('Không thể khởi tạo phiên bảo mật. Vui lòng thử lại.')
  const csrf = await csrfResponse.json()
  if (csrf.headerName !== 'X-CSRF-TOKEN' || typeof csrf.token !== 'string' || !csrf.token) {
    throw new Error('Phiên bảo mật không hợp lệ.')
  }
  const response = await fetch('/api/auth/logout', {
    method: 'POST',
    credentials: 'same-origin',
    headers: { [csrf.headerName]: csrf.token },
  }).catch(network)
  if (!response.ok) throw new Error('Không thể đăng xuất. Vui lòng thử lại.')
  setCurrentUser(null)
}

/** Chữ cái đầu của tên (hoặc email) để làm avatar. Dùng Array.from để không cắt đôi ký tự Unicode. */
export function userInitial(user) {
  return Array.from((user?.displayName || user?.email || '?').trim())[0]?.toUpperCase() || '?'
}
