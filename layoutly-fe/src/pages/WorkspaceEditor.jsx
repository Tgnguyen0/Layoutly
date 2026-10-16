import { useEffect, useMemo, useState } from 'react'
import '@fontsource/inter/400.css'
import '@fontsource/inter/500.css'
import '@fontsource/inter/600.css'
import '@fontsource/inter/700.css'
import '@fontsource/inter/800.css'
import { X } from 'lucide-react'
import useSnapshotWorkspace from '../hooks/useSnapshotWorkspace.js'
import { flattenNodes, nodeAnchor } from '../lib/workspace.js'
import WorkspaceToolbar from '../components/workspace/WorkspaceToolbar.jsx'
import LayersTree from '../components/workspace/LayersTree.jsx'
import CodePanel from '../components/workspace/CodePanel.jsx'
import PropertiesPanel from '../components/workspace/PropertiesPanel.jsx'
import PreviewCanvas from '../components/workspace/PreviewCanvas.jsx'
import AIChatPanel from '../components/workspace/AIChatPanel.jsx'
import ImportDialog from '../components/workspace/ImportDialog.jsx'
import '../styles/public.css'
import '../styles/editor.css'
import '../styles/workspace.css'

export default function WorkspaceEditor() {
  const workspace = useSnapshotWorkspace()
  const [width, setWidth] = useState(1440)
  const [zoom, setZoom] = useState('fit')
  const [selected, setSelected] = useState(null)
  const [selectedPage, setSelectedPage] = useState(null)
  const [layersOpen, setLayersOpen] = useState(() => window.innerWidth >= 1200)
  const [aiOpen, setAiOpen] = useState(() => window.innerWidth >= 1440)
  const [mode, setMode] = useState('preview')
  const [codeOpen, setCodeOpen] = useState(true)
  const [codeHeight, setCodeHeight] = useState(286)
  const [aiWidth, setAiWidth] = useState(430)
  const [importOpen, setImportOpen] = useState(() => new URLSearchParams(window.location.hash.split('?')[1]).get('import') === '1')
  const [exporting, setExporting] = useState(false)
  const [exportType, setExportType] = useState('AUTO')
  const rows = useMemo(() => flattenNodes(workspace.tree), [workspace.tree])
  const nodeByAnchor = useMemo(() => new Map(rows.map(({ node }) => [nodeAnchor(node.id), node])), [rows])
  const selectedNode = rows.find(({ node }) => node.id === selected)?.node
  useEffect(() => { document.title = `${workspace.snapshot?.fileName || 'Workspace'} - Layoutly`; setSelected(null); setSelectedPage(null) }, [workspace.snapshot?.snapshotId])
  useEffect(() => { function escape(e) { if (e.key === 'Escape') { setMode('preview'); setExporting(false); if (window.innerWidth < 1200) { setLayersOpen(false); setAiOpen(false) } } } window.addEventListener('keydown', escape); return () => window.removeEventListener('keydown', escape) }, [])
  function resize(e, axis) {
    e.preventDefault()
    const target = e.currentTarget
    target.setPointerCapture(e.pointerId)
    document.documentElement.classList.add('we-resizing')
    const start = axis === 'code' ? e.clientY : e.clientX
    const initial = axis === 'code' ? codeHeight : aiWidth
    function move(event) { const delta = start - (axis === 'code' ? event.clientY : event.clientX); if (axis === 'code') setCodeHeight(Math.max(150, Math.min(window.innerHeight - 260, initial + delta))); else setAiWidth(Math.max(360, Math.min(520, initial + delta))) }
    function end() { document.documentElement.classList.remove('we-resizing'); target.removeEventListener('pointermove', move); target.removeEventListener('pointerup', end); target.removeEventListener('pointercancel', end) }
    target.addEventListener('pointermove', move); target.addEventListener('pointerup', end); target.addEventListener('pointercancel', end)
  }
  function changeMode(next) { setMode(next); if (next === 'code') setCodeOpen(value => window.innerWidth < 900 ? true : mode === 'code' ? !value : true); if (window.innerWidth < 900) { setAiOpen(false); setLayersOpen(false) } }
  return <div className="public-app workspace-editor" id="workspace" style={{ '--we-code-height': `${codeHeight}px`, '--we-ai-width': `${aiWidth}px` }}>
    <WorkspaceToolbar workspace={workspace} width={width} setWidth={setWidth} onImport={() => setImportOpen(true)} onLayers={() => { setLayersOpen(!layersOpen); if (window.innerWidth < 1200) setAiOpen(false) }} onInspector={() => { setMode(mode === 'properties' ? 'preview' : 'properties'); if (window.innerWidth < 900) setAiOpen(false) }} onAI={() => { setAiOpen(!aiOpen); if (window.innerWidth < 1200) setLayersOpen(false) }} aiOpen={aiOpen} mode={mode} onMode={changeMode} exporting={exporting} setExporting={setExporting} />
    {(workspace.error || workspace.status) && !importOpen && <div className={workspace.error ? 'we-banner we-error' : 'we-banner we-status'} role={workspace.error ? 'alert' : 'status'}>{workspace.error || workspace.status}</div>}
    {exporting && <section className="we-export-menu" aria-label="Xuất dự án">{workspace.acceptedRevision && <p className="we-muted">Export accepted revision · {workspace.acceptedRevision.revisionId}</p>}<label>HTML pages<select value={exportType} onChange={e => setExportType(e.target.value)}><option value="AUTO">AUTO</option><option value="CANVAS">CANVAS</option><option value="FRAME">FRAME</option></select></label><button className="ly-button ly-button-primary" type="button" onClick={() => workspace.exportProject('HTML', exportType)} disabled={Boolean(workspace.busy) || Boolean(workspace.acceptedRevision && workspace.acceptedRevision.targetFramework !== 'HTML')}>HTML/CSS ZIP</button><button className="ly-button ly-button-quiet" type="button" onClick={() => workspace.exportProject('REACT')} disabled={Boolean(workspace.busy) || Boolean(workspace.acceptedRevision && workspace.acceptedRevision.targetFramework !== 'REACT')}>React ZIP</button><button className="ly-button ly-button-quiet" type="button" onClick={workspace.saveFixture} disabled={Boolean(workspace.busy)}>Lưu test fixture</button></section>}
    <main className={`we-body ${layersOpen ? '' : 'we-hide-layers'} ${aiOpen ? 'we-ai-open' : ''} we-mode-${mode}`}>
      {layersOpen && <LayersTree tree={workspace.tree} selected={selected} onSelect={setSelected} onSelectPage={id => { setSelectedPage(id); setSelected(id) }} onClose={() => setLayersOpen(false)} />}
      <div className={`we-center ${codeOpen ? '' : 'we-code-collapsed'}`}>
        <PreviewCanvas snapshot={workspace.snapshot} version={workspace.version} width={width} setWidth={setWidth} zoom={zoom} setZoom={setZoom} selected={selected} selectedPage={selectedPage} nodeByAnchor={nodeByAnchor} onSelect={setSelected} revision={workspace.viewRevision} />
        {mode === 'properties' && <aside className="we-properties-drawer" aria-label="Node properties"><div className="we-drawer-heading"><strong>Properties</strong><button className="we-icon" title="Đóng properties" onClick={() => setMode('preview')}><X size={16} /></button></div><PropertiesPanel node={selectedNode} /></aside>}
        {codeOpen && <><div className="we-code-resizer" role="separator" aria-label="Thay đổi chiều cao code" aria-orientation="horizontal" tabIndex={0} onPointerDown={e => resize(e, 'code')} onKeyDown={e => { if (['ArrowUp', 'ArrowDown'].includes(e.key)) { e.preventDefault(); setCodeHeight(h => Math.max(150, Math.min(window.innerHeight - 260, h + (e.key === 'ArrowUp' ? 20 : -20)))) } }} /><CodePanel workspace={workspace} onCollapse={() => setCodeOpen(false)} /></>}
        {!workspace.snapshot && !workspace.busy && <div className="we-import-action"><button type="button" className="ly-button ly-button-primary" onClick={() => setImportOpen(true)}>Import Figma</button></div>}
      </div>
      <div className={`we-ai-slot ${aiOpen ? 'is-open' : ''}`} hidden={!aiOpen}><div className="we-ai-resizer" role="separator" aria-label="Thay đổi chiều rộng AI" aria-orientation="vertical" tabIndex={aiOpen ? 0 : -1} onPointerDown={e => resize(e, 'ai')} onKeyDown={e => { if (['ArrowLeft', 'ArrowRight'].includes(e.key)) { e.preventDefault(); setAiWidth(w => Math.max(360, Math.min(520, w + (e.key === 'ArrowLeft' ? 20 : -20)))) } }} /><AIChatPanel snapshot={workspace.snapshot} selectedNode={selectedNode} onClose={() => setAiOpen(false)} onRevisionAccepted={workspace.acceptRevision} onPreviewRevision={workspace.setViewRevision} onRevisionRejected={workspace.rejectRevisionView} /></div>
    </main>
    {importOpen && <ImportDialog workspace={workspace} onClose={() => setImportOpen(false)} />}
  </div>
}
