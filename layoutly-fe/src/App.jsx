import React, { useEffect, useMemo, useState } from 'react'
import {
  ArrowDownTrayIcon,
  ArrowLeftIcon,
  ArrowPathIcon,
  ArrowRightIcon,
  ArrowTopRightOnSquareIcon,
  BeakerIcon,
  BoltIcon,
  CheckCircleIcon,
  CircleStackIcon,
  ClipboardDocumentIcon,
  CodeBracketIcon,
  ComputerDesktopIcon,
  DevicePhoneMobileIcon,
  EyeIcon,
  EyeSlashIcon,
  FolderIcon,
  LinkIcon,
  MoonIcon,
  QuestionMarkCircleIcon,
  Squares2X2Icon,
} from '@heroicons/react/24/outline'
import { downloadSnapshotExport, snapshotApi } from './lib/api.js'

const VIEWPORTS = [
  { key: 'desktop', label: 'Desktop', width: 1440 },
  { key: 'laptop', label: 'Laptop', width: 1280 },
  { key: 'tablet', label: 'Tablet', width: 768 },
  { key: 'mobile', label: 'Mobile', width: 430 },
  { key: 'mobile-small', label: 'Mobile S', width: 390 },
]

function extractFileKey(input) {
  const value = input.trim()
  if (!value) return ''
  try {
    const url = new URL(value)
    return url.pathname.match(/\/(?:file|design)\/([^/]+)/)?.[1] || value
  } catch {
    return value
  }
}

function formatDate(value) {
  if (!value) return 'Không có dữ liệu'
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(date)
}

function errorMessage(error) {
  if (error?.code === 'RATE_LIMITED') {
    const seconds = Number(error.retryAfterSeconds) || 60
    const duration = seconds >= 86400
      ? `${Math.ceil(seconds / 86400)} ngày`
      : seconds >= 3600
        ? `${Math.ceil(seconds / 3600)} giờ`
        : `${Math.ceil(seconds / 60)} phút`
    const retryAt = error.retryAt
      ? new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(error.retryAt))
      : null
    const plan = error.planTier ? ` Gói hiện tại: ${error.planTier}.` : ''
    return `Figma đang giới hạn API. Thử lại sau khoảng ${duration}${retryAt ? ` (${retryAt})` : ''}.${plan}`
  }
  if (error?.message === 'Failed to fetch') {
    return 'Không thể nhận phản hồi từ backend. Hãy kiểm tra backend rồi thử lại.'
  }
  return error?.message || String(error)
}

function BrandMark() {
  return (
    <a href="/" className="flex items-center gap-2.5" aria-label="Layoutly trang chủ">
      <span className="relative grid h-10 w-10 grid-cols-2 gap-1 rounded-md border border-slate-200 bg-white p-1.5 shadow-sm" aria-hidden="true">
        <span className="rounded-[3px] bg-[#1769ff]" />
        <span className="rounded-[3px] bg-[#22c55e]" />
        <span className="rounded-[3px] bg-[#f97316]" />
        <span className="rounded-[3px] bg-[#6d4aff]" />
        <span className="absolute left-1/2 top-1/2 grid h-5 w-5 -translate-x-1/2 -translate-y-1/2 place-items-center rounded-full bg-white shadow ring-1 ring-slate-200">
          <ArrowRightIcon className="h-3.5 w-3.5 stroke-[2.5] text-[#1769ff]" />
        </span>
      </span>
      <span className="leading-none">
        <strong className="block text-[21px] font-extrabold text-slate-950">Layoutly</strong>
        <span className="mt-1 block text-[8px] font-bold uppercase tracking-[0.18em] text-slate-500">Figma to code</span>
      </span>
    </a>
  )
}

