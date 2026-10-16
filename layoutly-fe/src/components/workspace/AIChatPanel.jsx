import { useEffect, useRef, useState } from 'react'
import { AlertCircle, ArrowUpRight, Check, ChevronRight, Code2, Download, Eye, FileCode2, History, Loader2, Paintbrush, PanelRightClose, RefreshCw, Send, Settings2, Smartphone, Sparkles, X } from 'lucide-react'
import { snapshotApi, downloadAiExport } from '../../lib/api.js'

const suggestions = [
  { label: 'Đổi màu nút', prompt: 'Đổi màu nút chính thành vàng #F59E0B, bo góc 16px và thêm hiệu ứng hover.', icon: Paintbrush },
  { label: 'Chỉnh mobile', prompt: 'Cải thiện responsive cho mobile, giữ nguyên thiết kế desktop.', icon: Smartphone },
  { label: 'Thêm navbar', prompt: 'Thêm navbar phù hợp với thiết kế hiện tại.', icon: Code2 },
  { label: 'Làm giao diện đẹp hơn', prompt: 'Cải thiện spacing và typography của giao diện hiện tại.', icon: Sparkles },
]

export function connectionState(info, busy, failed, model) {
  if (busy) return { label: 'Processing', kind: 'working' }
  if (failed || info?.modelError || model?.available === false) return { label: 'Model Error', kind: 'error' }
  if (info?.modelReady === true || model?.ready === true) return { label: 'Model Ready', kind: 'success' }
  if (info?.providerReachable === true) return { label: 'Provider Reachable', kind: 'success' }
  if (info?.available) return { label: 'Configured · unverified', kind: 'neutral' }
  return { label: 'Not Configured', kind: 'neutral' }
}

export function diffStats(diffs = []) {
  return diffs.reduce((sum, diff) => {
    for (const line of (diff.patchUnified || '').split('\n')) {
      if (line.startsWith('+') && !line.startsWith('+++')) sum.added++
      if (line.startsWith('-') && !line.startsWith('---')) sum.deleted++
    }
    return sum
  }, { added: 0, deleted: 0 })
}

