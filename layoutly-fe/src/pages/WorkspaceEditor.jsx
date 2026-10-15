import { useEffect, useMemo, useState } from 'react'
import '@fontsource/inter/400.css'
import '@fontsource/inter/500.css'
import '@fontsource/inter/600.css'
import '@fontsource/inter/700.css'
import '@fontsource/inter/800.css'
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

export default function WorkspaceEditor() {
  const workspace = useSnapshotWorkspace()
  const [width, setWidth] = useState(1440)
  const [zoom, setZoom] = useState('fit')
  const [selected, setSelected] = useState(null)
  const [layersOpen, setLayersOpen] = useState(() => window.innerWidth >= 900)
  const [inspectorOpen, setInspectorOpen] = useState(() => window.innerWidth >= 900)
  const [aiOpen, setAiOpen] = useState(false)
  const [importOpen, setImportOpen] = useState(() => new URLSearchParams(window.location.hash.split('?')[1]).get('import') === '1')
  const [exporting, setExporting] = useState(false)
  const [exportType, setExportType] = useState('AUTO')
  const rows = useMemo(() => flattenNodes(workspace.tree), [workspace.tree])
  const nodeByAnchor = useMemo(() => new Map(rows.map(({ node }) => [nodeAnchor(node.id), node])), [rows])
  const selectedNode = rows.find(({ node }) => node.id === selected)?.node
  useEffect(() => { document.title = `${workspace.snapshot?.fileName || 'Workspace'} - Layoutly`; setSelected(null) }, [workspace.snapshot?.snapshotId, workspace.version])
  function toggleLayers() { setLayersOpen(value => window.innerWidth < 900 && inspectorOpen ? true : !value); if (window.innerWidth < 900) setInspectorOpen(false) }
  function toggleInspector() { setInspectorOpen(value => window.innerWidth < 900 && layersOpen ? true : !value); if (window.innerWidth < 900) setLayersOpen(false) }
  return <div className="public-app workspace-editor" id="workspace">
    <WorkspaceToolbar workspace={workspace} width={width} setWidth={setWidth} zoom={zoom} setZoom={setZoom} onImport={() => setImportOpen(true)} onLayers={toggleLayers} onInspector={toggleInspector} onAI={() => setAiOpen(!aiOpen)} aiOpen={aiOpen} exporting={exporting} setExporting={setExporting} />
    {(workspace.error || workspace.status) && !importOpen && <div className={workspace.error ? 'we-banner we-error' : 'we-banner we-status'} role={workspace.error ? 'alert' : 'status'}>{workspace.error || workspace.status}</div>}
    {exporting && <section className="we-export-menu" aria-label="Xuất dự án"><label>HTML pages<select value={exportType} onChange={e => setExportType(e.target.value)}><option value="AUTO">AUTO</option><option value="CANVAS">CANVAS</option><option value="FRAME">FRAME</option></select></label><button className="ly-button ly-button-primary" type="button" onClick={() => workspace.exportProject('HTML', exportType)} disabled={Boolean(workspace.busy)}>HTML/CSS ZIP</button><button className="ly-button ly-button-quiet" type="button" onClick={() => workspace.exportProject('REACT')} disabled={Boolean(workspace.busy)}>React ZIP</button><button className="ly-button ly-button-quiet" type="button" onClick={workspace.saveFixture} disabled={Boolean(workspace.busy)}>Lưu test fixture</button></section>}
    <main className={`we-body ${layersOpen ? '' : 'we-hide-layers'} ${inspectorOpen ? '' : 'we-hide-inspector'}`}>
      {layersOpen && <LayersTree tree={workspace.tree} selected={selected} onSelect={setSelected} onClose={() => setLayersOpen(false)} />}
      <PreviewCanvas snapshot={workspace.snapshot} version={workspace.version} width={width} zoom={zoom} selected={selected} nodeByAnchor={nodeByAnchor} onSelect={setSelected} />
      {inspectorOpen && <aside className="we-inspector"><CodePanel workspace={workspace} /><PropertiesPanel node={selectedNode} /></aside>}
      {!workspace.snapshot && !workspace.busy && <div className="we-import-action"><button type="button" className="ly-button ly-button-primary" onClick={() => setImportOpen(true)}>Import Figma</button></div>}
    </main>
    {aiOpen && <AIChatPanel snapshot={workspace.snapshot} selectedNode={selectedNode} onClose={() => setAiOpen(false)} onRevisionAccepted={workspace.refreshCode} />}
    {importOpen && <ImportDialog workspace={workspace} onClose={() => setImportOpen(false)} />}
  </div>
}
