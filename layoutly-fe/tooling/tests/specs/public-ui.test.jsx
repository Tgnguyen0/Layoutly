import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import AuthPage from '../../../src/pages/AuthPage.jsx'
import LandingPage from '../../../src/pages/LandingPage.jsx'
import PublicHeader from '../../../src/components/public/PublicHeader.jsx'

describe('public account UI', () => {
  it('shows unavailable auth honestly and hides OAuth', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ status: 404 }))
    render(<AuthPage mode="login" />)
    expect(await screen.findByText('Dịch vụ tài khoản chưa khả dụng')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Đăng nhập', exact: true })).toBeDisabled()
    expect(screen.queryByText('Google')).not.toBeInTheDocument()
    expect(screen.queryByText('GitHub')).not.toBeInTheDocument()
    expect(fetch).toHaveBeenCalledTimes(1)
  })
  it('supports show/hide and accessible email validation', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ status: 404 }))
    const user = userEvent.setup()
    render(<AuthPage mode="login" />)
    await screen.findByText('Dịch vụ tài khoản chưa khả dụng')
    expect(screen.getByLabelText('Mật khẩu', { exact: true })).toHaveAttribute('type', 'password')
    await user.click(screen.getByRole('button', { name: 'Hiện mật khẩu', exact: true }))
    expect(screen.getByLabelText('Mật khẩu', { exact: true })).toHaveAttribute('type', 'text')
    await user.type(screen.getByLabelText('Email'), 'not-an-email')
    await user.tab()
    expect(screen.getByText('Nhập một địa chỉ email hợp lệ.')).toBeInTheDocument()
    expect(screen.getByLabelText('Email')).toHaveAttribute('aria-invalid', 'true')
    expect(fetch).toHaveBeenCalledTimes(1)
  })
  it('shows loading, failure and retry without inventing a session', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValueOnce(new Error('offline')).mockResolvedValue({ status: 404 }))
    render(<AuthPage mode="register" />)
    expect(screen.getByRole('button', { name: 'Tạo tài khoản', exact: true })).toBeDisabled()
    await screen.findByText('Không thể kết nối dịch vụ tài khoản.')
    fireEvent.click(screen.getByRole('button', { name: 'Thử kết nối lại' }))
    await screen.findByText('Dịch vụ tài khoản chưa khả dụng')
    expect(fetch).toHaveBeenCalledTimes(2)
  })
  it('validates all registration fields before an enabled submission', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => ({ contractVersion: 1, registration: true }) }))
    const user = userEvent.setup()
    render(<AuthPage mode="register" />)
    const button = screen.getByRole('button', { name: 'Tạo tài khoản', exact: true })
    await waitFor(() => expect(button).toBeEnabled())
    await user.click(button)
    expect(screen.getByText('Mật khẩu cần từ 12 đến 128 ký tự.')).toBeInTheDocument()
    expect(screen.getByLabelText('Họ và tên')).toHaveFocus()
    expect(fetch).toHaveBeenCalledTimes(1)
  })
  it('cancels a pending capability request on unmount', () => {
    let signal
    vi.stubGlobal('fetch', vi.fn((_, options) => { signal = options.signal; return new Promise(() => {}) }))
    const { unmount } = render(<AuthPage mode="login" />)
    unmount()
    expect(signal.aborted).toBe(true)
  })
  it('renders forgot-password as unavailable when email service is absent', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ status: 404 }))
    render(<AuthPage mode="forgot" />)
    await screen.findByText('Dịch vụ tài khoản chưa khả dụng')
    expect(screen.queryByLabelText('Mật khẩu', { exact: true })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Gửi liên kết đặt lại' })).toBeDisabled()
  })
})
describe('landing and header', () => {
  it('reports actual Bridge readiness and marks unreleased features', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => ({ status: 'READY' }) }))
    render(<LandingPage />)
    await screen.findByText('Figma Bridge sẵn sàng')
    expect(screen.getAllByText('Chưa khả dụng')).toHaveLength(2)
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Tạo code từ')
    expect(screen.getByRole('link', { name: 'Bắt đầu miễn phí' })).toHaveAttribute('href', '/#workspace')
  })
  it('supports keyboard dismissal of the mobile navigation', async () => {
    const user = userEvent.setup()
    render(<PublicHeader route="/" />)
    const trigger = screen.getByRole('button', { name: 'Mở menu' })
    await user.click(trigger)
    expect(screen.getByRole('navigation', { name: 'Điều hướng di động' })).toBeInTheDocument()
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('navigation', { name: 'Điều hướng di động' })).not.toBeInTheDocument()
    expect(trigger).toHaveFocus()
  })
})