function Header({ connected }) {
  return (
    <header className="border-b border-slate-200 bg-white">
      <nav className="mx-auto flex h-[62px] max-w-[1540px] items-center justify-between px-4" aria-label="Điều hướng chính">
        <div className="flex min-w-0 items-center gap-8">
          <BrandMark />
          <div className="hidden h-[62px] items-stretch text-sm font-semibold text-slate-700 md:flex">
            <a href="#converter" className="flex items-center border-b-2 border-[#1769ff] bg-blue-50/70 px-5 text-[#1255d8]">Chuyển Figma</a>
            <a href="#workspace" className="flex items-center px-5 hover:text-[#1769ff]">Dự án</a>
            <a href="#workspace" className="flex items-center px-5 hover:text-[#1769ff]">Test Cases</a>
            <a href="#workspace" className="flex items-center px-5 hover:text-[#1769ff]">Tài liệu</a>
          </div>
        </div>
        <div className="flex items-center gap-3">
          <button type="button" className="hidden h-9 w-9 place-items-center text-slate-700 sm:grid" title="Giao diện">
            <MoonIcon className="h-5 w-5" />
          </button>
          <a href="#figma-connection" className="hidden h-10 items-center gap-2 rounded-md border border-blue-200 px-4 text-sm font-bold text-slate-800 hover:bg-blue-50 sm:flex">
            <Squares2X2Icon className="h-5 w-5 text-[#1769ff]" />
            Kết nối Figma
          </a>
          <span className="hidden items-center gap-2 text-xs font-semibold text-slate-600 lg:flex">
            <span className={`h-2 w-2 rounded-full ${connected ? 'bg-emerald-500' : 'bg-slate-300'}`} />
            {connected ? 'Đã kết nối' : 'Chưa kết nối'}
          </span>
          <span className="grid h-9 w-9 place-items-center rounded-full bg-indigo-100 text-sm font-bold text-indigo-600">N</span>
        </div>
      </nav>
    </header>
  )
}

function SyncBadge() {
  return (
    <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-2.5 py-1 text-xs font-bold text-emerald-700">
      <CheckCircleIcon className="h-4 w-4" />
      Đã đồng bộ
    </span>
  )
}

function StatRow({ label, value, icon: Icon }) {
  return (
    <li className="flex min-h-8 items-center justify-between gap-3 text-sm">
      <span className="flex min-w-0 items-center gap-2 text-slate-600">
        <Icon className="h-4 w-4 flex-none text-slate-500" />
        <span className="truncate">{label}</span>
      </span>
      <strong className="text-slate-950">{value ?? 0}</strong>
    </li>
  )
}

function CodeViewer({ snapshot, activeTab, onTabChange, code, loading }) {
  return (
    <section className="flex h-full min-h-[570px] min-w-0 flex-col border-l border-slate-200 bg-white xl:min-h-0" aria-label="Mã nguồn">
      <div className="flex h-12 items-end border-b border-slate-200 px-3">
        {['HTML', 'CSS', 'REACT'].map((tab) => (
          <button
            key={tab}
            type="button"
            disabled={!snapshot}
            onClick={() => onTabChange(tab)}
            className={`h-12 border-b-2 px-4 text-sm font-bold transition disabled:opacity-40 ${
              activeTab === tab ? 'border-[#1769ff] text-[#1769ff]' : 'border-transparent text-slate-500 hover:text-slate-900'
            }`}
          >
            {tab === 'REACT' ? 'React' : tab}
          </button>
        ))}
      </div>
      <div className="flex items-center justify-between border-b border-white/10 bg-[#101720] px-4 py-2">
        <span className="text-xs font-semibold text-slate-400">
          {activeTab === 'REACT' ? 'Page.jsx' : activeTab === 'CSS' ? 'styles.css' : 'index.html'}
        </span>
        <button
          type="button"
          disabled={!code}
          onClick={() => navigator.clipboard?.writeText(code)}
          className="inline-flex h-8 items-center gap-2 rounded-md border border-slate-600 px-3 text-xs font-bold text-slate-100 hover:bg-white/10 disabled:opacity-40"
        >
          <ClipboardDocumentIcon className="h-4 w-4" />
          Sao chép
        </button>
      </div>
      <pre className="m-0 min-h-0 min-w-0 flex-1 overflow-auto bg-[#101720] p-5 font-mono text-[12px] leading-6 text-sky-100">
        <code className="block min-w-max">{loading ? 'Đang tạo mã nguồn...' : code || 'Phân tích một thiết kế để xem mã nguồn.'}</code>
      </pre>
    </section>
  )
}

