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
  ClipboardDocumentIcon,
  CodeBracketIcon,
  ComputerDesktopIcon,
  DevicePhoneMobileIcon,
  EyeIcon,
  EyeSlashIcon,
  FolderIcon,
  LinkIcon,
  QuestionMarkCircleIcon,
  SparklesIcon,
  Squares2X2Icon,
} from '@heroicons/react/24/outline'
import { downloadSnapshotExport, snapshotApi } from './lib/api.js'
import WorkspaceEditor from './pages/WorkspaceEditor.jsx'
import { readRoute } from './lib/routes.js'
import PublicSite from './pages/PublicSite.jsx'
import RequireAccount from './components/RequireAccount.jsx'

const VIEWPORTS = [
  { key: 'desktop', label: 'Desktop', width: 1440 },
  { key: 'laptop', label: 'Laptop', width: 1280 },
  { key: 'tablet', label: 'Tablet', width: 768 },
  { key: 'mobile', label: 'Mobile', width: 430 },
  { key: 'mobile-small', label: 'Mobile S', width: 390 },
]

const CODE_TABS = [
  { key: 'HTML', label: 'HTML', file: 'index.html' },
  { key: 'CSS', label: 'CSS', file: 'styles.css' },
  { key: 'REACT', label: 'React', file: 'Page.jsx' },
  { key: 'TREE', label: 'JSON', file: 'figma-tree.json' },
]

function normalizeNodeId(nodeId) {
  return nodeId ? nodeId.replace(/-/g, ':') : ''
}

