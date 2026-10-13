import { ArrowLeft, Download, History, Layers, MessageSquare, Monitor, PanelRight, Plus, RefreshCw, Smartphone, Undo2, Redo2 } from 'lucide-react'

export function ToolButton({ icon: Icon, label, active, ...props }) {
  return <button type="button" className={`we-icon ${active ? 'is-active' : ''}`} title={label} aria-label={label} {...props}><Icon size={17} /></button>
}

export default function WorkspaceToolbar({ workspace, width, setWidth, zoom, setZoom, onImport, onLayers, onInspector, onAI, aiOpen, exporting, setExporting }) {
  return <header className="we-toolbar">
    <a className="we-icon" href="/#/dashboard" title="Về dashboard" aria-label="Về dashboard"><ArrowLeft size={18} /></a>
    <div className="we-project-title"><h1>{workspace.snapshot?.fileName || 'Thiết kế mới'}</h1><span>{workspace.snapshot ? 'Snapshot cục bộ' : 'Chưa nhập thiết kế'}</span></div>
    <ToolButton icon={Plus} label="Nhập thiết kế" onClick={onImport} />
    <div className="we-viewport-controls"><div className="we-segment"><ToolButton icon={Monitor} label="Desktop" active={width > 500} onClick={() => setWidth(1440)} /><ToolButton icon={Smartphone} label="Mobile" active={width <= 500} onClick={() => setWidth(390)} /></div>
      <select aria-label="Viewport preview" value={width} onChange={e => setWidth(Number(e.target.value))}><option value={1440}>Desktop · 1440</option><option value={1280}>Laptop · 1280</option><option value={768}>Tablet · 768</option><option value={430}>Mobile · 430</option><option value={390}>Mobile · 390</option></select>
      <select aria-label="Zoom preview" value={zoom} onChange={e => setZoom(e.target.value)}><option value="fit">Vừa khung</option><option value="0.5">50%</option><option value="0.75">75%</option><option value="1">100%</option></select></div>
    <div className="we-toolbar-tools"><ToolButton icon={Undo2} label="Undo chưa khả dụng" disabled /><ToolButton icon={Redo2} label="Redo chưa khả dụng" disabled /><ToolButton icon={History} label="Lịch sử chưa khả dụng" disabled /><ToolButton icon={RefreshCw} label="Refresh từ Figma (cần token)" disabled={!workspace.token || !workspace.snapshot || Boolean(workspace.busy) || workspace.snapshot.source !== 'FIGMA_REST'} onClick={workspace.refresh} /></div>
    <ToolButton icon={Layers} label="Mở hoặc đóng layers" onClick={onLayers} /><ToolButton icon={PanelRight} label="Mở hoặc đóng inspector" onClick={onInspector} /><ToolButton icon={MessageSquare} label="Mở hoặc đóng Layoutly AI" active={aiOpen} onClick={onAI} />
    <button type="button" className="ly-button ly-button-primary we-export" disabled={!workspace.snapshot || Boolean(workspace.busy)} onClick={() => setExporting(!exporting)} aria-expanded={exporting}><Download size={16} /><span>Xuất dự án</span></button>
  </header>
}
