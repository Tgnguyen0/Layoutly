import React, { useState, useEffect, useMemo } from 'react'
import { Frame, Download, FileText, FileType2, Trash2, AlertCircle, CheckCircle2, Loader2 } from 'lucide-react'
import InputPanel from './components/InputPanel.jsx'
import JsonTree from './components/JsonTree.jsx'
import { figmaApi, exportFile, downloadTextAsTxt, triggerDownload } from './lib/api.js'

const STORAGE_KEYS = ['layoutly_token', 'layoutly_fileKey', 'layoutly_nodeIds']

export default function App() {
  const [token, setToken] = useState(() => localStorage.getItem('layoutly_token') || '')
  const [fileKey, setFileKey] = useState(() => localStorage.getItem('layoutly_fileKey') || '')
  const [nodeIds, setNodeIds] = useState(() => localStorage.getItem('layoutly_nodeIds') || '')
  const [format, setFormat] = useState('png')

  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [statusMsg, setStatusMsg] = useState('')
  const [result, setResult] = useState(null) // { data, raw, label }

  useEffect(() => localStorage.setItem('layoutly_token', token), [token])
  useEffect(() => localStorage.setItem('layoutly_fileKey', fileKey), [fileKey])
  useEffect(() => localStorage.setItem('layoutly_nodeIds', nodeIds), [nodeIds])

  const exportName = useMemo(() => {
    if (!result) return 'layoutly-export'
    return `layoutly-${result.label}-${fileKey || 'file'}`
  }, [result, fileKey])

  async function handleCall(type) {
    setError('')
    setStatusMsg('')

    if (type !== 'me' && !fileKey.trim()) {
      setError('Nhập File Key trước đã.')
      return
    }
    if ((type === 'nodes' || type === 'images') && !nodeIds.trim()) {
      setError('Endpoint này cần Node IDs.')
      return
    }

    setLoading(true)
    setResult(null)
    try {
      let res
      switch (type) {
        case 'me': res = await figmaApi.me(token); break
        case 'file': res = await figmaApi.file(token, fileKey); break
        case 'nodes': res = await figmaApi.nodes(token, fileKey, nodeIds); break
        case 'images': res = await figmaApi.images(token, fileKey, nodeIds, format); break
        case 'components': res = await figmaApi.components(token, fileKey); break
        case 'styles': res = await figmaApi.styles(token, fileKey); break
        default: return
      }
      setResult({ data: res.json, raw: res.json ? JSON.stringify(res.json, null, 2) : res.raw, label: type })
      setStatusMsg(`Thành công · HTTP ${res.status}`)
    } catch (err) {
      setError(err.message || String(err))
    } finally {
      setLoading(false)
    }
  }

  async function handleDownload(type) {
    if (!result) {
      setError('Chưa có kết quả nào để tải. Gọi 1 API ở bên trái trước.')
      return
    }
    setError('')
    try {
      if (type === 'txt') {
        downloadTextAsTxt(result.raw, exportName)
        return
      }
      const blob = await exportFile(type, exportName, result.raw)
      triggerDownload(blob, exportName + '.' + type)
    } catch (err) {
      setError('Lỗi khi tạo file: ' + (err.message || String(err)))
    }
  }

  function clearResult() {
    setResult(null)
    setError('')
    setStatusMsg('')
  }

  return (
    <div className="min-h-screen bg-graphite-950 text-ink-100 font-body flex flex-col">
      {/* Header dang blueprint: nen luoi mo, duong scan chay ngang mot lan */}
      <header className="relative overflow-hidden border-b border-graphite-700 bg-graphite-900">
        <div className="absolute inset-0 bg-grid-pattern bg-grid opacity-60" />
        <div className="absolute top-0 left-0 right-0 h-px bg-gradient-to-r from-transparent via-blueprint to-transparent animate-scan" />
        <div className="relative px-6 py-5 flex items-center gap-3">
          <div className="w-9 h-9 rounded-md border border-blueprint/40 bg-blueprint/10 flex items-center justify-center">
            <Frame size={18} className="text-blueprint" />
          </div>
          <div>
            <h1 className="font-display font-bold text-lg tracking-tight text-ink-100">Layoutly</h1>
            <p className="text-[12px] text-ink-500 -mt-0.5">Figma Structure Explorer — Document · Canvas · Frame · Node</p>
          </div>
        </div>
      </header>

      <div className="flex-1 flex overflow-hidden">
        {/* Sidebar */}
        <aside className="w-[340px] shrink-0 border-r border-graphite-700 bg-graphite-900 p-5 overflow-y-auto">
          <InputPanel
            token={token} setToken={setToken}
            fileKey={fileKey} setFileKey={setFileKey}
            nodeIds={nodeIds} setNodeIds={setNodeIds}
            format={format} setFormat={setFormat}
            onCall={handleCall}
            loading={loading}
          />
        </aside>

        {/* Main panel */}
        <main className="flex-1 flex flex-col overflow-hidden">
          {/* Status bar */}
          <div className="flex items-center justify-between gap-4 px-5 py-3 border-b border-graphite-700 bg-graphite-900/60">
            <div className="flex items-center gap-2 text-[12.5px] min-h-[18px]">
              {loading && (
                <span className="flex items-center gap-1.5 text-ink-500">
                  <Loader2 size={14} className="animate-spin" /> Đang gọi Figma API...
                </span>
              )}
              {!loading && error && (
                <span className="flex items-center gap-1.5 text-red-400">
                  <AlertCircle size={14} /> {error}
                </span>
              )}
              {!loading && !error && statusMsg && (
                <span className="flex items-center gap-1.5 text-emerald-400">
                  <CheckCircle2 size={14} /> {statusMsg}
                </span>
              )}
              {!loading && !error && !statusMsg && (
                <span className="text-ink-500">Chưa có kết quả nào — chọn 1 hành động ở bên trái.</span>
              )}
            </div>

            <div className="flex items-center gap-2 shrink-0">
              <button
                onClick={() => handleDownload('txt')}
                disabled={!result}
                className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-md text-[12px] font-medium bg-graphite-800 border border-graphite-600 text-ink-300 hover:text-blueprint hover:border-blueprint/50 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
              >
                <Download size={13} /> .txt
              </button>
              <button
                onClick={() => handleDownload('docx')}
                disabled={!result}
                className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-md text-[12px] font-medium bg-graphite-800 border border-graphite-600 text-ink-300 hover:text-blueprint hover:border-blueprint/50 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
              >
                <FileText size={13} /> .docx
              </button>
              <button
                onClick={() => handleDownload('pdf')}
                disabled={!result}
                className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-md text-[12px] font-medium bg-graphite-800 border border-graphite-600 text-ink-300 hover:text-blueprint hover:border-blueprint/50 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
              >
                <FileType2 size={13} /> .pdf
              </button>
              <button
                onClick={clearResult}
                disabled={!result}
                className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-md text-[12px] font-medium text-ink-500 hover:text-red-400 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
              >
                <Trash2 size={13} />
              </button>
            </div>
          </div>

          {/* Ket qua */}
          <div className="flex-1 overflow-auto relative">
            <div className="absolute inset-0 bg-grid-pattern bg-grid opacity-[0.15] pointer-events-none" />
            <div className="relative p-5">
              {result ? (
                result.data ? (
                  <JsonTree data={result.data} defaultExpandedDepth={2} />
                ) : (
                  <pre className="font-mono text-[12.5px] text-ink-300 whitespace-pre-wrap break-words">
                    {result.raw}
                  </pre>
                )
              ) : (
                <div className="h-full flex flex-col items-center justify-center text-center py-24 text-ink-500">
                  <Frame size={40} className="mb-3 opacity-40" />
                  <p className="text-[13px]">Cây cấu trúc Figma sẽ hiện ở đây.</p>
                  <p className="text-[12px] mt-1">Nhập token + File Key rồi bấm "Xem file" để bắt đầu.</p>
                </div>
              )}
            </div>
          </div>
        </main>
      </div>
    </div>
  )
}
