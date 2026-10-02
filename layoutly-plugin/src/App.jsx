import React, { useEffect, useRef, useState } from 'react'
import {
  ArrowDownTrayIcon,
  ArrowPathIcon,
  CheckCircleIcon,
  ClipboardDocumentIcon,
  CodeBracketIcon,
  ComputerDesktopIcon,
  DevicePhoneMobileIcon,
  EyeIcon,
  EyeSlashIcon,
  FolderIcon,
  LinkIcon,
} from '@heroicons/react/24/outline'
import { downloadSnapshotZip, fetchSnapshotPreview, importSelection, snapshotApi } from './lib/api.js'

const VIEWPORTS = [
  { key: 'desktop', label: 'Desktop', width: 1440 },
  { key: 'tablet', label: 'Tablet', width: 768 },
  { key: 'mobile', label: 'Mobile', width: 430 },
]
const TABS = [
  { key: 'PREVIEW', label: 'Preview' },
  { key: 'HTML', label: 'HTML', file: 'index.html' },
  { key: 'CSS', label: 'CSS', file: 'styles.css' },
]

function extractFileKey(input) {
  const value = input.trim()
  if (!value) return ''
  const match = value.match(/figma\.com\/(?:file|design|proto|board)\/([a-zA-Z0-9]+)/)
  return match ? match[1] : value
}

function errorMessage(error) {
  if (error?.code === 'RATE_LIMITED') {
    const minutes = Math.ceil((Number(error.retryAfterSeconds) || 60) / 60)
    return `Figma đang giới hạn API. Thử lại sau khoảng ${minutes} phút.`
  }
  if (error?.message === 'Failed to fetch') {
    return 'Không kết nối được backend (localhost:8080). Hãy bật backend rồi bấm Thử lại.'
  }
  return error?.message || String(error)
}

function notify(message) {
  parent.postMessage({ pluginMessage: { type: 'notify', message } }, '*')
}

// navigator.clipboard thường bị chặn trong iframe của plugin nên dùng execCommand.
function copyText(text) {
  const el = document.createElement('textarea')
  el.value = text
  el.style.cssText = 'position:fixed;opacity:0'
  document.body.appendChild(el)
  el.select()
  try { document.execCommand('copy') } finally { el.remove() }
}

function SyncBadge() {
  return (
    <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 px-2 py-0.5 text-[11px] font-bold text-emerald-700">
      <CheckCircleIcon className="h-3.5 w-3.5" /> Đã đồng bộ
    </span>
  )
}

function Stat({ label, value }) {
  return (
    <div className="rounded-md bg-slate-50 px-3 py-2">
      <p className="text-[11px] text-slate-500">{label}</p>
      <p className="text-sm font-extrabold text-slate-950">{value ?? 0}</p>
    </div>
  )
}

function Header({ connected }) {
  return (
    <header className="flex h-12 flex-none items-center justify-between border-b border-slate-200 bg-white px-3">
      <div className="flex items-center gap-2">
        <span className="grid h-7 w-7 grid-cols-2 gap-0.5 rounded border border-slate-200 p-1" aria-hidden="true">
          <span className="rounded-[2px] bg-[#1769ff]" />
          <span className="rounded-[2px] bg-[#22c55e]" />
          <span className="rounded-[2px] bg-[#f97316]" />
          <span className="rounded-[2px] bg-[#6d4aff]" />
        </span>
        <strong className="text-[15px] font-extrabold">Layoutly</strong>
        <span className="text-[11px] font-semibold text-slate-500">Figma to code</span>
      </div>
      <span className="flex items-center gap-1.5 text-xs font-semibold text-slate-600">
        <span className={`h-2 w-2 rounded-full ${connected ? 'bg-emerald-500' : 'bg-slate-300'}`} />
        {connected ? 'Đã kết nối' : 'Chưa kết nối'}
      </span>
    </header>
  )
}

function PreviewPane({ snapshot, viewport, onViewportChange, html }) {
  const width = viewport.width <= 768 ? `${viewport.width}px` : '100%'
  return (
    <>
      <div className="flex flex-none items-center gap-2 border-b border-slate-200 px-3 py-2">
        {VIEWPORTS.map((v) => (
          <button
            key={v.key}
            type="button"
            onClick={() => onViewportChange(v)}
            className={`inline-flex h-7 items-center gap-1.5 rounded-md border px-2.5 text-xs font-bold ${
              viewport.key === v.key ? 'border-blue-200 bg-blue-50 text-[#1769ff]' : 'border-slate-200 text-slate-600 hover:border-slate-300'
            }`}
          >
            {v.width <= 430 ? <DevicePhoneMobileIcon className="h-3.5 w-3.5" /> : <ComputerDesktopIcon className="h-3.5 w-3.5" />}
            {v.label}
          </button>
        ))}
      </div>
      <div className="flex min-h-0 flex-1 justify-center overflow-auto bg-[#f5f7fb] p-3">
        {snapshot && html ? (
          <iframe title="Preview" srcDoc={html} className="h-full min-h-[300px] max-w-full border border-slate-200 bg-white" style={{ width }} />
        ) : (
          <div className="grid w-full place-items-center text-center text-slate-500">
            <div>
              <ComputerDesktopIcon className="mx-auto h-9 w-9 stroke-[1.3] text-slate-400" />
              <p className="mt-2 text-sm font-semibold">
                {snapshot ? 'Đang tải preview...' : 'Preview sẽ xuất hiện sau khi phân tích thiết kế.'}
              </p>
            </div>
          </div>
        )}
      </div>
    </>
  )
}

