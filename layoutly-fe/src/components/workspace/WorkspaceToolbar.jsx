import { ChevronRight, Code2, Download, Eye, Layers, Monitor, Plus, SlidersHorizontal, Sparkles, Undo2, Redo2 } from 'lucide-react'

export function ToolButton({ icon: Icon, label, active, ...props }) {
  return <button type="button" className={`we-icon ${active ? 'is-active' : ''}`} title={label} aria-label={label} {...props}><Icon size={16} /></button>
}

export default function WorkspaceToolbar({ workspace, width, setWidth, onImport, onLayers, onInspector, onAI, aiOpen, mode, onMode, exporting, setExporting }) {
  return <header className="we-toolbar">
    <a className="we-brand" href="/#/dashboard" title="Về dashboard" aria-label="Về dashboard"><span><Code2 size={18} /></span><strong>Layoutly</strong></a>
    <div className="we-breadcrumb"><a href="/#/dashboard">Projects</a><ChevronRight size={13} /><div className="we-project-title"><h1 title={workspace.project?.name || workspace.snapshot?.fileName}>{workspace.project?.name || workspace.snapshot?.fileName || 'Thiết kế mới'}</h1></div><ChevronRight size={12} /></div>
    <span className={`we-save-state ${workspace.error ? 'is-error' : ''}`}><i />{workspace.error ? 'Error' : workspace.busy ? 'Loading…' : workspace.project ? 'Saved' : 'Chưa lưu'}</span>
    <nav className="we-main-tabs" aria-label="Workspace panels">
      <button title="Live preview" className={mode === 'preview' && !aiOpen ? 'is-active' : ''} onClick={() => onMode('preview')}><Eye size={15} /><span>Preview</span></button>
      <button title="Mở hoặc thu gọn code" className={mode === 'code' ? 'is-active' : ''} onClick={() => onMode('code')}><Code2 size={15} /><span>Code</span></button>
      <button title="Thuộc tính node" aria-label="Mở hoặc đóng inspector" className={mode === 'properties' ? 'is-active' : ''} onClick={onInspector}><SlidersHorizontal size={15} /><span>Properties</span></button>
      <button title="Mở hoặc đóng Layoutly AI" aria-label="Mở hoặc đóng Layoutly AI" aria-expanded={aiOpen} className={aiOpen ? 'is-active' : ''} onClick={onAI}><Sparkles size={16} /><span>AI Assistant</span></button>
    </nav>
    <div className="we-viewport-controls"><Monitor size={16} /><select aria-label="Viewport preview" value={width} onChange={e => setWidth(Number(e.target.value))}><option value={1440}>Desktop 1440</option><option value={1280}>Laptop 1280</option><option value={768}>Tablet 768</option><option value={430}>Mobile 430</option><option value={390}>Mobile 390</option></select></div>
    <div className="we-toolbar-tools"><ToolButton icon={Undo2} label="Undo chưa khả dụng" disabled /><ToolButton icon={Redo2} label="Redo chưa khả dụng" disabled /></div>
    <ToolButton icon={Layers} label="Mở hoặc đóng layers" onClick={onLayers} />
    <ToolButton icon={Plus} label="Nhập thiết kế" onClick={onImport} />
    <button type="button" className="we-icon" aria-label="Lưu dự án" title="Lưu dự án" onClick={workspace.saveProject} disabled={!workspace.snapshot || Boolean(workspace.busy)}>Lưu</button>
    <button type="button" className="we-export" title="Xuất mã nguồn dự án" aria-label="Xuất dự án" disabled={!workspace.snapshot || Boolean(workspace.busy)} onClick={() => setExporting(!exporting)} aria-expanded={exporting}><Download size={16} /><span>Export</span></button>
  </header>
}
