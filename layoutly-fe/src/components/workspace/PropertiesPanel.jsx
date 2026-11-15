import { useState } from 'react'

export default function PropertiesPanel({ node }) {
  const [tab, setTab] = useState('Style')
  const style = node?.style || {}
  const rows = tab === 'Style' ? [['Màu nền', style.backgroundColor], ['Font', style.fontFamily], ['Cỡ chữ', style.fontSize], ['Font weight', style.fontWeight], ['Bo góc', style.cornerRadius], ['Opacity', style.opacity]] : tab === 'Layout' ? [['W', node?.bounds?.width], ['H', node?.bounds?.height], ['Layout', node?.layout?.type], ['Direction', node?.layout?.direction], ['Gap', node?.layout?.gap], ['X', node?.bounds?.x], ['Y', node?.bounds?.y]] : [['Role', node?.role], ['Link target', node?.linkTargetId], ['Transition', node?.transition?.type]]
  return <section className="we-properties" aria-label="Thuộc tính node"><div className="we-properties-title"><strong>Thuộc tính</strong><span>Chỉ đọc</span></div><div className="we-panel-tabs">{['Style', 'Layout', 'Interaction'].map(value => <button type="button" key={value} onClick={() => setTab(value)} className={tab === value ? 'is-active' : ''}>{value}</button>)}</div>
    {node ? <><p className="we-selected-name">{node.name} · {node.id}</p><dl>{rows.map(([name, value]) => <div key={name}><dt>{name}</dt><dd>{name === 'Màu nền' && typeof value === 'string' && /^(#[0-9a-f]{3,8}|rgba?\([\d\s.,%]+\))$/i.test(value) && <i className="we-swatch" style={{ backgroundColor: value }} />}{value ?? '—'}</dd></div>)}</dl></> : <p className="we-panel-empty">Chọn một node để xem thuộc tính.</p>}
  </section>
}