function ViewportButtons({ selected, onChange, compact = false }) {
  return (
    <div className="flex flex-wrap items-center gap-2" aria-label="Kích thước preview">
      {VIEWPORTS.map((viewport) => (
        <button
          key={viewport.key}
          type="button"
          onClick={() => onChange(viewport)}
          className={`inline-flex h-8 items-center gap-1.5 rounded-md border px-3 text-xs font-bold transition ${
            selected.key === viewport.key
              ? 'border-blue-200 bg-blue-50 text-[#1769ff]'
              : 'border-slate-200 bg-white text-slate-600 hover:border-slate-300'
          }`}
          title={`${viewport.label} ${viewport.width}px`}
        >
          {viewport.width <= 430
            ? <DevicePhoneMobileIcon className="h-3.5 w-3.5" />
            : <ComputerDesktopIcon className="h-3.5 w-3.5" />}
          {viewport.label}{compact ? '' : ` (${viewport.width})`}
        </button>
      ))}
    </div>
  )
}

function PreviewPanel({ snapshot, viewport, onViewportChange, previewVersion }) {
  const previewWidth = viewport.width <= 768 ? `${viewport.width}px` : '100%'
  return (
    <section className="flex h-full min-h-[570px] min-w-0 flex-col bg-white xl:min-h-0" aria-label="Xem trước thiết kế">
      <div className="flex min-h-12 flex-wrap items-center justify-between gap-2 border-b border-slate-200 px-3 py-2">
        <ViewportButtons selected={viewport} onChange={onViewportChange} compact />
        {snapshot && (
          <button
            type="button"
            onClick={() => window.open(`/preview/${snapshot.snapshotId}`, '_blank', 'noopener,noreferrer')}
            className="inline-flex h-8 items-center gap-1.5 rounded-md border border-blue-200 px-3 text-xs font-bold text-[#1769ff] hover:bg-blue-50"
          >
            <ArrowTopRightOnSquareIcon className="h-4 w-4" />
            Mở trong tab mới
          </button>
        )}
      </div>
      <div className="flex min-h-0 flex-1 justify-center overflow-auto bg-[#f5f7fb] p-3">
        {snapshot ? (
          <div className="h-full min-h-[460px] max-w-full overflow-hidden border border-slate-200 bg-white shadow-sm xl:min-h-0" style={{ width: previewWidth }}>
            <iframe
              key={`${previewVersion}-${viewport.key}`}
              title="Snapshot preview"
              src={`/api/snapshots/${snapshot.snapshotId}/preview?targetWidth=${viewport.width}&v=${previewVersion}`}
              className="h-full w-full border-0 bg-white"
            />
          </div>
        ) : (
          <div className="grid min-h-[460px] w-full place-items-center text-center text-slate-500">
            <div>
              <ComputerDesktopIcon className="mx-auto h-11 w-11 stroke-[1.3] text-slate-400" />
              <p className="mt-3 text-sm font-semibold">Preview sẽ xuất hiện sau khi phân tích thiết kế.</p>
            </div>
          </div>
        )}
      </div>
    </section>
  )
}

function PreviewPage({ snapshotId }) {
  const [snapshot, setSnapshot] = useState(null)
  const [viewport, setViewport] = useState(VIEWPORTS[0])
  const [error, setError] = useState('')

  useEffect(() => {
    snapshotApi.get(snapshotId)
      .then((response) => setSnapshot(response.json))
      .catch((caught) => setError(errorMessage(caught)))
  }, [snapshotId])

  return (
    <main className="flex h-screen min-h-0 flex-col bg-[#eef2f7] text-slate-950">
      <Header connected={Boolean(snapshot)} />
      <section className="flex min-h-14 flex-wrap items-center justify-between gap-3 border-b border-slate-200 bg-white px-4 py-2">
        <div className="flex items-center gap-3">
          <a href="/" className="grid h-8 w-8 place-items-center rounded-md border border-slate-200" title="Quay lại workspace">
            <ArrowLeftIcon className="h-4 w-4" />
          </a>
          <div>
            <h1 className="text-sm font-extrabold">{snapshot?.fileName || 'Snapshot Preview'}</h1>
            <p className="text-xs text-slate-500">{viewport.label} · {viewport.width}px</p>
          </div>
        </div>
        <ViewportButtons selected={viewport} onChange={setViewport} compact />
      </section>
      <section className="flex min-h-0 flex-1 justify-center overflow-auto p-4">
        {error ? (
          <p className="m-auto rounded-md bg-red-50 px-4 py-3 text-sm font-semibold text-red-700">{error}</p>
        ) : (
          <iframe
            title="Layoutly full preview"
            src={`/api/snapshots/${snapshotId}/preview?targetWidth=${viewport.width}`}
            className="h-full min-h-[600px] max-w-full border border-slate-300 bg-white shadow-lg"
            style={{ width: `${viewport.width}px` }}
          />
        )}
      </section>
    </main>
  )
}

