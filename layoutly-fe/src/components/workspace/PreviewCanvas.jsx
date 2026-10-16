import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { ExternalLink, MousePointer2, Play, RefreshCw } from 'lucide-react'
import { nodeAnchor } from '../../lib/workspace.js'

export function preparePreview(html, baseURL, nonce) {
  const document = new DOMParser().parseFromString(html, 'text/html')
  document.querySelectorAll('base').forEach(element => element.remove())
  const base = document.createElement('base')
  base.href = baseURL
  document.head.prepend(base)
  if (document.head.querySelector('style')) document.querySelectorAll('link[rel="stylesheet"][href="styles.css"]').forEach(element => element.remove())
  const style = document.createElement('style')
  style.textContent = '.layoutly-selected{outline:2px solid #258bf7!important;outline-offset:2px!important}'
  document.head.append(style)
  const script = document.createElement('script')
  // The nonce scopes UI messages to this isolated frame; no design text is interpolated.
  script.textContent = `(()=>{const nonce=${JSON.stringify(nonce)};let inspect=true;const send=(type,id)=>parent.postMessage({type,nonce,id},'*');addEventListener('error',e=>send('layoutly-preview-error',e.message));addEventListener('unhandledrejection',e=>send('layoutly-preview-error',String(e.reason?.message||e.reason))); document.addEventListener('click',e=>{if(!inspect)return;const node=e.target.closest('.figma-node[id]');if(node){e.preventDefault();e.stopImmediatePropagation();send('layoutly-select',node.id)}},true);addEventListener('message',e=>{if(e.source!==parent||e.data?.nonce!==nonce)return;if(e.data.type==='layoutly-mode'){inspect=e.data.inspect===true;return}if(e.data.type!=='layoutly-highlight')return;document.querySelectorAll('.layoutly-selected').forEach(n=>n.classList.remove('layoutly-selected'));const node=document.getElementById(e.data.id);if(node){node.classList.add('layoutly-selected');node.scrollIntoView({block:'nearest',inline:'nearest'})}send('layoutly-highlight-result',node?e.data.id:null)});})();`
  document.body.append(script)
  return '<!doctype html>' + document.documentElement.outerHTML
}

