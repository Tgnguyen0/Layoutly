import React, { useEffect, useMemo, useRef, useState } from 'react'
import {
  ArrowDownTrayIcon,
  ArrowRightIcon,
  Bars3Icon,
  CheckCircleIcon,
  ChevronDownIcon,
  CloudArrowDownIcon,
  CodeBracketIcon,
  ComputerDesktopIcon,
  CursorArrowRaysIcon,
  DocumentArrowUpIcon,
  HomeIcon,
  KeyIcon,
  LinkIcon,
  ShieldCheckIcon,
  Squares2X2Icon,
} from '@heroicons/react/24/outline'
import { downloadZipExport, figmaApi } from './lib/api.js'

function extractFileKey(input) {
  const value = input.trim()
  if (!value) return ''

  try {
    const url = new URL(value)
    const match = url.pathname.match(/\/(?:file|design)\/([^/]+)/)
    return match?.[1] || value
  } catch {
    return value
  }
}

function BrandMark() {
  return (
    <a href="/" className="flex items-center gap-2.5" aria-label="Layoutly trang chủ">
      <span
        className="relative grid h-12 w-12 grid-cols-2 gap-1 rounded-lg border border-zinc-200 bg-white p-1.5 shadow-[0_8px_24px_rgba(15,23,42,0.10)]"
        aria-hidden="true"
      >
        <span className="rounded-[4px] bg-[#0b5cff]" />
        <span className="rounded-[4px] bg-[#22c55e]" />
        <span className="rounded-[4px] bg-[#f97316]" />
        <span className="rounded-[4px] bg-zinc-900" />
        <span className="absolute left-1/2 top-1/2 flex h-6 w-6 -translate-x-1/2 -translate-y-1/2 items-center justify-center rounded-full bg-white shadow-sm ring-1 ring-zinc-200">
          <ArrowRightIcon className="h-4 w-4 stroke-[2.5] text-[#0b5cff]" />
        </span>
        <span className="absolute -bottom-1 -right-1 rounded bg-zinc-950 px-1 py-0.5 text-[9px] font-black leading-none text-white ring-2 ring-white">
          TT
        </span>
      </span>
      <span className="flex flex-col leading-none">
        <strong className="text-[28px] font-extrabold tracking-[-0.02em] text-zinc-900">
          Layoutly
        </strong>
        <span className="mt-1 text-[10px] font-bold uppercase tracking-[0.18em] text-zinc-500">
          Thanh & Tan
        </span>
      </span>
    </a>
  )
}

function NavButton({ children, primary }) {
  return (
    <button
      type="button"
      className={
        primary
          ? 'rounded-md bg-[#0b5cff] px-4 py-3 text-base font-bold text-white shadow-sm transition hover:bg-[#064be0]'
          : 'rounded-md border border-zinc-200 bg-white px-4 py-3 text-base font-medium text-zinc-900 transition hover:bg-zinc-50'
      }
    >
      {children}
    </button>
  )
}

function InfoBlock({ icon: Icon, title, children }) {
  return (
    <article className="grid grid-cols-[32px_1fr] gap-5">
      <span className="mt-1 flex h-8 w-8 items-center justify-center" aria-hidden="true">
        <Icon
          className="block stroke-[1.35] text-zinc-950"
          style={{ width: 32, height: 32, minWidth: 32, maxWidth: 32 }}
        />
      </span>
      <section className="min-w-0">
        <h3 className="text-2xl font-extrabold leading-tight text-zinc-950">{title}</h3>
        <p className="mt-4 text-[19px] font-medium leading-8 text-zinc-900">{children}</p>
      </section>
    </article>
  )
}

