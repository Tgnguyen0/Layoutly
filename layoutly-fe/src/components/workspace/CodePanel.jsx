import { useState } from 'react'
import { Check, ChevronDown, Copy, FileCode2 } from 'lucide-react'
import WorkspaceFileExplorer from './WorkspaceFileExplorer.jsx'

const names = { HTML: 'index.html', CSS: 'styles.css', REACT: 'App.jsx', JSON: 'DesignNode.json' }
const tokenPattern = /(\/\/[^\n]*|\/\*[\s\S]*?\*\/|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|`(?:\\.|[^`\\])*`|\b(?:import|from|export|default|function|return|const|let|if|else|class|new|true|false|null|async|await)\b|<\/?[\w.-]+|\b\d+(?:\.\d+)?\b)/g
export function HighlightedCode({ code }) {
  return <code>{code.split('\n').map((line, index) => <span className="we-source-line" key={index}><span className="we-line-number" aria-hidden="true">{index + 1}</span>{line.split(tokenPattern).map((part, i) => { const kind = /^['"`]/.test(part) ? 'string' : /^\/[/\*]/.test(part) ? 'comment' : /^<\/?\w/.test(part) ? 'tag' : /^\d/.test(part) ? 'number' : /^(import|from|export|default|function|return|const|let|if|else|class|new|true|false|null|async|await)$/.test(part) ? 'keyword' : ''; return kind ? <span className={`we-token-${kind}`} key={i}>{part}</span> : part })}{'\n'}</span>)}</code>
}
export default function CodePanel({ workspace, onCollapse }) {
  const [copied, setCopied] = useState(false)
  const [copyError, setCopyError] = useState('')
  const files = (workspace.files || []).filter(file => /\.(jsx?|tsx?|css|html|json|md)$/.test(file))
  const selectedFile = workspace.selectedFile || (workspace.viewRevision ? files.find(file => workspace.tab === 'CSS' ? file.endsWith('.css') : workspace.tab === 'HTML' ? file.endsWith('.html') : workspace.tab === 'REACT' ? file === 'src/App.jsx' : false) : null)
  async function copy() { try { await navigator.clipboard.writeText(workspace.code); setCopied(true); setCopyError('') } catch { setCopyError('Không thể truy cập clipboard.') } }
  return <section className="we-code-panel" aria-label="Mã nguồn sinh ra">
    <div className="we-panel-tabs">{['HTML', 'CSS', 'REACT', 'JSON'].map(tab => <button type="button" className={workspace.tab === tab ? 'is-active' : ''} key={tab} onClick={() => { workspace.setTab(tab); setCopied(false) }}>{tab === 'REACT' ? 'React' : tab}</button>)}<button className="we-icon" title="Thu gọn code" aria-label="Thu gọn code" onClick={onCollapse}><ChevronDown size={16} /></button></div>
    {workspace.viewRevision && <div className="we-revision-switch"><span>{workspace.viewRevision.status} · {workspace.viewRevision.revisionId}</span>{workspace.viewRevision.status === 'DRAFT' && <><button onClick={() => workspace.setViewRevision(workspace.acceptedRevision || { revisionId: 'rev_000_baseline', targetFramework: workspace.viewRevision.targetFramework, status: 'BASELINE' })}>Before</button></>}{workspace.comparisonDraft && workspace.viewRevision.revisionId !== workspace.comparisonDraft.revisionId && <button onClick={() => workspace.setViewRevision(workspace.comparisonDraft)}>After</button>}</div>}
    <div className="we-code-body"><WorkspaceFileExplorer files={files} selected={selectedFile} hasSnapshot={Boolean(workspace.snapshot)} onSelect={file => { workspace.setSelectedFile(file); setCopied(false) }} />
    <div className="we-code-document"><div className="we-code-title"><span><FileCode2 size={13} />{workspace.tab === 'JSON' ? names.JSON : selectedFile || names[workspace.tab]}</span><small>Read only</small><button className="we-icon" type="button" title="Copy code" aria-label="Copy code" disabled={!workspace.code} onClick={copy}>{copied ? <Check size={14} /> : <Copy size={14} />}</button></div><pre><HighlightedCode code={workspace.code || (workspace.snapshot ? 'Đang tải mã nguồn…' : 'Chưa có mã nguồn.')} /></pre>{copyError && <p className="we-error" role="alert">{copyError}</p>}</div></div>
  </section>
}