function CodePane({ file, code, loading }) {
  return (
    <>
      <div className="flex flex-none items-center justify-between border-b border-white/10 bg-[#101720] px-3 py-2">
        <span className="text-xs font-semibold text-slate-400">{file}</span>
        <button
          type="button"
          disabled={!code}
          onClick={() => { copyText(code); notify('Đã sao chép') }}
          className="inline-flex h-7 items-center gap-1.5 rounded-md border border-slate-600 px-2.5 text-xs font-bold text-slate-100 hover:bg-white/10 disabled:opacity-40"
        >
          <ClipboardDocumentIcon className="h-4 w-4" /> Sao chép
        </button>
      </div>
      <pre className="m-0 min-h-0 flex-1 overflow-auto bg-[#101720] p-4 font-mono text-[12px] leading-5 text-sky-100">
        <code className="block min-w-max">{loading ? 'Đang tạo mã nguồn...' : code || 'Phân tích một thiết kế để xem mã nguồn.'}</code>
      </pre>
    </>
  )
}

export default function App() {
  const [selection, setSelection] = useState(null)
  const [selectionReady, setSelectionReady] = useState(false)
  const [exportScope, setExportScope] = useState('SELECTION')
  const [exportType, setExportType] = useState('AUTO')
  const [snapshot, setSnapshot] = useState(null)
  const [syncing, setSyncing] = useState(false)
  const [tab, setTab] = useState('PREVIEW')
  const [codeByTab, setCodeByTab] = useState({})
  const [codeLoading, setCodeLoading] = useState(false)
  const [previewHtml, setPreviewHtml] = useState('')
  const [viewport, setViewport] = useState(VIEWPORTS[0])
  const [busy, setBusy] = useState('')
  const [error, setError] = useState('')
  const [status, setStatus] = useState('')
  const reqRef = useRef(0)
  const [retry, setRetry] = useState(0)

  const structure = snapshot?.structure || {}
  // Chưa chọn gì thì xem trước cả page
  const effectiveScope = exportScope === 'SELECTION' && !selection ? 'PAGE' : exportScope
  const syncKey = `${effectiveScope}:${effectiveScope === 'SELECTION' ? selection?.id : ''}`

  useEffect(() => {
    parent.postMessage({ pluginMessage: { type: 'ready' } }, '*')
  }, [])

  // Tự đồng bộ khi mở plugin / đổi selection / đổi phạm vi
  useEffect(() => {
    if (!selectionReady) return
    const timer = setTimeout(() => {
      const requestId = ++reqRef.current
      setSyncing(true)
      setError('')
      parent.postMessage({ pluginMessage: { type: 'export', scope: effectiveScope, requestId } }, '*')
    }, 500)
    return () => clearTimeout(timer)
  }, [selectionReady, syncKey, retry])

  useEffect(() => {
    async function onMessage(e) {
      const m = e.data?.pluginMessage
      if (!m) return
      if (m.type === 'selection') {
        setSelection(m.empty ? null : { id: m.id, name: m.name })
        setSelectionReady(true)
        return
      }
      if (m.requestId !== reqRef.current) return // kết quả cũ
      if (m.type === 'export-error') {
        setError(m.message)
        setSyncing(false)
      }
      if (m.type === 'export-data') {
        try {
          const imported = await importSelection(m)
          if (m.requestId !== reqRef.current) return
          const structure = await snapshotApi.structure(imported.snapshotId)
          setSnapshot({ ...imported, structure })
          setCodeByTab({})
          setPreviewHtml('')
        } catch (err) {
          setError(errorMessage(err))
        } finally {
          if (m.requestId === reqRef.current) setSyncing(false)
        }
      }
    }
    window.addEventListener('message', onMessage)
    return () => window.removeEventListener('message', onMessage)
  }, [])

  useEffect(() => {
    if (!snapshot || tab !== 'PREVIEW') return
    let cancelled = false
    fetchSnapshotPreview(snapshot.snapshotId)
      .then((html) => { if (!cancelled) setPreviewHtml(html) })
      .catch((e) => { if (!cancelled) setError(errorMessage(e)) })
    return () => { cancelled = true }
  }, [snapshot, tab])

  useEffect(() => {
    if (!snapshot || tab === 'PREVIEW' || codeByTab[tab]) return
    setCodeLoading(true)
    snapshotApi[tab.toLowerCase()](snapshot.snapshotId)
      .then((code) => setCodeByTab((current) => ({ ...current, [tab]: code })))
      .catch((e) => setError(errorMessage(e)))
      .finally(() => setCodeLoading(false))
  }, [snapshot, tab, codeByTab])

  async function handleExport() {
    if (!snapshot) return
    setBusy('export')
    setError('')
    try {
      await downloadSnapshotZip(snapshot.snapshotId, exportType)
      setStatus('Đã xuất file zip HTML/CSS.')
      notify('Xuất zip thành công')
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy('')
    }
  }

  const activeTab = TABS.find((t) => t.key === tab)

  return (
    <main className="flex h-full flex-col bg-[#f4f7fb] text-slate-950">
      <Header connected={Boolean(snapshot)} />

      <div className="flex min-h-0 flex-1 flex-col gap-3 overflow-auto p-3">
        {(error || status) && (
          <p
            role={error ? 'alert' : 'status'}
            className={`flex flex-none items-center justify-between gap-2 rounded-md border px-3 py-2 text-xs font-semibold ${error ? 'border-red-200 bg-red-50 text-red-700' : 'border-emerald-200 bg-emerald-50 text-emerald-700'}`}
          >
            <span>{error || status}</span>
            {error && (
              <button type="button" onClick={() => setRetry((n) => n + 1)} className="flex-none rounded border border-red-300 px-2 py-0.5 hover:bg-red-100">
                Thử lại
              </button>
            )}
          </p>
        )}

        {snapshot && (
          <section className="flex-none rounded-lg border border-slate-200 bg-white p-3 shadow-sm">
            <div className="flex items-center gap-3">
              <span className="grid h-10 w-10 flex-none place-items-center rounded-md bg-gradient-to-br from-blue-50 to-indigo-100 text-[#1769ff]">
                <FolderIcon className="h-5 w-5" />
              </span>
              <div className="min-w-0">
                <div className="flex items-center gap-2">
                  <h2 className="truncate text-sm font-extrabold">{snapshot.fileName}</h2>
                  <SyncBadge />
                  {syncing && <ArrowPathIcon className="h-4 w-4 animate-spin text-slate-500" />}
                </div>
                <p className="truncate text-[11px] text-slate-500">Đang xem: {snapshot.nodeName || '-'}</p>
              </div>
            </div>
            <div className="mt-3 grid grid-cols-4 gap-2">
              <Stat label="Frame" value={structure.frameCount} />
              <Stat label="Node" value={structure.nodeCount} />
              <Stat label="Auto Layout" value={structure.autoLayoutCount} />
              <Stat label="Hình ảnh" value={structure.imageCount} />
            </div>
          </section>
        )}

        <section className="flex min-h-[340px] flex-1 flex-col overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm">
          <div className="flex h-10 flex-none items-end border-b border-slate-200 px-2">
            {TABS.map((t) => (
              <button
                key={t.key}
                type="button"
                disabled={!snapshot}
                onClick={() => setTab(t.key)}
                className={`h-10 border-b-2 px-3 text-sm font-bold disabled:opacity-40 ${
                  tab === t.key ? 'border-[#1769ff] text-[#1769ff]' : 'border-transparent text-slate-500 hover:text-slate-900'
                }`}
              >
                {t.label}
              </button>
            ))}
          </div>
          {tab === 'PREVIEW' ? (
            <PreviewPane snapshot={snapshot} viewport={viewport} onViewportChange={setViewport} html={previewHtml} />
          ) : (
            <CodePane file={activeTab.file} code={codeByTab[tab]} loading={codeLoading} />
          )}
        </section>
      </div>

      <footer className="flex flex-none items-center justify-end gap-2 border-t border-slate-200 bg-white px-3 py-2.5">
        <select
          value={exportScope}
          onChange={(e) => setExportScope(e.target.value)}
          className="h-9 rounded-md border border-slate-300 bg-white px-2 text-xs font-bold text-slate-700 outline-none focus:border-[#1769ff]"
        >
          <option value="SELECTION">Frame đang chọn</option>
          <option value="PAGE">Tất cả frame trong page</option>
        </select>
        <select
          value={exportType}
          onChange={(e) => setExportType(e.target.value)}
          className="h-9 rounded-md border border-slate-300 bg-white px-2 text-xs font-bold text-slate-700 outline-none focus:border-[#1769ff]"
        >
          <option value="AUTO">Tách tự động</option>
          <option value="CANVAS">Tách theo Page</option>
          <option value="FRAME">Tách theo Frame</option>
        </select>
        <button
          type="button"
          disabled={!snapshot || syncing || busy === 'export'}
          onClick={handleExport}
          className="inline-flex h-9 items-center gap-1.5 rounded-md bg-[#1769ff] px-3 text-xs font-extrabold text-white hover:bg-[#0f55d8] disabled:opacity-40"
        >
          {busy === 'export' ? <ArrowPathIcon className="h-4 w-4 animate-spin" /> : <ArrowDownTrayIcon className="h-4 w-4" />}
          Xuất{snapshot ? ` "${snapshot.nodeName}"` : ''} (.zip)
        </button>
      </footer>
    </main>
  )
}