export default function App() {
  const previewPanelRef = useRef(null)
  const [figmaUrl, setFigmaUrl] = useState('')
  const [token, setToken] = useState(() => localStorage.getItem('layoutly_token') || '')
  const [isTokenOpen, setIsTokenOpen] = useState(false)
  const [loading, setLoading] = useState(false)
  const [htmlLoading, setHtmlLoading] = useState(false)
  const [htmlResult, setHtmlResult] = useState('')
  const [previewLoading, setPreviewLoading] = useState(false)
  const [previewHtml, setPreviewHtml] = useState('')
  const [previewSize, setPreviewSize] = useState({ width: 0, height: 0 })
  const [previewHeight, setPreviewHeight] = useState(720)
  const [status, setStatus] = useState('')
  const [error, setError] = useState('')

  const fileKey = useMemo(() => extractFileKey(figmaUrl), [figmaUrl])

  function fitPreviewHeight(width, height) {
    if (!width || !height) {
      setPreviewHeight(720)
      return
    }

    const panelWidth = previewPanelRef.current?.clientWidth || Math.min(window.innerWidth - 32, 1420)
    const scale = Math.min(1, panelWidth / width)
    setPreviewHeight(Math.ceil(height * scale) + 2)
  }

  function updatePreviewHeight(html) {
    const doc = new DOMParser().parseFromString(html, 'text/html')
    const width = Number(doc.querySelector('meta[name="figma-width"]')?.content)
    const height = Number(doc.querySelector('meta[name="figma-height"]')?.content)

    setPreviewSize({ width, height })
    fitPreviewHeight(width, height)
  }

  useEffect(() => {
    if (!previewHtml || !previewSize.width || !previewSize.height) return undefined

    const handleResize = () => fitPreviewHeight(previewSize.width, previewSize.height)
    handleResize()
    window.addEventListener('resize', handleResize)
    return () => window.removeEventListener('resize', handleResize)
  }, [previewHtml, previewSize.width, previewSize.height])

  async function handleSubmit(event) {
    event.preventDefault()
    setError('')
    setStatus('')

    if (!fileKey) {
      setError('Vui lòng dán link Figma hoặc File Key trước khi chuyển đổi.')
      return
    }

    setLoading(true)
    try {
      localStorage.setItem('layoutly_token', token)
      await downloadZipExport(token, fileKey)
      setStatus('Đã tạo và tải file ZIP thành công.')
    } catch (err) {
      setError(err.message || String(err))
    } finally {
      setLoading(false)
    }
  }

  async function handleHtmlConvert() {
    setError('')
    setStatus('')
    setHtmlResult('')

    if (!fileKey) {
      setError('Vui lòng dán link Figma hoặc File Key trước khi chuyển đổi.')
      return
    }

    setHtmlLoading(true)
    try {
      localStorage.setItem('layoutly_token', token)
      const res = await figmaApi.html(token, fileKey)
      setHtmlResult(res.raw)
      setStatus('Đã chuyển Figma sang mã HTML.')
    } catch (err) {
      setError(err.message || String(err))
    } finally {
      setHtmlLoading(false)
    }
  }

  async function handlePreview() {
    setError('')
    setStatus('')
    setPreviewHtml('')

    if (!fileKey) {
      setError('Vui lòng dán link Figma hoặc File Key trước khi xem trước.')
      return
    }

    setPreviewLoading(true)
    try {
      localStorage.setItem('layoutly_token', token)
      const res = await figmaApi.preview(token, fileKey)
      updatePreviewHeight(res.raw)
      setPreviewHtml(res.raw)
      setStatus('Đã tạo bản xem trước.')
    } catch (err) {
      setError(err.message || String(err))
    } finally {
      setPreviewLoading(false)
    }
  }

  return (
    <main className="min-h-screen bg-white font-body text-zinc-950">
      <header className="sticky top-0 z-20 border-t-[5px] border-neutral-800 border-b border-zinc-200 bg-white">
        <nav className="mx-auto flex h-[70px] max-w-[1900px] items-center justify-between px-2.5" aria-label="Điều hướng chính">
          <section className="flex items-center gap-5" aria-label="Thương hiệu và công cụ">
            <BrandMark />

            <button
              type="button"
              className="hidden h-12 items-center gap-3 rounded-md border border-zinc-200 px-5 text-base font-medium text-zinc-900 md:flex"
              aria-haspopup="menu"
            >
              <Squares2X2Icon className="h-6 w-6" aria-hidden="true" />
              Công cụ
              <ChevronDownIcon className="h-4 w-4" aria-hidden="true" />
            </button>

            <ul className="hidden items-center gap-7 text-lg font-medium text-zinc-900 lg:flex">
              <li><a href="#convert" className="hover:text-[#0b5cff]">Chuyển Figma</a></li>
              <li><a href="#preview" className="hover:text-[#0b5cff]">Xem trước</a></li>
              <li><a href="#export" className="hover:text-[#0b5cff]">Xuất ZIP</a></li>
              <li><a href="#features" className="hover:text-[#0b5cff]">Tính năng</a></li>
              <li><a href="#guide" className="hover:text-[#0b5cff]">Hướng dẫn</a></li>
              <li><a href="#team" className="hover:text-[#0b5cff]">Nhóm</a></li>
            </ul>
          </section>

          <section className="hidden items-center gap-2.5 md:flex" aria-label="Tài khoản">
            <a href="#team" className="px-3 text-lg font-medium hover:text-[#0b5cff]">Nhóm</a>
            <NavButton>Đăng nhập</NavButton>
            <NavButton primary>Thử miễn phí</NavButton>
          </section>

          <button type="button" className="rounded-md p-2 text-zinc-900 md:hidden" aria-label="Mở menu">
            <Bars3Icon className="h-7 w-7" aria-hidden="true" />
          </button>
        </nav>
      </header>

      <article className="mx-auto max-w-[1420px] px-5 pb-20 pt-4">
        <nav className="mb-4 flex items-center gap-2 text-sm text-slate-600" aria-label="Breadcrumb">
          <a href="/" className="inline-flex items-center gap-1.5 hover:text-[#0b5cff]">
            <HomeIcon className="h-5 w-5" aria-hidden="true" />
            Trang Chủ
          </a>
          <span aria-hidden="true">›</span>
          <a href="#convert" className="hover:text-[#0b5cff]">Chuyển đổi Figma</a>
        </nav>

        <header className="mb-9 text-center">
          <h1 className="text-[40px] font-extrabold leading-tight tracking-[-0.01em] text-zinc-900 sm:text-5xl">
            Công cụ chuyển đổi Figma
          </h1>
        </header>

        <form id="convert" onSubmit={handleSubmit} aria-labelledby="converter-title">
          <section
            className="mx-auto flex min-h-[376px] max-w-[1420px] items-center justify-center rounded-lg bg-[#e82d2f] p-3 text-white"
            aria-labelledby="converter-title"
          >
            <fieldset className="flex min-h-[350px] w-full flex-col items-center justify-center rounded-md border border-dashed border-white/90 px-4 py-10 text-center">
              <legend id="converter-title" className="sr-only">Dán link Figma để chuyển đổi thành mã nguồn ZIP</legend>

              <DocumentArrowUpIcon className="mb-5 h-20 w-20 stroke-[1.25]" aria-hidden="true" />

              <label htmlFor="figma-url" className="sr-only">Link Figma hoặc File Key</label>
              <section className="flex w-full max-w-3xl overflow-hidden rounded-md bg-white text-zinc-900 shadow-sm" aria-label="Nhập link Figma">
                <span className="flex items-center border-r border-zinc-200 px-4" aria-hidden="true">
                  <LinkIcon className="h-6 w-6" />
                </span>
                <input
                  id="figma-url"
                  name="figma-url"
                  value={figmaUrl}
                  onChange={(event) => setFigmaUrl(event.target.value)}
                  placeholder="Dán link Figma hoặc File Key"
                  className="min-w-0 flex-1 px-4 py-4 text-base font-semibold outline-none placeholder:text-zinc-500"
                  autoComplete="url"
                />
                <button
                  type="submit"
                  disabled={loading}
                  className="flex items-center gap-2 border-l border-zinc-200 px-5 text-sm font-extrabold uppercase tracking-wide transition hover:bg-zinc-50 disabled:cursor-wait disabled:opacity-60"
                >
                  <ArrowDownTrayIcon className="h-5 w-5" aria-hidden="true" />
                  {loading ? 'Đang tạo' : 'ZIP'}
                </button>
                <button
                  type="button"
                  onClick={handleHtmlConvert}
                  disabled={htmlLoading}
                  className="flex items-center gap-2 border-l border-zinc-200 bg-zinc-950 px-5 text-sm font-extrabold uppercase tracking-wide text-white transition hover:bg-zinc-800 disabled:cursor-wait disabled:opacity-60"
                >
                  <CodeBracketIcon className="h-5 w-5" aria-hidden="true" />
                  {htmlLoading ? 'Đang tạo' : 'HTML'}
                </button>
                <button
                  type="button"
                  onClick={handlePreview}
                  disabled={previewLoading}
                  className="flex items-center gap-2 border-l border-zinc-200 bg-[#0b5cff] px-5 text-sm font-extrabold text-white transition hover:bg-[#064be0] disabled:cursor-wait disabled:opacity-60"
                >
                  <ComputerDesktopIcon className="h-5 w-5" aria-hidden="true" />
                  {previewLoading ? 'Đang tạo' : 'Xem trước'}
                </button>
              </section>

              <p className="mt-5 text-lg font-medium">hoặc thả link Figma vào đây</p>

              <button
                type="button"
                onClick={() => setIsTokenOpen((open) => !open)}
                className="mt-6 inline-flex items-center gap-2 rounded-md bg-white/10 px-4 py-2 text-sm font-semibold text-white ring-1 ring-white/30 transition hover:bg-white/15"
                aria-expanded={isTokenOpen}
                aria-controls="token-panel"
              >
                Token Figma
                <ChevronDownIcon className={`h-4 w-4 transition ${isTokenOpen ? 'rotate-180' : ''}`} aria-hidden="true" />
              </button>

              {isTokenOpen && (
                <section id="token-panel" className="mt-3 w-full max-w-xl">
                  <label htmlFor="figma-token" className="sr-only">Figma access token</label>
                  <input
                    id="figma-token"
                    name="figma-token"
                    type="password"
                    value={token}
                    onChange={(event) => setToken(event.target.value)}
                    placeholder="figd_xxxxxxxxxxxxxxxxxxxxx"
                    className="w-full rounded-md border border-white/30 bg-white px-4 py-3 text-sm font-medium text-zinc-900 outline-none placeholder:text-zinc-500"
                    autoComplete="off"
                  />
                </section>
              )}

              {(status || error) && (
                <p className="mt-4 text-sm font-semibold text-white" role={error ? 'alert' : 'status'}>
                  {error || status}
                </p>
              )}
            </fieldset>
          </section>
        </form>

        {previewHtml && (
          <section ref={previewPanelRef} id="preview" className="mx-auto mt-6 max-w-[1420px] rounded-lg border border-zinc-200 bg-white" aria-labelledby="visual-preview-title">
            <header className="flex items-center justify-between gap-4 border-b border-zinc-200 px-5 py-4">
              <section className="flex items-center gap-2">
                <ComputerDesktopIcon className="h-5 w-5" aria-hidden="true" />
                <h2 id="visual-preview-title" className="text-base font-extrabold text-zinc-950">Xem trước giao diện</h2>
              </section>
            </header>
            <section className="max-h-[760px] overflow-y-auto overflow-x-hidden bg-white" aria-label="Khung xem trước giao diện đã chuyển đổi">
            <iframe
              title="Xem trước HTML đã chuyển đổi"
              srcDoc={previewHtml}
              className="block w-full border-0 bg-white"
              style={{ height: `${previewHeight}px` }}
              scrolling="no"
              sandbox=""
            />
            </section>
          </section>
        )}

        {htmlResult && (
          <section id="html-code" className="mx-auto mt-6 max-w-[1420px] rounded-lg border border-zinc-200 bg-zinc-950 text-white" aria-labelledby="html-preview-title">
            <header className="flex items-center justify-between gap-4 border-b border-white/10 px-5 py-4">
              <section className="flex items-center gap-2">
                <CodeBracketIcon className="h-5 w-5" aria-hidden="true" />
                <h2 id="html-preview-title" className="text-base font-extrabold">Mã HTML đã chuyển đổi</h2>
              </section>
              <button
                type="button"
                onClick={() => navigator.clipboard?.writeText(htmlResult)}
                className="rounded-md border border-white/20 px-3 py-1.5 text-sm font-semibold text-white transition hover:bg-white/10"
              >
                Sao chép
              </button>
            </header>
            <pre className="max-h-[420px] overflow-auto p-5 text-left font-mono text-sm leading-6 text-zinc-100">
              <code>{htmlResult}</code>
            </pre>
          </section>
        )}

        <section id="features" className="mx-auto mt-8 grid max-w-[1420px] gap-8 lg:grid-cols-[1.3fr_1fr]" aria-labelledby="benefits-title">
          <h2 id="benefits-title" className="sr-only">Lợi ích khi chuyển đổi Figma bằng Layoutly</h2>

          <p className="max-w-[780px] text-[21px] font-medium leading-[1.62] text-black">
            Layoutly giúp lấy cấu trúc từ file Figma và xuất ra bộ mã nguồn cơ bản để bạn xem thử nhanh.
            Người dùng chỉ cần dán link, nhập token nếu cần, rồi tải file ZIP về máy.
          </p>

          <ul className="space-y-5 text-[19px] font-medium text-black">
            {[
              'Tự tách File Key từ link Figma',
              'Sinh file index.html và styles.css',
              'Tải xuống dưới dạng ZIP để mở thử ngay',
            ].map((item) => (
              <li key={item} className="flex items-start gap-3">
                <CheckCircleIcon className="mt-0.5 h-6 w-6 flex-none fill-[#00c853] stroke-white" aria-hidden="true" />
                <span>{item}</span>
              </li>
            ))}
          </ul>
        </section>

        <section id="guide" className="mx-auto mt-28 max-w-[1420px]" aria-labelledby="guide-title">
          <h2 id="guide-title" className="sr-only">Hướng dẫn dùng Layoutly</h2>

          <section className="grid gap-x-24 gap-y-16 lg:grid-cols-2" aria-label="Thông tin hướng dẫn">
            <InfoBlock icon={KeyIcon} title="Cách lấy token Figma">
              Vào Figma, bấm avatar tài khoản rồi mở Settings. Kéo xuống phần Personal access tokens,
              tạo token mới với tên dễ nhớ như Layoutly Export, sau đó copy token và dán vào ô Token Figma trên trang.
            </InfoBlock>

            <InfoBlock icon={ShieldCheckIcon} title="Giữ token an toàn">
              Token giống như chìa khóa đọc file Figma của bạn. Chỉ dùng trên máy cá nhân, không gửi cho người lạ,
              không chụp màn hình công khai và không commit token vào source code.
            </InfoBlock>

            <InfoBlock icon={CursorArrowRaysIcon} title="Dán link Figma để bắt đầu">
              Mở file thiết kế trong Figma rồi copy đường dẫn trên trình duyệt. Bạn có thể dán nguyên link,
              Layoutly sẽ tự lấy File Key nên không cần tách thủ công.
            </InfoBlock>

            <InfoBlock icon={CodeBracketIcon} title="Kết quả sau khi chuyển đổi">
              Khi bấm ZIP, backend sẽ đọc file Figma, dựng cây giao diện, sinh HTML/CSS và đóng gói thành file tải về.
              Bản hiện tại phù hợp để xem cấu trúc layout và demo luồng Figma sang code.
            </InfoBlock>

            <InfoBlock icon={ComputerDesktopIcon} title="Mở được trên nhiều máy">
              File ZIP tải về có thể giải nén trên Windows, macOS hoặc Linux. Sau khi giải nén, bạn mở index.html
              để kiểm tra nhanh phần giao diện đã được sinh ra.
            </InfoBlock>

            <InfoBlock icon={CloudArrowDownIcon} title="Nếu chuyển đổi bị lỗi">
              Kiểm tra lại quyền xem file Figma, token đã dán đúng chưa và link có phải link file thiết kế không.
              Nếu gọi nhiều lần bị giới hạn, đợi một lúc rồi thử lại.
            </InfoBlock>
          </section>
        </section>
      </article>

      <footer id="team" className="layoutly-footer">
        <section className="layoutly-footer__inner">
          <article className="layoutly-footer__brand">
            <BrandMark />
            <p className="layoutly-footer__copy">
              Chúng tôi làm giúp việc chuyển đổi giao diện Figma sang mã nguồn dễ thao tác hơn.
            </p>
          </article>

          <nav className="layoutly-footer__column" aria-label="Giải pháp Layoutly">
            <h2>Các giải pháp</h2>
            <ul>
              <li><a href="#convert" className="hover:text-[#0b5cff]">Chuyển Figma</a></li>
              <li><a href="#preview" className="hover:text-[#0b5cff]">Xem trước</a></li>
              <li><a href="#export" className="hover:text-[#0b5cff]">Xuất ZIP</a></li>
            </ul>
          </nav>

          <nav className="layoutly-footer__column" aria-label="Công ty">
            <h2>Công ty</h2>
            <ul>
              <li><a href="#team" className="hover:text-[#0b5cff]">Về chúng tôi</a></li>
              <li><a href="#guide" className="hover:text-[#0b5cff]">Hỗ trợ</a></li>
              <li><a href="#guide" className="hover:text-[#0b5cff]">Hướng dẫn</a></li>
            </ul>
          </nav>

          <nav className="layoutly-footer__column" aria-label="Sản phẩm">
            <h2>Sản phẩm</h2>
            <ul>
              <li><a href="#features" className="hover:text-[#0b5cff]">Tính năng</a></li>
              <li><a href="#team" className="hover:text-[#0b5cff]">Nhóm</a></li>
              <li><a href="#guide" className="hover:text-[#0b5cff]">Token Figma</a></li>
              <li><a href="#features" className="hover:text-[#0b5cff]">Developers</a></li>
            </ul>
          </nav>

          <section className="layoutly-footer__column" aria-labelledby="team-title">
            <h2 id="team-title">Nhóm</h2>
            <ul>
              <li>Thành</li>
              <li>Tấn</li>
              <li>Layoutly 2026</li>
            </ul>
          </section>
        </section>

        <section className="layoutly-footer__bottom">
          © 2026 Layoutly. Built by Thành & Tấn.
        </section>
      </footer>
    </main>
  )
}