function WorkspaceApp() {
  const [importMode, setImportMode] = useState('BRIDGE')
  const [figmaInput, setFigmaInput] = useState('')
  const [token, setToken] = useState('')
  const [showToken, setShowToken] = useState(false)
  const [connected, setConnected] = useState(false)
  const [snapshot, setSnapshot] = useState(null)
  const [activeTab, setActiveTab] = useState('HTML')
  const [codeByTab, setCodeByTab] = useState({})
  const [codeLoading, setCodeLoading] = useState(false)
  const [busy, setBusy] = useState('')
  const [error, setError] = useState('')
  const [status, setStatus] = useState('')
  const [viewport, setViewport] = useState(VIEWPORTS[0])
  const [previewVersion, setPreviewVersion] = useState(0)
  const [exportType, setExportType] = useState('AUTO')
  const [bridgeReady, setBridgeReady] = useState(false)
  const [showBridgeGuide, setShowBridgeGuide] = useState(false)

  const fileKey = useMemo(() => extractFileKey(figmaInput), [figmaInput])
  const structure = snapshot?.structure || {}

  useEffect(() => {
    const linkedSnapshotId = new URLSearchParams(window.location.search).get('snapshotId')
    const savedSnapshotId = linkedSnapshotId || sessionStorage.getItem('layoutly_snapshot_id')
    if (!savedSnapshotId) return
    snapshotApi.get(savedSnapshotId)
      .then((response) => {
        setSnapshot(response.json)
        setConnected(true)
        setImportMode(response.json.source === 'FIGMA_PLUGIN' ? 'BRIDGE' : 'REST')
        sessionStorage.setItem('layoutly_snapshot_id', savedSnapshotId)
        loadCode(savedSnapshotId, 'HTML')
        if (linkedSnapshotId) window.history.replaceState({}, '', window.location.pathname)
      })
      .catch(() => sessionStorage.removeItem('layoutly_snapshot_id'))
  }, [])

  useEffect(() => {
    if (importMode !== 'BRIDGE') return
    snapshotApi.bridgeStatus()
      .then(() => setBridgeReady(true))
      .catch(() => setBridgeReady(false))
  }, [importMode])

  async function loadCode(snapshotId, tab) {
    setCodeLoading(true)
    try {
      const key = tab.toLowerCase()
      const response = await snapshotApi[key](snapshotId)
      setCodeByTab((current) => ({ ...current, [tab]: response.raw }))
    } catch (caught) {
      setError(errorMessage(caught))
    } finally {
      setCodeLoading(false)
    }
  }

  async function handleTabChange(tab) {
    setActiveTab(tab)
    if (snapshot && !codeByTab[tab]) await loadCode(snapshot.snapshotId, tab)
  }

  async function handleImport(event) {
    event.preventDefault()
    setError('')
    setStatus('')
    if (importMode === 'BRIDGE') {
      setShowBridgeGuide(true)
      return
    }
    if (!fileKey) {
      setError('Hãy dán URL Figma hoặc nhập File Key.')
      return
    }

    setBusy('import')
    try {
      const response = await snapshotApi.importFigma(token, fileKey)
      setSnapshot(response.json)
      setConnected(true)
      setCodeByTab({})
      setActiveTab('HTML')
      sessionStorage.setItem('layoutly_snapshot_id', response.json.snapshotId)
      await loadCode(response.json.snapshotId, 'HTML')
      setStatus('Đã tạo snapshot. Các thao tác tiếp theo không gọi lại file Figma.')
      setPreviewVersion((value) => value + 1)
    } catch (caught) {
      setError(errorMessage(caught))
    } finally {
      setBusy('')
    }
  }

  async function handleRefresh() {
    if (!snapshot) return
    if (!window.confirm('Làm mới sẽ thay thế snapshot và mã nguồn đang xem. Tiếp tục?')) return
    setBusy('refresh')
    setError('')
    try {
      const response = await snapshotApi.refresh(token, snapshot.snapshotId)
      setSnapshot(response.json)
      setCodeByTab({})
      setActiveTab('HTML')
      await loadCode(response.json.snapshotId, 'HTML')
      setPreviewVersion((value) => value + 1)
      setStatus('Đã làm mới snapshot từ Figma.')
    } catch (caught) {
      setError(errorMessage(caught))
    } finally {
      setBusy('')
    }
  }

  async function handleExport(format) {
    if (!snapshot) return
    setBusy(`export-${format}`)
    setError('')
    try {
      await downloadSnapshotExport(snapshot.snapshotId, format, format === 'HTML' ? exportType : 'AUTO')
      setStatus(`Đã xuất ${format === 'REACT' ? 'React/Vite' : 'HTML/CSS'} từ snapshot.`)
    } catch (caught) {
      setError(errorMessage(caught))
    } finally {
      setBusy('')
    }
  }

  async function handleSaveFixture() {
    if (!snapshot) return
    const suggested = snapshot.fileName?.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '') || 'layout-case'
    const caseName = window.prompt('Tên test case (ví dụ: landing-horizontal)', suggested)
    if (!caseName) return
    setBusy('fixture')
    setError('')
    try {
      const response = await snapshotApi.saveFixture(snapshot.snapshotId, caseName)
      setStatus(`Đã lưu test fixture tại ${response.json.path}`)
    } catch (caught) {
      setError(errorMessage(caught))
    } finally {
      setBusy('')
    }
  }

  return (
    <main className="min-h-screen bg-[#f4f7fb] text-slate-950">
      <Header connected={connected || Boolean(snapshot)} />

      <div className="mx-auto max-w-[1540px] space-y-3 px-4 py-3">
        <section id="converter" className="grid gap-3 lg:grid-cols-[minmax(0,2fr)_minmax(340px,0.95fr)]">
          <form onSubmit={handleImport} className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
            <h1 className="text-2xl font-extrabold text-slate-950">Import thiết kế Figma</h1>
            <p className="mt-1 text-sm font-medium text-slate-500">Figma → Snapshot → HTML/CSS / React</p>
            <div className="mt-4 grid grid-cols-1 gap-2 rounded-md bg-slate-100 p-1 sm:grid-cols-2" aria-label="Phương thức import">
              <button
                type="button"
                onClick={() => setImportMode('BRIDGE')}
                className={`flex min-h-12 items-center justify-center gap-2 rounded-md px-3 text-sm font-bold ${importMode === 'BRIDGE' ? 'bg-white text-[#1769ff] shadow-sm' : 'text-slate-600'}`}
              >
                <BoltIcon className="h-5 w-5" /> Đồng bộ từ Figma
                <span className="rounded bg-emerald-100 px-1.5 py-0.5 text-[10px] uppercase text-emerald-700">Khuyên dùng</span>
              </button>
              <button
                type="button"
                onClick={() => setImportMode('REST')}
                className={`flex min-h-12 items-center justify-center gap-2 rounded-md px-3 text-sm font-bold ${importMode === 'REST' ? 'bg-white text-[#1769ff] shadow-sm' : 'text-slate-600'}`}
              >
                <LinkIcon className="h-5 w-5" /> Nhập bằng URL
              </button>
            </div>

            {importMode === 'BRIDGE' ? (
              <div className="mt-4 rounded-md border border-blue-100 bg-blue-50 p-4">
                <div className="flex items-center justify-between gap-3">
                  <div>
                    <p className="text-sm font-extrabold text-slate-900">Waiting for Layoutly Bridge</p>
                    <p className="mt-1 text-xs text-slate-600">Không cần Personal Access Token. Chọn Frame trong Figma rồi Sync to Layoutly.</p>
                  </div>
                  <span className={`h-2.5 w-2.5 flex-none rounded-full ${bridgeReady ? 'bg-emerald-500' : 'bg-amber-400'}`} title={bridgeReady ? 'Backend sẵn sàng' : 'Chưa kết nối backend'} />
                </div>
                <button type="button" onClick={() => setShowBridgeGuide((value) => !value)} className="mt-3 inline-flex h-9 items-center gap-2 rounded-md border border-blue-200 bg-white px-3 text-xs font-bold text-[#1769ff] hover:bg-blue-50">
                  <QuestionMarkCircleIcon className="h-4 w-4" /> Hướng dẫn kết nối
                </button>
                {showBridgeGuide && (
                  <p className="mt-3 border-t border-blue-100 pt-3 text-xs leading-5 text-slate-600">
                    Mở Figma Desktop → Plugins → Development → Layoutly Bridge, chọn Frame và nhấn “Sync to Layoutly”.
                  </p>
                )}
              </div>
            ) : (
              <>
                <div className="mt-4 flex flex-col gap-2 sm:flex-row">
                  <label className="flex min-w-0 flex-1 items-center rounded-md border border-slate-300 bg-white focus-within:border-[#1769ff] focus-within:ring-2 focus-within:ring-blue-100">
                    <LinkIcon className="ml-4 h-5 w-5 flex-none text-slate-500" />
                    <span className="sr-only">Dán URL Figma hoặc File Key</span>
                    <input
                      value={figmaInput}
                      onChange={(event) => setFigmaInput(event.target.value)}
                      placeholder="Dán URL Figma hoặc File Key"
                      className="h-12 min-w-0 flex-1 bg-transparent px-3 text-sm font-semibold outline-none placeholder:font-medium placeholder:text-slate-400"
                    />
                  </label>
                  <button
                    type="submit"
                    disabled={busy === 'import'}
                    className="inline-flex h-12 items-center justify-center gap-3 rounded-md bg-[#1769ff] px-7 text-sm font-extrabold text-white shadow-sm hover:bg-[#0f55d8] disabled:cursor-wait disabled:opacity-60"
                  >
                    {busy === 'import' ? <ArrowPathIcon className="h-5 w-5 animate-spin" /> : <CodeBracketIcon className="h-5 w-5" />}
                    {busy === 'import' ? 'Đang phân tích' : 'Phân tích thiết kế'}
                    {busy !== 'import' && <ArrowRightIcon className="h-5 w-5" />}
                  </button>
                </div>
                <p className="mt-2 break-words text-xs text-slate-500">Ví dụ: https://www.figma.com/design/xxxxx hoặc chỉ cần File Key.</p>
                <p className="mt-1 text-xs font-medium text-amber-700">REST import có thể chịu giới hạn request của Figma.</p>
              </>
            )}
          </form>

          <section id="figma-connection" className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
            <div className="flex items-center justify-between gap-3">
              <h2 className="flex items-center gap-2 text-base font-extrabold">
                <Squares2X2Icon className="h-6 w-6 text-[#1769ff]" />
                {importMode === 'BRIDGE' ? 'Layoutly Bridge' : 'Kết nối Figma REST'}
              </h2>
              {(importMode === 'BRIDGE' ? bridgeReady : connected || snapshot) && <SyncBadge />}
            </div>
            {importMode === 'REST' ? (
              <>
                <div className="mt-4 flex flex-col gap-2 sm:flex-row">
                  <label className="flex min-w-0 flex-1 items-center rounded-md border border-slate-200 bg-slate-50">
                    <span className="sr-only">Figma Personal Access Token</span>
                    <input
                      type={showToken ? 'text' : 'password'}
                      value={token}
                      onChange={(event) => setToken(event.target.value)}
                      placeholder="Figma Personal Access Token"
                      autoComplete="off"
                      className="h-10 min-w-0 flex-1 bg-transparent px-3 text-sm outline-none"
                    />
                    <button type="button" onClick={() => setShowToken((value) => !value)} className="grid h-10 w-10 place-items-center text-slate-600" title={showToken ? 'Ẩn token' : 'Hiện token'}>
                      {showToken ? <EyeSlashIcon className="h-5 w-5" /> : <EyeIcon className="h-5 w-5" />}
                    </button>
                  </label>
                  <button
                    type="button"
                    onClick={() => {
                      if (!token.trim()) setError('Hãy nhập Figma token trước khi kết nối.')
                      else {
                        setConnected(true)
                        setError('')
                        setStatus('Token đã sẵn sàng trong phiên làm việc hiện tại.')
                      }
                    }}
                    className="h-10 flex-none rounded-md border border-blue-200 px-4 text-sm font-bold text-[#1769ff] hover:bg-blue-50"
                  >
                    Kết nối
                  </button>
                </div>
                <p className="mt-2 break-all text-xs leading-5 text-slate-500">Token chỉ được giữ trong bộ nhớ phiên, không lưu vào localStorage.</p>
              </>
            ) : (
              <div className="mt-4 rounded-md border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600">
                <p className="font-bold text-slate-900">{bridgeReady ? 'Backend Bridge sẵn sàng' : 'Đang chờ backend tại localhost:8080'}</p>
                <p className="mt-2 text-xs leading-5">Dữ liệu thiết kế và assets được gửi trực tiếp từ Figma Plugin vào snapshot cục bộ.</p>
              </div>
            )}
          </section>
        </section>

        {(error || status) && (
          <section className={`rounded-md border px-4 py-3 text-sm font-semibold ${error ? 'border-red-200 bg-red-50 text-red-700' : 'border-emerald-200 bg-emerald-50 text-emerald-700'}`} role={error ? 'alert' : 'status'}>
            {error || status}
          </section>
        )}

        {snapshot && (
          <section className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-slate-200 bg-white px-4 py-3 shadow-sm">
            <div className="flex min-w-0 items-center gap-3">
              <span className="grid h-12 w-12 flex-none place-items-center rounded-md bg-gradient-to-br from-blue-50 to-indigo-100 text-[#1769ff]">
                <FolderIcon className="h-6 w-6" />
              </span>
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <h2 className="truncate text-sm font-extrabold">{snapshot.fileName}</h2>
                  <SyncBadge />
                  <span className="rounded bg-slate-100 px-2 py-1 text-[10px] font-bold text-slate-600">
                    {snapshot.source === 'FIGMA_PLUGIN' ? 'FIGMA BRIDGE' : 'FIGMA REST'}
                  </span>
                </div>
                <p className="mt-1 truncate text-xs text-slate-500">
                  File Key: {snapshot.fileKey} <span className="mx-2">|</span>
                  Frame: {snapshot.rootNodeName || '-'} <span className="mx-2">|</span>
                  Snapshot: {formatDate(snapshot.fetchedAt)}
                </p>
              </div>
            </div>
            {snapshot.source === 'FIGMA_REST' ? <button
              type="button"
              onClick={handleRefresh}
              disabled={busy === 'refresh'}
              className="inline-flex h-10 items-center gap-2 rounded-md border border-blue-200 px-4 text-sm font-bold text-[#1769ff] hover:bg-blue-50 disabled:opacity-50"
            >
              <ArrowPathIcon className={`h-4 w-4 ${busy === 'refresh' ? 'animate-spin' : ''}`} />
              Làm mới từ Figma
            </button> : <span className="text-xs font-bold text-emerald-700">Snapshot ready</span>}
          </section>
        )}

        <section id="workspace" className="rounded-lg border border-slate-200 bg-white shadow-sm">
          <div className="grid overflow-hidden rounded-t-lg xl:h-[640px] xl:min-h-0 xl:grid-cols-[230px_minmax(0,1fr)_390px]">
            <aside className="border-b border-slate-200 bg-white p-4 xl:min-h-0 xl:overflow-y-auto xl:border-b-0 xl:border-r" aria-label="Thông tin thiết kế">
              <h2 className="text-sm font-extrabold">Thông tin thiết kế</h2>
              <ul className="mt-3 space-y-1">
                <StatRow label="Canvas" value={structure.canvasCount} icon={FolderIcon} />
                <StatRow label="Frame" value={structure.frameCount} icon={ComputerDesktopIcon} />
                <StatRow label="Tổng số node" value={structure.nodeCount} icon={Squares2X2Icon} />
                <StatRow label="Độ sâu tối đa" value={structure.maxDepth} icon={ArrowDownTrayIcon} />
              </ul>
              <div className="my-4 border-t border-slate-200" />
              <h3 className="text-sm font-extrabold">Thống kê layout</h3>
              <ul className="mt-3 space-y-1">
                <StatRow label="Auto Layout" value={structure.autoLayoutCount} icon={Squares2X2Icon} />
                <StatRow label="Flex" value={structure.flexCount} icon={ArrowRightIcon} />
                <StatRow label="Grid" value={structure.gridCount} icon={Squares2X2Icon} />
                <StatRow label="Absolute" value={structure.absoluteCount} icon={ArrowTopRightOnSquareIcon} />
              </ul>
              <div className="my-4 border-t border-slate-200" />
              <h3 className="text-sm font-extrabold">Assets</h3>
              <ul className="mt-3 space-y-1">
                <StatRow label="Hình ảnh" value={structure.imageCount} icon={ComputerDesktopIcon} />
                <StatRow label="Vector / SVG" value={structure.vectorCount} icon={CodeBracketIcon} />
                <StatRow label="Đã cache" value={snapshot?.assets?.cachedAssetCount} icon={CircleStackIcon} />
              </ul>
              {snapshot && (
                <div className="mt-5 rounded-md border border-blue-100 bg-blue-50 p-3">
                  <p className="flex items-center gap-2 text-xs font-extrabold text-[#1769ff]">
                    <CheckCircleIcon className="h-4 w-4" /> Figma Snapshot
                  </p>
                  <p className="mt-1 text-[11px] leading-4 text-slate-600">Dữ liệu đã cache; các thao tác tiếp theo không gọi lại file Figma.</p>
                </div>
              )}
            </aside>

            <PreviewPanel snapshot={snapshot} viewport={viewport} onViewportChange={setViewport} previewVersion={previewVersion} />
            <CodeViewer
              snapshot={snapshot}
              activeTab={activeTab}
              onTabChange={handleTabChange}
              code={codeByTab[activeTab]}
              loading={codeLoading}
            />
          </div>

          <div className="sticky bottom-0 z-30 flex flex-col justify-between gap-3 rounded-b-lg border-t border-slate-200 bg-white px-4 py-3 shadow-[0_-8px_24px_rgba(15,23,42,0.10)] sm:flex-row sm:items-center">
            <button type="button" onClick={() => window.scrollTo({ top: 0, behavior: 'smooth' })} className="inline-flex h-10 items-center justify-center gap-2 rounded-md border border-slate-200 px-4 text-sm font-bold text-slate-700 hover:bg-slate-50">
              <ArrowLeftIcon className="h-4 w-4" /> Quay lại
            </button>
            <div className="flex flex-wrap items-center gap-2">
              <button type="button" disabled={!snapshot || busy === 'fixture'} onClick={handleSaveFixture} className="inline-flex h-10 items-center gap-2 rounded-md border border-slate-300 px-4 text-sm font-bold text-slate-700 hover:bg-slate-50 disabled:opacity-40">
                <BeakerIcon className="h-4 w-4" /> Lưu thành Test Case
              </button>
              <label htmlFor="export-type" className="sr-only">Tách HTML theo</label>
              <select
                id="export-type"
                value={exportType}
                onChange={(event) => setExportType(event.target.value)}
                disabled={!snapshot || busy === 'export-HTML'}
                title="Chọn cách tách file HTML"
                className="h-10 rounded-md border border-slate-300 bg-white px-3 text-sm font-bold text-slate-700 outline-none hover:bg-slate-50 focus:border-[#1769ff] focus:ring-2 focus:ring-blue-100 disabled:opacity-40"
              >
                <option value="AUTO">Tách tự động</option>
                <option value="CANVAS">Tách theo Page</option>
                <option value="FRAME">Tách theo Frame</option>
              </select>
              <button type="button" disabled={!snapshot || busy === 'export-HTML'} onClick={() => handleExport('HTML')} className="inline-flex h-10 items-center gap-2 rounded-md border border-[#1769ff] px-5 text-sm font-extrabold text-[#1769ff] hover:bg-blue-50 disabled:opacity-40">
                <ArrowDownTrayIcon className="h-4 w-4" /> Xuất HTML/CSS
              </button>
              <button type="button" disabled={!snapshot || busy === 'export-REACT'} onClick={() => handleExport('REACT')} className="inline-flex h-10 items-center gap-2 rounded-md bg-[#6d4aff] px-5 text-sm font-extrabold text-white hover:bg-[#5938dd] disabled:opacity-40">
                <CodeBracketIcon className="h-4 w-4" /> Xuất React (Vite)
              </button>
            </div>
          </div>
        </section>
      </div>
    </main>
  )
}

export default function App() {
  const previewMatch = window.location.pathname.match(/^\/preview\/([^/]+)$/)
  return previewMatch ? <PreviewPage snapshotId={previewMatch[1]} /> : <WorkspaceApp />
}