export default function PreviewCanvas({ snapshot, version, width, setWidth, zoom, setZoom, selected, selectedPage, nodeByAnchor, onSelect, revision }) {
  const host = useRef(null)
  const frame = useRef(null)
  const [availableWidth, setAvailableWidth] = useState(600)
  const [html, setHtml] = useState('')
  const [htmlKey, setHtmlKey] = useState('')
  const [error, setError] = useState('')
  const [resourceWarning, setResourceWarning] = useState('')
  const [inspect, setInspect] = useState(true)
  const [notRendered, setNotRendered] = useState(false)
  const [reload, setReload] = useState(0)
  const nonce = useRef('')
  const requestedWidth = Number(width)
  const sourceKey = `${snapshot?.snapshotId}:${revision?.revisionId || 'design'}:${version}:${width}:${selectedPage}:${reload}`
  const scale = zoom === 'fit' ? Math.min(1, Math.max(0.05, availableWidth / requestedWidth)) : Number(zoom)
  const height = Math.max(600, 800 / scale)
  useLayoutEffect(() => {
    const observer = new ResizeObserver(entries => setAvailableWidth(entries[0].contentRect.width))
    if (host.current) observer.observe(host.current)
    return () => observer.disconnect()
  }, [])
  useEffect(() => {
    setHtml(''); setError(''); setResourceWarning(''); setNotRendered(false)
    if (!snapshot) return
    const controller = new AbortController()
    const url = new URL(`/api/snapshots/${snapshot.snapshotId}/preview?targetWidth=${width}&v=${version}`, window.location.origin)
    if (selectedPage) url.searchParams.set('nodeId', selectedPage)
    const requestNonce = crypto.randomUUID()
    nonce.current = requestNonce
    const source = revision ? import('../../lib/revisionPreview.js').then(({ revisionPreview }) => revisionPreview(snapshot.snapshotId, revision, controller.signal)) : fetch(url, { signal: controller.signal }).then(async response => {
      if (!response.ok) throw new Error(`Preview HTTP ${response.status}`)
      const missing = Number(response.headers.get('X-Layoutly-Missing-Assets') || 0)
      if (missing && !controller.signal.aborted) setResourceWarning(`Thiết kế còn thiếu ${missing} tài nguyên ảnh. Mở Import Figma và nhập lại cùng URL/token để tải bổ sung.`)
      return response.text()
    })
    source.then(text => {
      if (!controller.signal.aborted) { setHtml(preparePreview(text, url.href, requestNonce)); setHtmlKey(sourceKey) }
    }).catch(caught => { if (!controller.signal.aborted) setError(caught.message) })
    return () => controller.abort()
  }, [snapshot, width, version, revision, reload, selectedPage])
  useEffect(() => {
    const receive = event => {
      if (event.source !== frame.current?.contentWindow || event.data?.nonce !== nonce.current) return
      if (event.data.type === 'layoutly-select' && nodeByAnchor.has(event.data.id)) onSelect(nodeByAnchor.get(event.data.id).id)
      if (event.data.type === 'layoutly-highlight-result') setNotRendered(event.data.id === null)
      if (event.data.type === 'layoutly-preview-error') setError(`Preview runtime: ${event.data.id}`)
    }
    window.addEventListener('message', receive)
    return () => window.removeEventListener('message', receive)
  }, [nodeByAnchor, onSelect])
  function syncFrame() {
    frame.current?.contentWindow?.postMessage({ type: 'layoutly-mode', nonce: nonce.current, inspect }, '*')
    if (selected) frame.current?.contentWindow?.postMessage({ type: 'layoutly-highlight', nonce: nonce.current, id: nodeAnchor(selected) }, '*')
  }
  useEffect(syncFrame, [selected, inspect, html])
  return <section className="we-canvas" ref={host} aria-label="Live Preview">
    <div className="we-canvas-tools"><div className="we-segment"><button className={`we-icon ${inspect ? 'is-active' : ''}`} type="button" title="Chọn node" aria-label="Chọn node" onClick={() => setInspect(true)}><MousePointer2 size={15} /></button><button className={`we-icon ${!inspect ? 'is-active' : ''}`} type="button" title="Tương tác với preview" aria-label="Tương tác với preview" onClick={() => setInspect(false)}><Play size={15} /></button></div><span className="we-preview-source" title={revision?.revisionId}>{revision ? `${revision.status} · ${revision.revisionId}` : 'Live preview'}</span><select className="we-canvas-zoom" aria-label="Zoom preview" value={zoom} onChange={e => setZoom?.(e.target.value)}><option value="fit">Fit · {Math.round(scale * 100)}%</option><option value="0.5">50%</option><option value="0.75">75%</option><option value="1">100%</option></select><button className="we-icon" title="Reload preview" aria-label="Reload preview" disabled={!snapshot} onClick={() => setReload(v => v + 1)}><RefreshCw size={14} /></button>{snapshot && <button className="we-icon" type="button" title="Preview toàn màn hình" aria-label="Preview toàn màn hình" disabled={!html || htmlKey !== sourceKey} onClick={() => { const blob = html && htmlKey === sourceKey ? URL.createObjectURL(new Blob(['<!doctype html><html><body style="margin:0"><iframe sandbox="allow-scripts" style="border:0;width:100vw;height:100vh" srcdoc="' + html.replace(/&/g, '&amp;').replace(/"/g, '&quot;') + '"></iframe></body></html>'], { type: 'text/html' })) : null; window.open(blob || `/preview/${snapshot.snapshotId}`, '_blank', 'noopener,noreferrer'); if (blob) setTimeout(() => URL.revokeObjectURL(blob), 60000) }}><ExternalLink size={15} /></button>}</div>
    <label className="we-mobile-viewport">Viewport<select aria-label="Mobile canvas viewport" value={width} onChange={e => setWidth?.(Number(e.target.value))}><option value={1440}>Desktop · 1440</option><option value={1280}>Laptop · 1280</option><option value={768}>Tablet · 768</option><option value={430}>Mobile · 430</option><option value={390}>Mobile · 390</option></select></label>
    {notRendered && <p className="we-canvas-notice">Node nằm ngoài root preview hiện tại. Thuộc tính vẫn xem được.</p>}
    {resourceWarning && <p className="we-canvas-notice" role="status">{resourceWarning}</p>}
    <div className="we-canvas-scroll">{snapshot ? error ? <p className="we-error" role="alert">{error}</p> : html && htmlKey === sourceKey ? <div className="we-preview-frame" style={{ width: requestedWidth * scale, height: height * scale }}><iframe ref={frame} title="Snapshot preview" sandbox="allow-scripts" srcDoc={html} onLoad={syncFrame} style={{ width: requestedWidth, height, transform: `scale(${scale})` }} /></div> : <p className="we-panel-empty">Đang tải preview...</p> : <div className="we-canvas-empty"><MousePointer2 size={28} /><h2>Thiết kế của bạn ở đây</h2><p>Chưa có snapshot được nhập.</p></div>}</div>
  </section>
}
