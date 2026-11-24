import { useEffect, useRef, useState } from 'react'
import { ArrowRight, Eye, EyeOff, Figma, X } from 'lucide-react'
import { bridgeApi } from '../../lib/api.js'

const CODE_KEY = 'layoutly_bridge_code'

// Hiện mã ghép cặp cho plugin, cho phép yêu cầu đồng bộ và tự mở snapshot khi plugin gửi lên.
function BridgePairing({ workspace }) {
  const [session, setSession] = useState(null)
  const [error, setError] = useState('')
  const [requested, setRequested] = useState('')
  const handled = useRef('')
  const latest = useRef(workspace)
  latest.current = workspace

  async function start(forceNew) {
    setError('')
    try {
      let stored = ''
      try { stored = forceNew ? '' : sessionStorage.getItem(CODE_KEY) || '' } catch { /* bỏ qua */ }
      let view = null
      if (stored) view = (await bridgeApi.getSession(stored).catch(() => null))?.json
      if (!view) view = (await bridgeApi.createSession()).json
      try { sessionStorage.setItem(CODE_KEY, view.code) } catch { /* bỏ qua */ }
      handled.current = view.snapshotAt || ''
      setSession(view)
    } catch (caught) { setError(caught.message === 'Failed to fetch' ? 'Không kết nối được backend.' : caught.message) }
  }
  useEffect(() => { start(false) }, [])

  useEffect(() => {
    if (!session?.code) return
    let active = true
    const timer = setInterval(async () => {
      try {
        const view = (await bridgeApi.getSession(session.code)).json
        if (!active) return
        setSession(view)
        if (view.snapshotId && view.snapshotAt && view.snapshotAt !== handled.current) {
          const ok = await latest.current.openSnapshot(view.snapshotId)
          if (ok && active) { handled.current = view.snapshotAt; setRequested('') }
        }
      } catch (caught) {
        if (!active) return
        // Mã hết hạn (backend khởi động lại hoặc quá 2 giờ) -> tạo mã mới.
        if (/het han|khong ton tai/i.test(caught.message)) start(true)
      }
    }, 1500)
    return () => { active = false; clearInterval(timer) }
  }, [session?.code])

  async function requestSync(scope) {
    setError('')
    try { await bridgeApi.sync(session.code, scope); setRequested(scope); setTimeout(() => setRequested(''), 20000) } catch (caught) { setError(caught.message) }
  }

  const code = session?.code
  return <div className="we-import-guide">
    <h3>Kết nối Figma</h3>
    <p className="we-bridge-state">{!code ? 'Đang tạo mã kết nối...' : session.pluginConnected ? 'Plugin đã kết nối' : 'Đang chờ plugin Layoutly kết nối'}</p>
    {code && <p style={{ fontFamily: 'monospace', fontSize: 28, fontWeight: 800, letterSpacing: '0.3em', margin: '8px 0' }} aria-label="Mã kết nối">{code}</p>}
    <ol>
      <li>Mở file thiết kế trong Figma Desktop và chạy plugin <strong>Layoutly</strong>.</li>
      <li>Nhập mã trên vào ô "Mã kết nối" của plugin rồi bấm Kết nối.</li>
      <li>Thiết kế sẽ tự mở ở đây. Bấm "Đồng bộ lại" để lấy bản mới nhất.</li>
    </ol>
    <div className="we-dialog-actions">
      <button type="button" className="ly-button ly-button-quiet" disabled={!session?.pluginConnected || Boolean(requested)} onClick={() => requestSync('SELECTION')}>Đồng bộ frame đang chọn</button>
      <button type="button" className="ly-button ly-button-primary" disabled={!session?.pluginConnected || Boolean(requested)} onClick={() => requestSync('PAGE')}>{requested ? 'Đang chờ plugin...' : 'Đồng bộ cả page'}</button>
    </div>
    {error && <p className="we-error" role="alert">{error}</p>}
    <p className="we-muted">Không cần personal access token. Mã có hiệu lực 2 giờ.</p>
  </div>
}

export default function ImportDialog({ workspace, onClose }) {
  const [mode, setMode] = useState('BRIDGE')
  const [input, setInput] = useState('')
  const [snapshotId, setSnapshotId] = useState('')
  const [showToken, setShowToken] = useState(false)
  const dialog = useRef(null)
  const close = useRef(onClose)
  close.current = onClose
  useEffect(() => {
    const previous = document.activeElement
    dialog.current.showModal()
    return () => { dialog.current?.close(); previous?.focus?.() }
  }, [])
  async function submit(event) { event.preventDefault(); if (await workspace.importDesign(input)) onClose() }
  async function open(event) { event.preventDefault(); if (await workspace.openSnapshot(snapshotId)) onClose() }
  return <dialog className="we-dialog" ref={dialog} onCancel={event => { event.preventDefault(); close.current() }} aria-labelledby="import-title"><div className="we-dialog-heading"><Figma size={20} /><h2 id="import-title">Nhập thiết kế</h2><button type="button" className="we-icon" aria-label="Đóng nhập thiết kế" onClick={onClose}><X size={18} /></button></div>
    <div className="we-panel-tabs">{['BRIDGE', 'REST', 'SNAPSHOT'].map(value => <button type="button" key={value} className={mode === value ? 'is-active' : ''} onClick={() => setMode(value)}>{value === 'BRIDGE' ? 'Figma Bridge' : value === 'REST' ? 'Figma URL' : 'Snapshot ID'}</button>)}</div>
    {mode === 'BRIDGE' ? <BridgePairing workspace={workspace} /> : mode === 'REST' ? <form onSubmit={submit}><label className="we-field">Figma URL hoặc File Key<input value={input} onChange={e => setInput(e.target.value)} placeholder="https://www.figma.com/design/..." required /></label><label className="we-field">Figma personal access token<div className="we-token-input"><input value={workspace.token} onChange={e => workspace.setToken(e.target.value)} type={showToken ? 'text' : 'password'} autoComplete="off" required /><button type="button" className="we-icon" aria-label={showToken ? 'Ẩn token' : 'Hiện token'} onClick={() => setShowToken(!showToken)}>{showToken ? <EyeOff size={16} /> : <Eye size={16} />}</button></div></label><div className="we-dialog-actions"><button type="button" className="ly-button ly-button-quiet" disabled={Boolean(workspace.busy)} onClick={workspace.validateToken}>Kiểm tra token</button><button className="ly-button ly-button-primary" disabled={Boolean(workspace.busy)} type="submit">Nhập thiết kế <ArrowRight size={15} /></button></div><p className="we-muted">Token chỉ được giữ trong bộ nhớ của phiên làm việc này.</p></form> : <form onSubmit={open}><label className="we-field">Snapshot ID<input value={snapshotId} onChange={e => setSnapshotId(e.target.value)} required autoComplete="off" /></label><button className="ly-button ly-button-primary" disabled={Boolean(workspace.busy)} type="submit">Mở snapshot</button></form>}
    {workspace.error && <p className="we-error" role="alert">{workspace.error}</p>}{workspace.status && <p className="we-status" role="status">{workspace.status}</p>}
  </dialog>
}
