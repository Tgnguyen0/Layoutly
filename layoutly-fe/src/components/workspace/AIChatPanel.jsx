import { useEffect, useState } from 'react'
import { MessageSquare, Send, X, Check, FileCode, History, Eye, ShieldAlert, Sparkles, RefreshCw, AlertCircle } from 'lucide-react'
import { snapshotApi, downloadAiExport } from '../../lib/api.js'

export default function AIChatPanel({ snapshot, selectedNode, onClose, onRevisionAccepted }) {
  const [messages, setMessages] = useState([])
  const [inputPrompt, setInputPrompt] = useState('')
  const [targetFramework, setTargetFramework] = useState('REACT')
  const [busy, setBusy] = useState(false)
  const [statusInfo, setStatusInfo] = useState(null)
  const [revisions, setRevisions] = useState([])
  const [currentDraft, setCurrentDraft] = useState(null)
  const [showDiffModal, setShowDiffModal] = useState(false)
  const [diffs, setDiffs] = useState([])
  const [showHistory, setShowHistory] = useState(false)
  const [errorText, setErrorText] = useState('')

  const [selectedProvider, setSelectedProvider] = useState('')
  const [selectedModel, setSelectedModel] = useState('')

  const snapshotId = snapshot?.snapshotId

  useEffect(() => {
    if (!snapshotId) return
    snapshotApi.aiStatus(snapshotId)
      .then(res => {
        const info = res.json
        setStatusInfo(info)
        if (info) {
          if (info.activeProvider) setSelectedProvider(info.activeProvider)
          if (info.activeModel) setSelectedModel(info.activeModel)
        }
      })
      .catch(() => setStatusInfo({ available: false, message: 'Khong the ket noi backend AI status.' }))

    fetchRevisions()
  }, [snapshotId])

  function fetchRevisions() {
    if (!snapshotId) return
    snapshotApi.aiRevisions(snapshotId)
      .then(res => setRevisions(res.json || []))
      .catch(() => {})
  }

  // Find currently selected model object to check tool support
  const currentProviderObj = statusInfo?.providers?.find(p => p.id === selectedProvider)
  const currentModelObj = currentProviderObj?.models?.find(m => m.id === selectedModel)
  const isToolCallingSupported = currentModelObj ? currentModelObj.supportsTools : true

  async function handleSendPrompt(promptText = inputPrompt) {
    if (!promptText.trim() || busy || !snapshotId) return

    const userMsg = { role: 'user', content: promptText.trim() }
    setMessages(prev => [...prev, userMsg])
    setInputPrompt('')
    setBusy(true)
    setErrorText('')

    try {
      const response = await snapshotApi.aiChat(snapshotId, {
        prompt: promptText.trim(),
        targetFramework,
        selectedNodeId: selectedNode?.id,
        selectedNodeName: selectedNode?.name,
        history: messages.slice(-6),
        provider: selectedProvider,
        model: selectedModel
      })

      const aiResponse = response.json
      const aiMsg = {
        role: 'assistant',
        content: aiResponse.message || 'Da hoan thanh yeu cau chinh sua code.',
        revisionId: aiResponse.revisionId,
        modifiedFiles: aiResponse.modifiedFiles,
        diffs: aiResponse.diffs,
        buildSuccess: aiResponse.buildSuccess,
        buildDiagnostics: aiResponse.buildDiagnostics,
        usage: aiResponse.usage
      }

      setMessages(prev => [...prev, aiMsg])
      setCurrentDraft(aiResponse)
      setDiffs(aiResponse.diffs || [])
      fetchRevisions()
    } catch (err) {
      setErrorText(err.message || 'Loi khi gui yeu cau cho AI Coding Agent.')
    } finally {
      setBusy(false)
    }
  }

  async function handleAcceptRevision(revisionId) {
    if (!snapshotId || !revisionId) return
    setBusy(true)
    try {
      await snapshotApi.aiAccept(snapshotId, revisionId, targetFramework)
      setCurrentDraft(null)
      fetchRevisions()
      if (onRevisionAccepted) onRevisionAccepted()
    } catch (err) {
      setErrorText(err.message || 'Loi khi chap nhan revision.')
    } finally {
      setBusy(false)
    }
  }

  async function handleRejectRevision(revisionId) {
    if (!snapshotId || !revisionId) return
    setBusy(true)
    try {
      await snapshotApi.aiReject(snapshotId, revisionId)
      setCurrentDraft(null)
      fetchRevisions()
    } catch (err) {
      setErrorText(err.message || 'Loi khi tu choi revision.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="we-ai" aria-label="Layoutly AI Coding Agent">
      {/* Panel Header */}
      <div className="we-ai-title">
        <MessageSquare size={18} />
        <strong>Layoutly AI Agent</strong>
        {statusInfo?.available ? (
          <span className="ly-badge ly-badge-success" style={{ marginLeft: 6, fontSize: 11 }}>
            Connected ({selectedProvider || statusInfo.activeProvider || statusInfo.provider || 'OpenAI'})
          </span>
        ) : (
          <span className="ly-badge ly-badge-warning" style={{ marginLeft: 6, fontSize: 11 }}>
            Chua ket noi
          </span>
        )}
        <div style={{ marginLeft: 'auto', display: 'flex', gap: 6 }}>
          <button
            className="we-icon"
            type="button"
            title="Lich su revisions"
            onClick={() => setShowHistory(!showHistory)}
          >
            <History size={16} />
          </button>
          <button className="we-icon" type="button" aria-label="Dong AI Chat" onClick={onClose}>
            <X size={17} />
          </button>
        </div>
      </div>

      {/* Target Framework & Model Selector Toolbar */}
      <div style={{ padding: '8px 12px', background: 'rgba(15, 23, 42, 0.7)', borderBottom: '1px solid rgba(255, 255, 255, 0.1)', display: 'flex', flexDirection: 'column', gap: 6 }}>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <span style={{ fontSize: 11, color: '#94A3B8' }}>Target:</span>
          <button
            type="button"
            className={`ly-button ${targetFramework === 'REACT' ? 'ly-button-primary' : 'ly-button-quiet'}`}
            style={{ padding: '2px 6px', fontSize: 11 }}
            onClick={() => setTargetFramework('REACT')}
          >
            React (JSX)
          </button>
          <button
            type="button"
            className={`ly-button ${targetFramework === 'HTML' ? 'ly-button-primary' : 'ly-button-quiet'}`}
            style={{ padding: '2px 6px', fontSize: 11 }}
            onClick={() => setTargetFramework('HTML')}
          >
            HTML / CSS
          </button>

          {selectedNode && (
            <span className="ly-badge" style={{ marginLeft: 'auto', fontSize: 10, background: '#1E293B', color: '#F59E0B' }}>
              Node: {selectedNode.name || selectedNode.id}
            </span>
          )}
        </div>

        {/* Model Selector Dropdown */}
        {statusInfo?.providers && (
          <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
            <span style={{ fontSize: 11, color: '#94A3B8' }}>Model:</span>
            <select
              style={{
                flex: 1, fontSize: 11, padding: '3px 6px', borderRadius: 4,
                background: '#1E293B', color: '#F8FAFC', border: '1px solid #334155'
              }}
              value={`${selectedProvider}::${selectedModel}`}
              onChange={e => {
                const [p, m] = e.target.value.split('::')
                setSelectedProvider(p)
                setSelectedModel(m)
              }}
            >
              {statusInfo.providers.map(p => (
                <optgroup key={p.id} label={`${p.displayName} ${p.configured ? '(Configured)' : '(Not Configured)'}`}>
                  {p.models.map(m => (
                    <option key={m.id} value={`${p.id}::${m.id}`} disabled={!p.configured}>
                      {m.name} {m.isFree ? ' [Free]' : ''} {m.supportsTools ? ' ⚡ Tools' : ' ❌ No Tools'}
                    </option>
                  ))}
                </optgroup>
              ))}
            </select>
          </div>
        )}
      </div>

      {/* Main Content Area */}
      <div className="we-ai-content" style={{ display: 'flex', flexDirection: 'column', height: 'calc(100% - 90px)', overflowY: 'auto' }}>
        {!statusInfo?.available && (
          <div style={{ padding: 12, margin: 8, borderRadius: 6, background: 'rgba(239, 68, 68, 0.15)', border: '1px solid rgba(239, 68, 68, 0.3)', color: '#FCA5A5', fontSize: 12 }}>
            <ShieldAlert size={16} style={{ display: 'inline', marginRight: 6, verticalAlign: 'middle' }} />
            {statusInfo?.message || 'Dich vu AI chua duoc ket noi. Vui long cau hinh OPENAI_API_KEY tren backend environment.'}
          </div>
        )}

        {/* Prompt suggestions */}
        {messages.length === 0 && (
          <div style={{ padding: 12 }}>
            <p style={{ fontSize: 12, color: '#94A3B8', marginBottom: 8 }}>Goi y nhanh cho AI Coding Agent:</p>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
              {[
                'Doi mau nut thanh vang (#F59E0B)',
                'Them thanh Navigation header',
                'Chinh responsive layout mobile',
                'Tao React component moi'
              ].map(prompt => (
                <button
                  key={prompt}
                  type="button"
                  disabled={!statusInfo?.available || busy}
                  style={{ fontSize: 11, padding: '4px 8px', borderRadius: 4, background: '#1E293B', color: '#E2E8F0', border: '1px solid #334155', cursor: 'pointer' }}
                  onClick={() => handleSendPrompt(prompt)}
                >
                  <Sparkles size={11} style={{ marginRight: 4, color: '#F59E0B' }} />
                  {prompt}
                </button>
              ))}
            </div>
          </div>
        )}

        {/* Messages List */}
        <div style={{ flex: 1, padding: 12, display: 'flex', flexDirection: 'column', gap: 12 }}>
          {messages.map((msg, idx) => (
            <div key={idx} style={{ alignSelf: msg.role === 'user' ? 'flex-end' : 'flex-start', maxWidth: '88%' }}>
              <div style={{
                padding: '8px 12px',
                borderRadius: 8,
                fontSize: 13,
                lineHeight: '1.4',
                background: msg.role === 'user' ? '#2563EB' : '#1E293B',
                color: '#F8FAFC',
                border: msg.role === 'user' ? 'none' : '1px solid #334155'
              }}>
                {msg.content}
              </div>

              {/* Revision Proposal Card */}
              {msg.revisionId && (
                <div style={{ marginTop: 6, padding: 8, borderRadius: 6, background: '#0F172A', border: '1px solid #334155', fontSize: 11 }}>
                  <div style={{ fontWeight: 600, color: '#F59E0B', marginBottom: 4 }}>
                    Draft Revision Propose: {msg.revisionId}
                  </div>
                  {msg.modifiedFiles && msg.modifiedFiles.length > 0 && (
                    <div style={{ color: '#94A3B8', marginBottom: 6 }}>
                      Files modified: {msg.modifiedFiles.join(', ')}
                    </div>
                  )}

                  <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
                    <button
                      type="button"
                      className="ly-button ly-button-quiet"
                      style={{ padding: '2px 8px', fontSize: 11 }}
                      onClick={() => { setDiffs(msg.diffs || []); setShowDiffModal(true) }}
                    >
                      <Eye size={12} style={{ marginRight: 4 }} /> Xem Diff
                    </button>
                    <button
                      type="button"
                      className="ly-button ly-button-primary"
                      style={{ padding: '2px 8px', fontSize: 11 }}
                      onClick={() => handleAcceptRevision(msg.revisionId)}
                    >
                      <Check size={12} style={{ marginRight: 4 }} /> Chap nhan
                    </button>
                    <button
                      type="button"
                      className="ly-button ly-button-quiet"
                      style={{ padding: '2px 8px', fontSize: 11, color: '#EF4444' }}
                      onClick={() => handleRejectRevision(msg.revisionId)}
                    >
                      Tu choi
                    </button>
                    <button
                      type="button"
                      className="ly-button ly-button-quiet"
                      style={{ padding: '2px 8px', fontSize: 11 }}
                      onClick={() => downloadAiExport(snapshotId, msg.revisionId, targetFramework)}
                    >
                      Export ZIP
                    </button>
                  </div>
                </div>
              )}
            </div>
          ))}

          {busy && (
            <div style={{ fontSize: 12, color: '#94A3B8', display: 'flex', alignItems: 'center', gap: 6 }}>
              <RefreshCw size={14} className="spin" />
              AI Coding Agent dang doc code & thuc hien edit workspace...
            </div>
          )}

          {errorText && (
            <div style={{ padding: 8, background: 'rgba(239, 68, 68, 0.1)', border: '1px solid #EF4444', color: '#FCA5A5', borderRadius: 4, fontSize: 12 }}>
              <AlertCircle size={14} style={{ display: 'inline', marginRight: 4 }} />
              {errorText}
            </div>
          )}
        </div>

        {/* Revisions History Overlay / Drawer */}
        {showHistory && (
          <div style={{ padding: 12, background: '#0F172A', borderTop: '1px solid #334155' }}>
            <div style={{ fontSize: 12, fontWeight: 600, color: '#CBD5E1', marginBottom: 6 }}>Lich su Workspace Revisions:</div>
            {revisions.length === 0 ? (
              <span style={{ fontSize: 11, color: '#64748B' }}>Chua co revision nao.</span>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 4, maxHeight: 120, overflowY: 'auto' }}>
                {revisions.map(rev => (
                  <div key={rev.revisionId} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '4px 8px', background: '#1E293B', borderRadius: 4, fontSize: 11 }}>
                    <span>{rev.revisionId} ({rev.targetFramework})</span>
                    <span className={`ly-badge ${rev.status === 'ACCEPTED' ? 'ly-badge-success' : rev.status === 'REJECTED' ? 'ly-badge-error' : ''}`}>
                      {rev.status}
                    </span>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* Input Controls */}
        <label className="we-ai-input" style={{ margin: 8 }}>
          <input
            aria-label="Yeu cau AI"
            placeholder={statusInfo?.available ? "Nhap yeu cau chinh sua code..." : "Dich vu AI chưa duoc ket noi..."}
            value={inputPrompt}
            onChange={e => setInputPrompt(e.target.value)}
            onKeyDown={e => { if (e.key === 'Enter') handleSendPrompt() }}
            disabled={!statusInfo?.available || busy}
          />
          <button
            className="we-icon"
            type="button"
            title="Gui yeu cau AI"
            aria-label="Gui yeu cau AI"
            disabled={!statusInfo?.available || busy || !inputPrompt.trim()}
            onClick={() => handleSendPrompt()}
          >
            <Send size={17} />
          </button>
        </label>
      </div>

      {/* Code Diff Viewer Modal */}
      {showDiffModal && (
        <div style={{
          position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
          background: 'rgba(0, 0, 0, 0.75)', zIndex: 9999,
          display: 'flex', justifyContent: 'center', alignItems: 'center', padding: 20
        }}>
          <div style={{
            width: '90%', maxWidth: 800, maxHeight: '85vh', background: '#0F172A',
            border: '1px solid #334155', borderRadius: 8, display: 'flex', flexDirection: 'column'
          }}>
            <div style={{ padding: '12px 16px', borderBottom: '1px solid #334155', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <strong style={{ color: '#F8FAFC' }}>Unified Code Diff Viewer</strong>
              <button className="we-icon" onClick={() => setShowDiffModal(false)}><X size={18} /></button>
            </div>
            <div style={{ padding: 16, overflowY: 'auto', flex: 1, fontFamily: 'monospace', fontSize: 12 }}>
              {diffs.length === 0 ? (
                <p style={{ color: '#94A3B8' }}>Khong co thay doi code nao.</p>
              ) : (
                diffs.map((diff, i) => (
                  <div key={i} style={{ marginBottom: 16 }}>
                    <div style={{ color: '#F59E0B', fontWeight: 600, marginBottom: 4 }}>
                      File: {diff.filePath} ({diff.changeType})
                    </div>
                    <pre style={{
                      background: '#1E293B', padding: 12, borderRadius: 4, overflowX: 'auto',
                      whiteSpace: 'pre-wrap', color: '#E2E8F0', border: '1px solid #334155'
                    }}>
                      {diff.patchUnified || diff.newContent}
                    </pre>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>
      )}
    </section>
  )
}
