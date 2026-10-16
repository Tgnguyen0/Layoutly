import { useEffect, useRef, useState } from 'react'
import { ArrowRight, Eye, EyeOff, Figma, X } from 'lucide-react'
import { accountApi } from '../../lib/accounts.js'

export default function ImportDialog({ workspace, onClose }) {
  const [mode, setMode] = useState('BRIDGE')
  const [input, setInput] = useState('')
  const [snapshotId, setSnapshotId] = useState('')
  const [showToken, setShowToken] = useState(false)
  const [pairing, setPairing] = useState(null)
  const [pairError, setPairError] = useState('')
  async function pair() {
    setPairError('')
    try { setPairing(await accountApi.bridgeToken()) } catch (error) { setPairError(error.message) }
  }
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
    {mode === 'BRIDGE' ? <div className="we-import-guide"><h3>Layoutly Bridge</h3><p className="we-bridge-state">{workspace.bridge?.status === 'READY' ? 'Backend sẵn sàng nhận thiết kế' : 'Chưa kết nối được backend'}</p><ol><li>Mở plugin Layoutly hoặc Layoutly Figma Bridge trong Figma.</li><li>Chọn frame và đồng bộ tới backend hiện tại.</li><li>Mở đường dẫn Layoutly có snapshotId do plugin cung cấp.</li></ol><p>Không cần personal access token khi đồng bộ qua plugin.</p></div> : mode === 'REST' ? <form onSubmit={submit}><label className="we-field">Figma URL hoặc File Key<input value={input} onChange={e => setInput(e.target.value)} placeholder="https://www.figma.com/design/..." required /></label><label className="we-field">Figma personal access token<div className="we-token-input"><input value={workspace.token} onChange={e => workspace.setToken(e.target.value)} type={showToken ? 'text' : 'password'} autoComplete="off" required /><button type="button" className="we-icon" aria-label={showToken ? 'Ẩn token' : 'Hiện token'} onClick={() => setShowToken(!showToken)}>{showToken ? <EyeOff size={16} /> : <Eye size={16} />}</button></div></label><div className="we-dialog-actions"><button type="button" className="ly-button ly-button-quiet" disabled={Boolean(workspace.busy)} onClick={workspace.validateToken}>Kiểm tra token</button><button className="ly-button ly-button-primary" disabled={Boolean(workspace.busy)} type="submit">Nhập thiết kế <ArrowRight size={15} /></button></div><p className="we-muted">Token chỉ được giữ trong bộ nhớ của phiên làm việc này.</p></form> : <form onSubmit={open}><label className="we-field">Snapshot ID<input value={snapshotId} onChange={e => setSnapshotId(e.target.value)} required autoComplete="off" /></label><button className="ly-button ly-button-primary" disabled={Boolean(workspace.busy)} type="submit">Mở snapshot</button></form>}
    {mode === 'BRIDGE' && <div className="we-import-guide"><button type="button" className="ly-button ly-button-primary" onClick={pair}>Tạo mã kết nối Bridge</button>{pairing && <label className="we-field">Mã kết nối (hết hạn sau 10 phút)<input readOnly value={pairing.token} onFocus={e => e.target.select()} autoComplete="off" /><small>Dán mã này vào plugin trước khi đồng bộ.</small></label>}{pairError && <p role="alert">{pairError}</p>}</div>}
    {workspace.error && <p className="we-error" role="alert">{workspace.error}</p>}{workspace.status && <p className="we-status" role="status">{workspace.status}</p>}
  </dialog>
}
