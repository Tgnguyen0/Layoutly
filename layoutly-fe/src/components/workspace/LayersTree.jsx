import { useMemo, useState } from 'react'
import { ChevronDown, ChevronRight, File, Layers, Search, Type, X } from 'lucide-react'
import { flattenNodes } from '../../lib/workspace.js'

export default function LayersTree({ tree, selected, onSelect, onPage = onSelect, previewRoot, onClose }) {
  const [tab, setTab] = useState('Pages')
  const [query, setQuery] = useState('')
  const [collapsed, setCollapsed] = useState(new Set())
  const rows = useMemo(() => flattenNodes(tree), [tree])
  const matches = rows.filter(({ node }) => `${node.name} ${node.id}`.toLowerCase().includes(query.toLowerCase()))
  const candidates = matches
  const pages = rows.filter(({ node, depth }) => node.type === 'FRAME' && depth <= 2)
  const hidden = new Set()
  if (!query) {
    let hiddenDepth = Infinity
    for (const { node, depth } of rows) {
      if (depth > hiddenDepth) hidden.add(node.id)
      else hiddenDepth = collapsed.has(node.id) ? depth : Infinity
    }
  }
  function toggle(id) { setCollapsed(current => { const next = new Set(current); next.has(id) ? next.delete(id) : next.add(id); return next }) }
  return <aside className="we-layers" aria-label="Pages và Layers">
    <div className="we-panel-tabs">{['Pages', 'Layers'].map(value => <button type="button" key={value} className={tab === value ? 'is-active' : ''} onClick={() => setTab(value)}>{value}</button>)}<button className="we-icon we-close-panel" type="button" aria-label="Đóng layers" onClick={onClose}><X size={16} /></button></div>
    {tab === 'Pages' && pages.length > 0 && <div className="we-pages-list">{pages.map(({ node }) => <button type="button" key={node.id} className={(previewRoot || selected) === node.id ? 'is-selected' : ''} onClick={() => onPage(node.id)} title={node.name}><File size={13} /><span>{node.name}</span></button>)}</div>}
    <label className="we-search"><Search size={14} /><input value={query} onChange={e => setQuery(e.target.value)} placeholder="Tìm layer..." aria-label="Tìm layer" /></label>
    <div className="we-tree" role="list" aria-label="Cây thiết kế">{candidates.filter(({ node }) => !hidden.has(node.id)).map(({ node, depth }) => <div key={node.id} role="listitem" className={`we-node ${selected === node.id ? 'is-selected' : ''}`} style={{ paddingLeft: 8 + Math.min(depth, 8) * 12 }}>
      {node.children?.length ? <button type="button" className="we-tree-toggle" aria-label={`${collapsed.has(node.id) ? 'Mở' : 'Thu gọn'} ${node.name}`} onClick={() => toggle(node.id)}>{collapsed.has(node.id) ? <ChevronRight size={12} /> : <ChevronDown size={12} />}</button> : <span className="we-tree-spacer" />}
      <button className="we-node-label" type="button" title={`${node.name} (${node.id})`} onClick={() => onSelect(node.id)}>{node.type === 'TEXT' ? <Type size={13} /> : node.type === 'FRAME' ? <File size={13} /> : <Layers size={13} />}<span>{node.name || node.type}</span></button>
    </div>)}{!tree && <p className="we-panel-empty">Chưa có thiết kế.</p>}{tree && !candidates.length && <p className="we-panel-empty">Không tìm thấy layer.</p>}</div>
    <div className="we-panel-foot">{rows.length} nodes · {tab === 'Pages' ? 'Chọn node để xem thuộc tính' : 'DesignNode'}</div>
  </aside>
}
