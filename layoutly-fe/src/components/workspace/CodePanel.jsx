import { useState } from 'react'
import { Check, Copy } from 'lucide-react'

export default function CodePanel({ workspace }) {
  const [copied, setCopied] = useState(false)
  const [copyError, setCopyError] = useState('')
  async function copy() {
    try { await navigator.clipboard.writeText(workspace.code); setCopied(true); setCopyError('') }
    catch { setCopyError('Không thể truy cập clipboard.') }
  }
  return <section className="we-code-panel" aria-label="Mã nguồn sinh ra">
    <div className="we-panel-tabs">{['HTML', 'CSS', 'REACT', 'JSON'].map(tab => <button type="button" className={workspace.tab === tab ? 'is-active' : ''} key={tab} onClick={() => { workspace.setTab(tab); setCopied(false) }}>{tab === 'REACT' ? 'React' : tab}</button>)}</div>
    <div className="we-code-title"><span>{({ HTML: 'index.html', CSS: 'styles.css', REACT: 'App.jsx', JSON: 'DesignNode.json' })[workspace.tab]}</span><button className="we-icon" type="button" title="Copy code" aria-label="Copy code" disabled={!workspace.code} onClick={copy}>{copied ? <Check size={14} /> : <Copy size={14} />}</button></div>
    <pre><code>{workspace.code || (workspace.snapshot ? 'Đang tải mã nguồn...' : 'Chưa có mã nguồn.')}</code></pre>
    {copyError && <p className="we-error" role="alert">{copyError}</p>}
  </section>
}