function parseFigmaTarget(input) {
  const value = input.trim()
  if (!value) return { fileKey: '', nodeId: '' }
  try {
    const url = new URL(value)
    const fileKey = url.pathname.match(/\/(?:file|design|proto|board)\/([^/]+)/)?.[1] || ''
    const nodeId = normalizeNodeId(url.searchParams.get('node-id') || '')
    return { fileKey: fileKey || value, nodeId }
  } catch {
    const fileKey = value.match(/figma\.com\/(?:file|design|proto|board)\/([^/?#]+)/)?.[1] || value
    const nodeId = normalizeNodeId(value.match(/[?&]node-id=([^&#]+)/)?.[1] || '')
    return { fileKey, nodeId }
  }
}

function formatCode(tab, code) {
  if (tab !== 'TREE' || !code) return code
  try {
    return JSON.stringify(JSON.parse(code), null, 2)
  } catch {
    return code
  }
}

function formatDate(value) {
  if (!value) return 'No data'
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(date)
}

function errorMessage(error) {
  if (error?.code === 'RATE_LIMITED') {
    const seconds = Number(error.retryAfterSeconds) || 60
    const duration = seconds >= 86400
      ? `${Math.ceil(seconds / 86400)} ngay`
      : seconds >= 3600
        ? `${Math.ceil(seconds / 3600)} gio`
        : `${Math.ceil(seconds / 60)} phut`
    const retryAt = error.retryAt
      ? new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(error.retryAt))
      : null
    const plan = error.planTier ? ` Goi hien tai: ${error.planTier}.` : ''
    return `Figma dang gioi han API. Thu lai sau khoang ${duration}${retryAt ? ` (${retryAt})` : ''}.${plan}`
  }
  if (error?.message === 'Failed to fetch') {
    return 'Khong the nhan phan hoi tu backend. Hay kiem tra backend roi thu lai.'
  }
  return error?.message || String(error)
}

function BrandMark() {
  return (
    <a href="/" className="flex items-center gap-3" aria-label="Layoutly home">
      <span className="relative grid h-11 w-11 place-items-center rounded-2xl bg-[#00a99d] shadow-[0_12px_30px_rgba(0,169,157,0.28)]">
        <span className="absolute -right-1 -top-1 h-4 w-4 rounded-full bg-[#ffb31a]" />
        <CodeBracketIcon className="h-6 w-6 stroke-[2.4] text-white" />
      </span>
      <span className="leading-none">
        <strong className="block text-[22px] font-black text-[#001817]">Layoutly</strong>
        <span className="mt-1 block text-[10px] font-extrabold uppercase tracking-[0.22em] text-[#00a99d]">Figma to code</span>
      </span>
    </a>
  )
}

function Header({ connected }) {
  return (
    <header className="sticky top-0 z-40 border-b border-[#dce9df] bg-white/88 backdrop-blur-xl">
      <nav className="mx-auto flex h-[78px] max-w-[1480px] items-center justify-between px-5" aria-label="Main">
        <BrandMark />
        <div className="hidden items-center gap-10 text-[15px] font-bold text-[#818181] md:flex">
          <a className="text-[#001817]" href="#converter">Home</a>
          <a className="transition hover:text-[#00a99d]" href="#workspace">Work</a>
          <a className="transition hover:text-[#00a99d]" href="#ai-review">Grow</a>
          <a className="transition hover:text-[#00a99d]" href="#exports">Stay</a>
        </div>
        <div className="flex items-center gap-3">
          <span className="hidden items-center gap-2 rounded-full border border-[#dce9df] bg-[#f7fff8] px-3 py-2 text-xs font-extrabold text-[#001817] sm:flex">
            <span className={`h-2.5 w-2.5 rounded-full ${connected ? 'bg-[#00a99d]' : 'bg-[#ffb31a]'}`} />
            {connected ? 'Connected' : 'Local'}
          </span>
          <a
            href="#figma-connection"
            className="inline-flex h-12 items-center justify-center rounded-full bg-[#ffb31a] px-6 text-sm font-black text-[#001817] shadow-[0_14px_30px_rgba(255,179,26,0.28)] transition hover:bg-[#ffc64d]"
          >
            Open in Figma
          </a>
        </div>
      </nav>
    </header>
  )
}

function SyncBadge({ children = 'Synced' }) {
  return (
    <span className="inline-flex items-center gap-1.5 rounded-full bg-[#e3fff6] px-2.5 py-1 text-[11px] font-black uppercase tracking-wide text-[#008f83]">
      <CheckCircleIcon className="h-4 w-4" />
      {children}
    </span>
  )
}

function StatCard({ label, value, hint }) {
  return (
    <div className="min-w-0 border-r border-[#dce9df] px-4 py-3 last:border-r-0">
      <strong className="block text-2xl font-black text-[#001817]">{value ?? 0}</strong>
      <span className="mt-1 block text-[11px] font-bold uppercase tracking-wide text-[#5d6b67]">{label}</span>
      {hint && <span className="mt-1 block text-[11px] text-[#83918d]">{hint}</span>}
    </div>
  )
}

function MiniStat({ label, value, icon: Icon }) {
  return (
    <div className="rounded-2xl border border-[#dce9df] bg-white/70 p-3">
      <div className="flex items-center justify-between gap-3">
        <span className="text-xs font-extrabold uppercase tracking-wide text-[#66726f]">{label}</span>
        <Icon className="h-4 w-4 text-[#00a99d]" />
      </div>
      <strong className="mt-2 block text-xl font-black text-[#001817]">{value ?? 0}</strong>
    </div>
  )
}

function HeroArt() {
  return (
    <div className="relative hidden min-h-[300px] lg:block" aria-hidden="true">
      <div className="absolute right-4 top-2 h-64 w-64 rounded-full bg-[#fff0b8]" />
      <div className="absolute right-10 top-12 h-44 w-44 rounded-full border-[28px] border-[#ffd44f]" />
      <div className="absolute right-24 top-20 h-28 w-28 rounded-full bg-[#ccfff0]" />
      <div className="absolute right-0 top-24 h-52 w-44 rounded-[42px] border-2 border-[#001817] bg-white/82 p-5 shadow-[0_30px_80px_rgba(0,24,23,0.12)]">
        <div className="h-8 w-20 rounded-full bg-[#00a99d]" />
        <div className="mt-5 h-3 w-28 rounded-full bg-[#dce9df]" />
        <div className="mt-3 h-3 w-20 rounded-full bg-[#dce9df]" />
        <div className="mt-7 grid grid-cols-3 gap-2">
          <span className="h-10 rounded-xl bg-[#ffb31a]" />
          <span className="h-10 rounded-xl bg-[#85eadb]" />
          <span className="h-10 rounded-xl bg-[#001817]" />
        </div>
      </div>
      <div className="absolute left-6 top-20 grid h-24 w-24 place-items-center rounded-full bg-white/70 shadow-[0_22px_70px_rgba(0,169,157,0.18)]">
        <SparklesIcon className="h-11 w-11 text-[#00a99d]" />
      </div>
    </div>
  )
}

function ViewportButtons({ selected, onChange, compact = false }) {
  return (
    <div className="flex flex-wrap items-center gap-2" aria-label="Preview size">
      {VIEWPORTS.map((viewport) => (
        <button
          key={viewport.key}
          type="button"
          onClick={() => onChange(viewport)}
          className={`inline-flex h-9 items-center gap-1.5 rounded-full border px-3 text-xs font-black transition ${
            selected.key === viewport.key
              ? 'border-[#00a99d] bg-[#00a99d] text-white shadow-[0_10px_22px_rgba(0,169,157,0.24)]'
              : 'border-[#dce9df] bg-white/82 text-[#4d5b57] hover:border-[#00a99d]'
          }`}
          title={`${viewport.label} ${viewport.width}px`}
        >
          {viewport.width <= 430
            ? <DevicePhoneMobileIcon className="h-4 w-4" />
            : <ComputerDesktopIcon className="h-4 w-4" />}
          {viewport.label}{compact ? '' : ` ${viewport.width}`}
        </button>
      ))}
    </div>
  )
}

function CodeViewer({ snapshot, activeTab, onTabChange, code, loading }) {
  const active = CODE_TABS.find((tab) => tab.key === activeTab) || CODE_TABS[0]
  const displayCode = formatCode(activeTab, code)
  return (
    <section className="flex h-[640px] max-h-[calc(100vh-118px)] min-h-0 min-w-0 flex-col overflow-hidden rounded-[26px] border border-[#17211f] bg-[#07110f] shadow-[0_30px_80px_rgba(0,24,23,0.18)]" aria-label="Code output">
      <div className="border-b border-white/10 p-3">
        <div className="grid grid-cols-4 gap-1 rounded-2xl bg-white/8 p-1">
          {CODE_TABS.map((tab) => (
            <button
              key={tab.key}
              type="button"
              disabled={!snapshot}
              onClick={() => onTabChange(tab.key)}
              className={`h-10 rounded-xl text-sm font-black transition disabled:opacity-40 ${
                activeTab === tab.key
                  ? 'bg-[#00d084] text-[#001817]'
                  : 'text-[#c9d8d3] hover:bg-white/10'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>
      </div>
      <div className="flex items-center justify-between gap-3 border-b border-white/10 px-4 py-3">
        <span className="truncate rounded-full bg-white/8 px-3 py-1 text-xs font-bold text-[#9fb2ad]">{active.file}</span>
        <button
          type="button"
          disabled={!displayCode}
          onClick={() => navigator.clipboard?.writeText(displayCode)}
          className="inline-flex h-9 items-center gap-2 rounded-full border border-white/15 px-3 text-xs font-black text-white transition hover:bg-white/10 disabled:opacity-40"
        >
          <ClipboardDocumentIcon className="h-4 w-4" />
          Copy
        </button>
      </div>
      <pre className="m-0 min-h-0 min-w-0 flex-1 overflow-auto p-5 font-mono text-[12px] leading-6 text-[#b7ffe6]">
        <code className="block min-w-max">{loading ? 'Generating code...' : displayCode || 'Import a design to see generated code.'}</code>
      </pre>
    </section>
  )
}

function PreviewPanel({ snapshot, viewport, onViewportChange, previewVersion }) {
  const previewWidth = viewport.width <= 768 ? `${viewport.width}px` : '100%'
  return (
    <section className="flex min-h-[520px] min-w-0 flex-col overflow-hidden rounded-[26px] border border-[#dce9df] bg-white/86 shadow-[0_24px_70px_rgba(0,24,23,0.10)]" aria-label="Design preview">
      <div className="flex min-h-16 flex-wrap items-center justify-between gap-3 border-b border-[#dce9df] px-4 py-3">
        <div>
          <p className="text-xs font-black uppercase tracking-[0.18em] text-[#00a99d]">Interactive preview</p>
          <h2 className="mt-1 text-lg font-black text-[#001817]">Rendered output</h2>
        </div>
        {snapshot && (
          <button
            type="button"
            onClick={() => window.open(`/preview/${snapshot.snapshotId}`, '_blank', 'noopener,noreferrer')}
            className="inline-flex h-10 items-center gap-2 rounded-full border border-[#00a99d]/30 bg-[#e6fff6] px-4 text-xs font-black text-[#008f83] transition hover:bg-[#d4ffee]"
          >
            <ArrowTopRightOnSquareIcon className="h-4 w-4" />
            Open tab
          </button>
        )}
      </div>
      <div className="border-b border-[#dce9df] bg-[#f7fff8] px-4 py-3">
        <ViewportButtons selected={viewport} onChange={onViewportChange} compact />
      </div>
      <div className="relative flex min-h-0 flex-1 justify-center overflow-auto bg-[radial-gradient(circle_at_18%_12%,rgba(0,169,157,0.14),transparent_24%),linear-gradient(135deg,#fbfff9,#eefaf3)] p-4">
        {snapshot ? (
          <div className="h-full min-h-[430px] max-w-full overflow-hidden rounded-[18px] border border-[#cadbd4] bg-white shadow-[0_20px_60px_rgba(0,24,23,0.14)]" style={{ width: previewWidth }}>
            <iframe
              key={`${previewVersion}-${viewport.key}`}
              title="Snapshot preview"
              src={`/api/snapshots/${snapshot.snapshotId}/preview?targetWidth=${viewport.width}&v=${previewVersion}`}
              className="h-full w-full border-0 bg-white"
            />
          </div>
        ) : (
          <div className="grid min-h-[430px] w-full place-items-center text-center">
            <div className="max-w-[340px]">
              <div className="mx-auto grid h-20 w-20 place-items-center rounded-full bg-[#fff0b8]">
                <ComputerDesktopIcon className="h-10 w-10 text-[#00a99d]" />
              </div>
              <p className="mt-5 text-lg font-black text-[#001817]">Preview is waiting</p>
              <p className="mt-2 text-sm font-medium leading-6 text-[#66726f]">Sync from the Figma plugin or import by URL to see the rendered page here.</p>
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
    <main className="flex h-screen min-h-0 flex-col bg-[#f7fff8] text-[#001817]">
      <Header connected={Boolean(snapshot)} />
      <section className="flex min-h-16 flex-wrap items-center justify-between gap-3 border-b border-[#dce9df] bg-white/80 px-5 py-3">
        <div className="flex items-center gap-3">
          <a href="/#workspace" className="grid h-10 w-10 place-items-center rounded-full border border-[#dce9df] bg-white" title="Back to workspace">
            <ArrowLeftIcon className="h-4 w-4" />
          </a>
          <div>
            <h1 className="text-sm font-black">{snapshot?.fileName || 'Snapshot Preview'}</h1>
            <p className="text-xs font-bold text-[#66726f]">{viewport.label} - {viewport.width}px</p>
          </div>
        </div>
        <ViewportButtons selected={viewport} onChange={setViewport} compact />
      </section>
      <section className="flex min-h-0 flex-1 justify-center overflow-auto bg-[linear-gradient(135deg,#effff5,#fffdf7)] p-5">
        {error ? (
          <p className="m-auto rounded-2xl bg-red-50 px-4 py-3 text-sm font-bold text-red-700">{error}</p>
        ) : (
          <iframe
            title="Layoutly full preview"
            src={`/api/snapshots/${snapshotId}/preview?targetWidth=${viewport.width}`}
            className="h-full min-h-[620px] max-w-full rounded-[22px] border border-[#cadbd4] bg-white shadow-[0_28px_90px_rgba(0,24,23,0.16)]"
            style={{ width: `${viewport.width}px` }}
          />
        )}
      </section>
    </main>
  )
}

function ImportPanel({
  importMode,
  setImportMode,
  figmaInput,
  setFigmaInput,
  token,
  setToken,
  showToken,
  setShowToken,
  busy,
  bridgeReady,
  showBridgeGuide,
  setShowBridgeGuide,
  handleImport,
  setError,
  setConnected,
  setStatus,
  detectedFileKey,
  detectedNodeId,
}) {
  return (
    <form id="converter" onSubmit={handleImport} className="rounded-[28px] border border-[#dce9df] bg-white/82 p-4 shadow-[0_24px_70px_rgba(0,24,23,0.08)] backdrop-blur">
      <div className="grid grid-cols-2 gap-1 rounded-2xl bg-[#eef7f0] p-1">
        <button
          type="button"
          onClick={() => setImportMode('BRIDGE')}
          className={`h-11 rounded-xl text-sm font-black transition ${importMode === 'BRIDGE' ? 'bg-[#00a99d] text-white shadow-lg' : 'text-[#66726f]'}`}
        >
          Bridge
        </button>
        <button
          type="button"
          onClick={() => setImportMode('REST')}
          className={`h-11 rounded-xl text-sm font-black transition ${importMode === 'REST' ? 'bg-[#00a99d] text-white shadow-lg' : 'text-[#66726f]'}`}
        >
          URL
        </button>
      </div>

      {importMode === 'BRIDGE' ? (
        <div className="mt-4 rounded-3xl border border-[#c9eee5] bg-[#effff8] p-4">
          <div className="flex items-start justify-between gap-4">
            <div>
              <p className="text-sm font-black text-[#001817]">Layoutly Bridge</p>
              <p className="mt-1 text-xs font-semibold leading-5 text-[#66726f]">Open the Figma plugin, choose a frame, then sync it to this local workspace.</p>
            </div>
            <span className={`mt-1 h-3 w-3 flex-none rounded-full ${bridgeReady ? 'bg-[#00a99d]' : 'bg-[#ffb31a]'}`} />
          </div>
          <button
            type="button"
            onClick={() => setShowBridgeGuide((value) => !value)}
            className="mt-4 inline-flex h-10 items-center gap-2 rounded-full border border-[#00a99d]/30 bg-white px-4 text-xs font-black text-[#008f83]"
          >
            <QuestionMarkCircleIcon className="h-4 w-4" />
            Guide
          </button>
          {showBridgeGuide && (
            <p className="mt-3 border-t border-[#c9eee5] pt-3 text-xs font-semibold leading-5 text-[#66726f]">
              Figma Desktop - Plugins - Development - Layoutly Bridge. Select a frame and press Sync to Layoutly.
            </p>
          )}
        </div>
      ) : (
        <div className="mt-4 space-y-3">
          <label className="flex h-12 items-center gap-3 rounded-2xl border border-[#dce9df] bg-white px-4 focus-within:border-[#00a99d]">
            <LinkIcon className="h-5 w-5 text-[#00a99d]" />
            <input
              value={figmaInput}
              onChange={(event) => setFigmaInput(event.target.value)}
              placeholder="Paste Figma URL or file key"
              className="min-w-0 flex-1 bg-transparent text-sm font-bold text-[#001817] outline-none placeholder:text-[#95a09d]"
            />
          </label>
          {(detectedFileKey || detectedNodeId) && (
            <div className="rounded-2xl border border-[#c9eee5] bg-[#effff8] px-4 py-3 text-xs font-bold leading-5 text-[#4d5b57]">
              <span className="text-[#008f83]">Detected</span>
              {detectedFileKey && <span> / file key: {detectedFileKey}</span>}
              {detectedNodeId && <span> / node: {detectedNodeId}</span>}
            </div>
          )}
          <label className="flex h-12 items-center gap-3 rounded-2xl border border-[#dce9df] bg-white px-4 focus-within:border-[#00a99d]">
            <input
              type={showToken ? 'text' : 'password'}
              value={token}
              onChange={(event) => setToken(event.target.value)}
              placeholder="Figma personal access token"
              autoComplete="off"
              className="min-w-0 flex-1 bg-transparent text-sm font-bold text-[#001817] outline-none placeholder:text-[#95a09d]"
            />
            <button type="button" onClick={() => setShowToken((value) => !value)} className="grid h-9 w-9 place-items-center rounded-full text-[#66726f]">
              {showToken ? <EyeSlashIcon className="h-5 w-5" /> : <EyeIcon className="h-5 w-5" />}
            </button>
          </label>
          <button
            type="button"
            onClick={() => {
              if (!token.trim()) setError('Nhap Figma token truoc khi ket noi.')
              else {
                setConnected(true)
                setError('')
                setStatus('Token da san sang trong phien hien tai.')
              }
            }}
            className="h-10 rounded-full border border-[#00a99d]/30 bg-[#effff8] px-4 text-xs font-black text-[#008f83]"
          >
            Test token
          </button>
        </div>
      )}

      <button
        type="submit"
        disabled={busy === 'import'}
        className="mt-4 inline-flex h-12 w-full items-center justify-center gap-3 rounded-full bg-[#ffb31a] px-5 text-sm font-black text-[#001817] shadow-[0_16px_36px_rgba(255,179,26,0.28)] transition hover:bg-[#ffc64d] disabled:cursor-wait disabled:opacity-60"
      >
        {busy === 'import' ? <ArrowPathIcon className="h-5 w-5 animate-spin" /> : <BoltIcon className="h-5 w-5" />}
        {importMode === 'BRIDGE' ? 'How to sync' : busy === 'import' ? 'Analyzing' : 'Analyze design'}
        <ArrowRightIcon className="h-5 w-5" />
      </button>
    </form>
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

  const figmaTarget = useMemo(() => parseFigmaTarget(figmaInput), [figmaInput])
  const fileKey = figmaTarget.fileKey
  const nodeId = figmaTarget.nodeId
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
        if (linkedSnapshotId) window.history.replaceState({}, '', '/#workspace')
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
      setError('Hay dan URL Figma hoac nhap File Key.')
      return
    }

    setBusy('import')
    try {
      const response = await snapshotApi.importFigma(token, { fileKey, figmaUrl: figmaInput, nodeId })
      setSnapshot(response.json)
      setConnected(true)
      setCodeByTab({})
      setActiveTab('HTML')
      sessionStorage.setItem('layoutly_snapshot_id', response.json.snapshotId)
      await loadCode(response.json.snapshotId, 'HTML')
      setStatus('Snapshot da san sang. Cac thao tac sau khong goi lai file Figma.')
      setPreviewVersion((value) => value + 1)
    } catch (caught) {
      setError(errorMessage(caught))
    } finally {
      setBusy('')
    }
  }

  async function handleRefresh() {
    if (!snapshot) return
    if (!window.confirm('Refresh will replace the current snapshot and generated code. Continue?')) return
    setBusy('refresh')
    setError('')
    try {
      const response = await snapshotApi.refresh(token, snapshot.snapshotId)
      setSnapshot(response.json)
      setCodeByTab({})
      setActiveTab('HTML')
      await loadCode(response.json.snapshotId, 'HTML')
      setPreviewVersion((value) => value + 1)
      setStatus('Snapshot refreshed from Figma.')
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
      setStatus(`Exported ${format === 'REACT' ? 'React/Vite' : 'HTML/CSS'} from snapshot.`)
    } catch (caught) {
      setError(errorMessage(caught))
    } finally {
      setBusy('')
    }
  }

  async function handleSaveFixture() {
    if (!snapshot) return
    const suggested = snapshot.fileName?.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '') || 'layout-case'
    const caseName = window.prompt('Test case name, for example landing-horizontal', suggested)
    if (!caseName) return
    setBusy('fixture')
    setError('')
    try {
      const response = await snapshotApi.saveFixture(snapshot.snapshotId, caseName)
      setStatus(`Saved test fixture at ${response.json.path}`)
    } catch (caught) {
      setError(errorMessage(caught))
    } finally {
      setBusy('')
    }
  }

  return (
    <main className="min-h-screen overflow-x-hidden bg-[linear-gradient(120deg,#dfffe7_0%,#fbfff8_38%,#fff8e7_100%)] text-[#001817]">
      <Header connected={connected || Boolean(snapshot)} />

      <section className="mx-auto grid max-w-[1480px] gap-8 px-5 pb-8 pt-10 lg:grid-cols-[minmax(0,1.12fr)_430px]" id="top">
        <div className="min-w-0">
          <p className="text-xs font-black uppercase tracking-[0.28em] text-[#00a99d]">Private bridge - Code studio - Fresh output</p>
          <h1 className="mt-5 max-w-[840px] text-[clamp(46px,7vw,92px)] font-black leading-[0.95] tracking-[-0.03em] text-[#001817]">
            Tao code tu Figma. <span className="text-[#00a99d]">Giu chat rieng.</span>
          </h1>
          <p className="mt-6 max-w-2xl text-lg font-semibold leading-8 text-[#4d5b57]">
            Layoutly giu workflow hien tai cua ban: sync tu plugin, preview local, xem code va export HTML/CSS hoac React.
          </p>
          <div className="mt-7 flex flex-wrap gap-3">
            <a href="#converter" className="inline-flex h-12 items-center gap-2 rounded-full bg-[#001817] px-5 text-sm font-black text-white">
              Import design <ArrowRightIcon className="h-5 w-5" />
            </a>
            <a href="#workspace" className="inline-flex h-12 items-center gap-2 rounded-full border border-[#001817]/10 bg-white/80 px-5 text-sm font-black text-[#001817]">
              Inspect output
            </a>
          </div>
        </div>

        <div className="grid gap-4">
          <HeroArt />
          <ImportPanel
            importMode={importMode}
            setImportMode={setImportMode}
            figmaInput={figmaInput}
            setFigmaInput={setFigmaInput}
            token={token}
            setToken={setToken}
            showToken={showToken}
            setShowToken={setShowToken}
            busy={busy}
            bridgeReady={bridgeReady}
            showBridgeGuide={showBridgeGuide}
            setShowBridgeGuide={setShowBridgeGuide}
            handleImport={handleImport}
            setError={setError}
            setConnected={setConnected}
            setStatus={setStatus}
            detectedFileKey={fileKey}
            detectedNodeId={nodeId}
          />
        </div>
      </section>

      <section className="mx-auto max-w-[1480px] px-5 pb-8">
        <div className="grid overflow-hidden rounded-[22px] border border-[#dce9df] bg-white/64 shadow-[0_18px_60px_rgba(0,24,23,0.08)] sm:grid-cols-4">
          <StatCard label="Frames" value={structure.frameCount} hint="screens detected" />
          <StatCard label="Nodes" value={structure.nodeCount} hint="parsed tree" />
          <StatCard label="Auto layout" value={structure.autoLayoutCount} hint="flex/grid inputs" />
          <StatCard label="Assets" value={(structure.imageCount ?? 0) + (structure.vectorCount ?? 0)} hint="image/vector" />
        </div>
      </section>

      {(error || status) && (
        <section className="mx-auto max-w-[1480px] px-5 pb-4">
          <div className={`rounded-2xl border px-4 py-3 text-sm font-black ${error ? 'border-red-200 bg-red-50 text-red-700' : 'border-[#b9eadc] bg-[#effff8] text-[#008f83]'}`} role={error ? 'alert' : 'status'}>
            {error || status}
          </div>
        </section>
      )}

      {snapshot && (
        <section className="mx-auto max-w-[1480px] px-5 pb-5">
          <div className="flex flex-wrap items-center justify-between gap-4 rounded-[26px] border border-[#dce9df] bg-white/82 px-5 py-4 shadow-[0_18px_60px_rgba(0,24,23,0.08)]">
            <div className="flex min-w-0 items-center gap-4">
              <span className="grid h-14 w-14 flex-none place-items-center rounded-2xl bg-[#fff0b8] text-[#00a99d]">
                <FolderIcon className="h-7 w-7" />
              </span>
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <h2 className="truncate text-base font-black">{snapshot.fileName}</h2>
                  <SyncBadge>{snapshot.source === 'FIGMA_PLUGIN' ? 'Bridge' : 'REST'}</SyncBadge>
                </div>
                <p className="mt-1 truncate text-xs font-bold text-[#66726f]">
                  {snapshot.rootNodeName || '-'} / {snapshot.fileKey} / {formatDate(snapshot.fetchedAt)}
                </p>
              </div>
            </div>
            {snapshot.source === 'FIGMA_REST' ? (
              <button
                type="button"
                onClick={handleRefresh}
                disabled={busy === 'refresh'}
                className="inline-flex h-11 items-center gap-2 rounded-full border border-[#00a99d]/30 bg-[#effff8] px-4 text-sm font-black text-[#008f83] disabled:opacity-50"
              >
                <ArrowPathIcon className={`h-4 w-4 ${busy === 'refresh' ? 'animate-spin' : ''}`} />
                Refresh
              </button>
            ) : (
              <span className="text-xs font-black uppercase tracking-wide text-[#008f83]">Snapshot ready</span>
            )}
          </div>
        </section>
      )}

      <section id="workspace" className="mx-auto grid max-w-[1480px] gap-5 px-5 pb-10 xl:grid-cols-[260px_minmax(0,1fr)_430px]">
        <aside className="grid gap-4 self-start">
          <section className="rounded-[26px] border border-[#dce9df] bg-white/78 p-4 shadow-[0_18px_55px_rgba(0,24,23,0.08)]">
            <p className="text-xs font-black uppercase tracking-[0.18em] text-[#00a99d]">Content state</p>
            <div className="mt-4 grid gap-3">
              <MiniStat label="Canvas" value={structure.canvasCount} icon={FolderIcon} />
              <MiniStat label="Frame" value={structure.frameCount} icon={ComputerDesktopIcon} />
              <MiniStat label="Depth" value={structure.maxDepth} icon={Squares2X2Icon} />
              <MiniStat label="Cache" value={snapshot?.assets?.cachedAssetCount} icon={CheckCircleIcon} />
            </div>
          </section>

          <section id="ai-review" className="rounded-[26px] border border-[#dce9df] bg-[#001817] p-4 text-white shadow-[0_24px_70px_rgba(0,24,23,0.18)]">
            <p className="flex items-center gap-2 text-xs font-black uppercase tracking-[0.18em] text-[#85eadb]">
              <SparklesIcon className="h-4 w-4" />
              AI suggestions
            </p>
            <ul className="mt-4 space-y-3 text-sm font-semibold leading-6 text-[#d7ebe5]">
              <li>Use visual diff to compare Figma PNG and rendered HTML.</li>
              <li>Export complex masks or vectors as images when CSS is risky.</li>
              <li>Add gradient, shadow and font checks to fixtures.</li>
            </ul>
          </section>
        </aside>

        <PreviewPanel snapshot={snapshot} viewport={viewport} onViewportChange={setViewport} previewVersion={previewVersion} />

        <div className="grid min-h-0 gap-4 self-start">
          <CodeViewer
            snapshot={snapshot}
            activeTab={activeTab}
            onTabChange={handleTabChange}
            code={codeByTab[activeTab]}
            loading={codeLoading}
          />

          <section id="exports" className="rounded-[26px] border border-[#dce9df] bg-white/82 p-4 shadow-[0_18px_55px_rgba(0,24,23,0.08)]">
            <div className="flex flex-wrap items-center gap-2">
              <button type="button" disabled={!snapshot || busy === 'fixture'} onClick={handleSaveFixture} className="inline-flex h-11 items-center gap-2 rounded-full border border-[#dce9df] bg-white px-4 text-sm font-black text-[#001817] disabled:opacity-40">
                <BeakerIcon className="h-4 w-4" /> Fixture
              </button>
              <select
                value={exportType}
                onChange={(event) => setExportType(event.target.value)}
                disabled={!snapshot || busy === 'export-HTML'}
                title="HTML split mode"
                className="h-11 rounded-full border border-[#dce9df] bg-white px-4 text-sm font-black text-[#001817] outline-none disabled:opacity-40"
              >
                <option value="AUTO">Auto split</option>
                <option value="CANVAS">By page</option>
                <option value="FRAME">By frame</option>
              </select>
              <button type="button" disabled={!snapshot || busy === 'export-HTML'} onClick={() => handleExport('HTML')} className="inline-flex h-11 items-center gap-2 rounded-full border border-[#00a99d]/30 bg-[#effff8] px-4 text-sm font-black text-[#008f83] disabled:opacity-40">
                <ArrowDownTrayIcon className="h-4 w-4" /> HTML/CSS
              </button>
              <button type="button" disabled={!snapshot || busy === 'export-REACT'} onClick={() => handleExport('REACT')} className="inline-flex h-11 items-center gap-2 rounded-full bg-[#ffb31a] px-4 text-sm font-black text-[#001817] disabled:opacity-40">
                <CodeBracketIcon className="h-4 w-4" /> React
              </button>
            </div>
          </section>
        </div>
      </section>
    </main>
  )
}

export default function App() {
  const [route, setRoute] = useState(readRoute)
  useEffect(() => {
    const update = () => setRoute(readRoute())
    window.addEventListener('hashchange', update)
    window.addEventListener('popstate', update)
    return () => {
      window.removeEventListener('hashchange', update)
      window.removeEventListener('popstate', update)
    }
  }, [])
  const previewMatch = window.location.pathname.match(/^\/preview\/([^/]+)$/)
  if (previewMatch) return <RequireAccount><PreviewPage snapshotId={previewMatch[1]} /></RequireAccount>
  return route === '/workspace' ? <RequireAccount><WorkspaceEditor /></RequireAccount> : route === '/workspace-legacy' ? <RequireAccount><WorkspaceApp /></RequireAccount> : <PublicSite route={route} />
}