// React escapes all text; deliberately do not interpret HTML supplied by the model.
function MessageContent({ content = '' }) {
  return content.split(/(```[\s\S]*?```)/g).map((part, index) => part.startsWith('```')
    ? <pre key={index}><code>{part.replace(/^```[^\n]*\n?/, '').replace(/```$/, '')}</code></pre>
    : <span key={index}>{part.split(/(\*\*[^*]+\*\*|`[^`]+`)/g).map((text, i) => text.startsWith('**') ? <strong key={i}>{text.slice(2, -2)}</strong> : text.startsWith('`') ? <code key={i}>{text.slice(1, -1)}</code> : text)}</span>)
}

function DraftRevisionCard({ revision, diffs, pending, onDiff, onPreview, onAccept, onReject, onExport }) {
  const files = revision.modifiedFiles || diffs.map(diff => diff.filePath)
  const stats = diffStats(diffs)
  const draft = revision.status === 'DRAFT'
  return <article className="we-revision-card">
    <div className="we-revision-heading"><strong title={revision.revisionId}>{draft ? 'Draft Revision' : 'Revision'} <span>{revision.revisionId}</span></strong><span className={`we-check-badge ${revision.buildSuccess === false ? 'is-error' : ''}`}><i />{revision.buildSuccess === true ? 'Static checks passed' : revision.buildSuccess === false ? 'Checks failed' : 'Not validated'}</span></div>
    <div className="we-revision-summary"><span>{files.length} files changed</span>{diffs.length > 0 && <><b className="we-added">+{stats.added}</b><b className="we-deleted">−{stats.deleted}</b></>}<span className="we-revision-state">{revision.status}</span></div>
    <div className="we-changed-files">{files.map(file => <button type="button" key={file} title={file} onClick={() => onDiff(revision)}><ChevronRight size={13} /><span>{file}</span><FileCode2 size={13} /></button>)}</div>
    {revision.buildDiagnostics && <details className="we-diagnostics"><summary>Validation details</summary><pre>{revision.buildDiagnostics}</pre></details>}
    <div className="we-revision-actions"><button disabled={pending} onClick={() => onDiff(revision)}>Xem diff</button><button disabled={pending} onClick={() => onPreview(revision)}><Eye size={13} />Xem trước</button>{draft ? <><button className="we-accept" disabled={pending} onClick={() => onAccept(revision)}>Chấp nhận</button><button className="we-reject" disabled={pending} onClick={() => onReject(revision)}>Từ chối</button></> : <button disabled={pending} onClick={() => onExport(revision)}><Download size={13} />ZIP</button>}</div>
  </article>
}

export default function AIChatPanel({ snapshot, selectedNode, onClose, onRevisionAccepted, onPreviewRevision, onRevisionRejected }) {
  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [framework, setFramework] = useState('REACT')
  const [busy, setBusy] = useState(false)
  const [pending, setPending] = useState(false)
  const [info, setInfo] = useState(null)
  const [revisions, setRevisions] = useState([])
  const [draft, setDraft] = useState(null)
  const [tab, setTab] = useState('Chat')
  const [error, setError] = useState('')
  const [modelError, setModelError] = useState(false)
  const [provider, setProvider] = useState('')
  const [model, setModel] = useState('')
  const [settings, setSettings] = useState(false)
  const [diffModal, setDiffModal] = useState(null)
  const [lastPrompt, setLastPrompt] = useState('')
  const scroll = useRef(null)
  const textarea = useRef(null)
  const epoch = useRef(0)
  const lock = useRef(false)
  const snapshotId = snapshot?.snapshotId
  useEffect(() => {
    const current = ++epoch.current
    lock.current = false
    setMessages([]); setDraft(null); setRevisions([]); setInfo(null); setError(''); setInput(''); setModelError(false); setBusy(false); setPending(false); setDiffModal(null)
    if (snapshotId) {
      snapshotApi.aiStatus(snapshotId).then(res => { if (epoch.current === current) { setInfo(res.json); setProvider(res.json?.activeProvider || ''); setModel(res.json?.activeModel || '') } }).catch(err => { if (epoch.current === current) setError(err.message) })
      snapshotApi.aiRevisions(snapshotId).then(res => { if (epoch.current === current) setRevisions(res.json || []) }).catch(err => { if (epoch.current === current) setError(err.message) })
    }
    return () => { epoch.current++ }
  }, [snapshotId])
  useEffect(() => { scroll.current?.scrollTo?.({ top: scroll.current.scrollHeight, behavior: 'smooth' }) }, [messages, busy, draft])
  useEffect(() => { if (textarea.current) { textarea.current.style.height = 'auto'; textarea.current.style.height = Math.min(140, Math.max(62, textarea.current.scrollHeight)) + 'px' } }, [input])
  const providerInfo = info?.providers?.find(item => item.id === provider)
  const modelInfo = providerInfo?.models?.find(item => item.id === model)
  const usable = Boolean(snapshotId && info?.available && (!providerInfo || (providerInfo.configured && modelInfo)) && modelInfo?.available !== false && modelInfo?.supportsTools !== false)
  const connectionInfo = providerInfo ? { ...info, available: providerInfo.configured, modelReady: info?.activeProvider === provider && info?.activeModel === model && info?.modelReady, providerReachable: info?.activeProvider === provider && info?.providerReachable } : info
  const state = connectionState(connectionInfo, busy, modelError, modelInfo)
  const currentDraft = draft || [...revisions].reverse().find(item => item.status === 'DRAFT')
  const activeDiffs = currentDraft?.diffs || []
  async function history(current = epoch.current) { const response = await snapshotApi.aiRevisions(snapshotId); if (current === epoch.current) setRevisions(response.json || []) }
  useEffect(() => {
    if (!snapshotId || !snapshotApi.aiConversation) return
    let active = true
    snapshotApi.aiConversation(snapshotId).then(response => {
      if (active) setMessages((response.json?.messages || []).map(item => ({ role: item.role, content: item.content?.message || '' })))
    }).catch(caught => { if (active) setError(caught.message) })
    return () => { active = false }
  }, [snapshotId])
  async function send(prompt = input) {
    if (!prompt.trim() || lock.current || busy || !usable) return
    lock.current = true
    const current = epoch.current
    setMessages(items => [...items, { role: 'user', content: prompt.trim() }]); setLastPrompt(prompt.trim()); setInput(''); setBusy(true); setError(''); setModelError(false); setTab('Chat')
    try {
      const response = await snapshotApi.aiChat(snapshotId, { prompt: prompt.trim(), targetFramework: framework, selectedNodeId: selectedNode?.id, selectedNodeName: selectedNode?.name, history: messages.slice(-6).map(({ role, content }) => ({ role, content })), provider, model })
      if (current !== epoch.current) return
      const result = { ...response.json, targetFramework: framework }
      if (result.status === 'ERROR') throw new Error(result.message || 'AI không hoàn thành yêu cầu.')
      setMessages(items => [...items, { role: 'assistant', content: result.message || 'Đã tạo đề xuất chỉnh sửa.' }])
      if (result.revisionId) setDraft(result)
      await history(current)
    } catch (caught) { if (current === epoch.current) { setError(caught.message); setModelError(true) } }
    finally { if (current === epoch.current) { setBusy(false); lock.current = false } }
  }
  async function action(operation) {
    if (pending || busy) return
    const current = epoch.current
    setPending(true); setError('')
    try { await operation(current) } catch (caught) { if (current === epoch.current) setError(caught.message) } finally { if (current === epoch.current) setPending(false) }
  }
  function accept(revision) { return action(async current => { const result = await snapshotApi.aiAccept(snapshotId, revision.revisionId, revision.targetFramework || framework); if (current !== epoch.current) return; const accepted = { ...revision, ...result.json, status: 'ACCEPTED' }; setDraft(accepted); await onRevisionAccepted?.(accepted); await history(current) }) }
  function reject(revision) { return action(async current => { await snapshotApi.aiReject(snapshotId, revision.revisionId); if (current !== epoch.current) return; setDraft({ ...revision, status: 'REJECTED' }); onRevisionRejected?.(revision); await history(current) }) }
  function viewDiff(revision) { return action(async current => { const result = await snapshotApi.aiDiff(snapshotId, revision.revisionId, revision.targetFramework || framework); if (current === epoch.current) setDiffModal({ revision, diffs: result.json || [] }) }) }
  function preview(revision) { onPreviewRevision?.({ ...revision, targetFramework: revision.targetFramework || framework }) }
  function exportRevision(revision) { return action(() => downloadAiExport(snapshotId, revision.revisionId, revision.targetFramework || framework)) }
  function card(revision) { return <DraftRevisionCard key={revision.revisionId} revision={revision} diffs={revision.diffs || []} pending={pending || busy} onDiff={viewDiff} onPreview={preview} onAccept={accept} onReject={reject} onExport={exportRevision} /> }
  function fill(prompt) { setInput(prompt); textarea.current?.focus() }
  return <aside className="we-ai" aria-label="Layoutly AI Assistant">
    <header className="we-ai-header"><Sparkles className="we-ai-mark" size={21} /><h2>Layoutly AI</h2><span className="we-beta">Beta</span><span className={`we-ai-connection is-${state.kind}`} title="Trạng thái do backend cung cấp; cấu hình key chưa xác nhận model hoạt động."><i />{state.label}</span><button className="we-icon" aria-label="Cài đặt AI" title="Cài đặt AI" aria-expanded={settings} onClick={() => setSettings(!settings)}><Settings2 size={16} /></button><button className="we-icon" aria-label="Đóng AI Chat" title="Đóng AI Chat" onClick={onClose}><X size={18} /></button></header>
    <div className="we-model-row"><label htmlFor="we-model">Model</label><select id="we-model" aria-label="AI model" disabled={busy || !info?.providers?.length} value={`${provider}::${model}`} onChange={event => { const [p, ...m] = event.target.value.split('::'); setProvider(p); setModel(m.join('::')); setModelError(false) }}>{(!info?.providers?.length || !modelInfo) && <option value={`${provider}::${model}`}>{info?.activeModel || 'Chưa có model được cấu hình'}</option>}{info?.providers?.map(p => <optgroup key={p.id} label={p.displayName || p.id}>{p.models?.map(m => <option key={m.id} value={`${p.id}::${m.id}`} disabled={!p.configured || m.available === false || m.supportsTools === false}>{m.name || m.id}{m.isFree ? ' (Free)' : ''}{!p.configured ? ' · Not configured' : m.supportsTools === false ? ' · No tools' : ''}</option>)}</optgroup>)}</select></div>
    {settings && <div className="we-ai-settings"><label>Output<select aria-label="AI target framework" value={framework} disabled={busy} onChange={e => setFramework(e.target.value)}><option value="REACT">React</option><option value="HTML">HTML / CSS</option></select></label><p>{info?.message || 'Import hoặc mở snapshot để đọc cấu hình AI từ backend.'}</p><button onClick={() => action(async current => { const response = await snapshotApi.aiStatus(snapshotId); if (current === epoch.current) setInfo(response.json) })} disabled={!snapshotId || busy || pending}><RefreshCw size={13} />Kiểm tra cấu hình</button></div>}
    <div className="we-ai-tabs" role="tablist" aria-label="AI views">{['Chat', 'Changes', 'Revisions'].map(value => <button key={value} role="tab" aria-selected={tab === value} className={tab === value ? 'is-active' : ''} onClick={() => setTab(value)}>{value}{value !== 'Chat' && <span>{value === 'Changes' ? (currentDraft?.modifiedFiles?.length || activeDiffs.length) : revisions.length}</span>}</button>)}</div>
    <div className="we-ai-scroll" ref={scroll} role="tabpanel" aria-label={tab}>
      {tab === 'Chat' && <>
        <section className="we-ai-welcome"><div className="we-welcome-title"><span className="we-ai-avatar"><Sparkles size={19} /></span><div><h3>Xin chào! Tôi là Layoutly AI.</h3><p>Tôi có thể giúp bạn chỉnh sửa giao diện, cải thiện responsive, thêm component và sửa lỗi code.</p></div></div><div className="we-prompt-chips">{suggestions.map(({ label, prompt, icon: Icon }) => <button key={label} title={`Điền yêu cầu: ${prompt}`} disabled={busy} onClick={() => fill(prompt)}><Icon size={13} />{label}</button>)}</div></section>
        <div className="we-messages" aria-live="polite">{messages.map((message, index) => <div key={index} className={`we-message is-${message.role}`}>{message.role === 'assistant' && <span className="we-ai-avatar"><Sparkles size={17} /></span>}<div className="we-message-bubble"><MessageContent content={message.content} /></div></div>)}</div>
        {busy && <div className="we-execution" role="status"><Loader2 size={17} className="we-spin" /><div><strong>AI đang xử lý yêu cầu</strong><p>Đang chờ kết quả từ backend.</p></div></div>}
        {draft && card(draft)}
      </>}
      {tab === 'Changes' && <><div className="we-ai-section-heading"><FileCode2 size={16} /><h3>Proposed changes</h3></div>{currentDraft ? card(currentDraft) : <div className="we-ai-empty"><FileCode2 size={26} /><h3>Chưa có đề xuất chỉnh sửa</h3><p>Gửi yêu cầu để AI tạo một draft revision.</p></div>}</>}
      {tab === 'Revisions' && <><div className="we-ai-section-heading"><History size={16} /><h3>Revision history</h3><button className="we-icon" title="Refresh history" onClick={() => action(() => history())} disabled={pending || busy}><RefreshCw size={14} /></button></div>{revisions.length ? [...revisions].reverse().map(card) : <div className="we-ai-empty"><History size={26} /><h3>Chưa có revision nào</h3><p>Các bản chỉnh sửa sẽ được lưu ở đây.</p></div>}</>}
      {error && <div className="we-ai-error" role="alert"><AlertCircle size={16} /><div>{error}{lastPrompt && <button disabled={busy || !usable} onClick={() => send(lastPrompt)}>Thử lại</button>}</div></div>}
    </div>
    <div className="we-composer-area">{!usable && !busy && <p className="we-ai-config-notice">{!snapshotId ? 'Mở snapshot để bắt đầu chỉnh sửa với AI.' : modelInfo?.supportsTools === false ? 'Model này chưa hỗ trợ công cụ chỉnh sửa code.' : info?.available ? 'Chọn model khả dụng để chỉnh sửa.' : 'AI chưa được cấu hình trên backend.'}</p>}
      <div className="we-composer">{selectedNode && <div className="we-context"><Code2 size={12} /><span>{selectedNode.name || selectedNode.id}</span></div>}<textarea ref={textarea} aria-label="Yêu cầu AI" placeholder="Nhập yêu cầu chỉnh sửa giao diện hoặc code..." value={input} disabled={busy} onChange={e => setInput(e.target.value)} onKeyDown={e => { if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) { e.preventDefault(); send() } }} /><div className="we-composer-bottom"><span><Sparkles size={13} />{selectedNode ? 'Selected node context' : framework === 'REACT' ? 'React workspace' : 'HTML / CSS workspace'}</span><button className="we-send" aria-label="Gửi yêu cầu AI" title="Gui yeu cau AI" disabled={!usable || busy || !input.trim()} onClick={() => send()}>{busy ? <Loader2 className="we-spin" size={17} /> : <Send size={17} />}</button></div></div>
      <div className="we-quick-prompts"><span>Gợi ý nhanh:</span>{suggestions.slice(0, 3).map(item => <button key={item.label} disabled={busy} onClick={() => fill(item.prompt)}>{item.label}</button>)}</div>
    </div>
    {diffModal && <DiffDialog revision={diffModal.revision} diffs={diffModal.diffs} onClose={() => setDiffModal(null)} />}
  </aside>
}

function DiffDialog({ revision, diffs, onClose }) {
  const ref = useRef(null)
  useEffect(() => { const dialog = ref.current; dialog.showModal(); return () => dialog.close() }, [])
  return <dialog ref={ref} className="we-dialog we-diff-dialog" aria-label="Code diff" onCancel={onClose}><div className="we-dialog-heading"><FileCode2 size={18} /><h2>Code diff</h2><button className="we-icon" aria-label="Đóng diff" onClick={onClose}><X size={18} /></button></div><p className="we-muted">{revision.revisionId}</p><div className="we-diff-content">{diffs.length ? diffs.map((diff, index) => <section key={index}><h3>{diff.filePath} <small>{diff.changeType}</small></h3><pre>{(diff.patchUnified || diff.newContent || '').split('\n').map((line, i) => <span key={i} className={line.startsWith('+') ? 'is-added' : line.startsWith('-') ? 'is-deleted' : ''}>{line || ' '}<br /></span>)}</pre></section>) : <p>Không có thay đổi code nào.</p>}</div></dialog>
}
