import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { ExternalLink, MousePointer2, Play } from 'lucide-react'
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
  script.textContent = `(()=>{const nonce=${JSON.stringify(nonce)};let inspect=true;const send=(type,id)=>parent.postMessage({type,nonce,id},'*');document.addEventListener('click',e=>{if(!inspect)return;const node=e.target.closest('.figma-node[id]');if(node){e.preventDefault();e.stopImmediatePropagation();send('layoutly-select',node.id)}},true);addEventListener('message',e=>{if(e.source!==parent||e.data?.nonce!==nonce)return;if(e.data.type==='layoutly-mode'){inspect=e.data.inspect===true;return}if(e.data.type!=='layoutly-highlight')return;document.querySelectorAll('.layoutly-selected').forEach(n=>n.classList.remove('layoutly-selected'));const node=document.getElementById(e.data.id);if(node){node.classList.add('layoutly-selected');node.scrollIntoView({block:'nearest',inline:'nearest'})}send('layoutly-highlight-result',node?e.data.id:null)});})();`
  document.body.append(script)
  return '<!doctype html>' + document.documentElement.outerHTML
}

export default function PreviewCanvas({ snapshot, rootId = '', version, width, zoom, selected, nodeByAnchor, onSelect }) {
  const host = useRef(null)
  const frame = useRef(null)
  const [availableWidth, setAvailableWidth] = useState(600)
  const [html, setHtml] = useState('')
  const [error, setError] = useState('')
  const [inspect, setInspect] = useState(true)
  const [notRendered, setNotRendered] = useState(false)
  const nonce = useRef('')
  const requestedWidth = Number(width)
  const scale = zoom === 'fit' ? Math.min(1, Math.max(0.05, (availableWidth - 48) / requestedWidth)) : Number(zoom)
  const height = Math.max(600, 800 / scale)
  useLayoutEffect(() => {
    const observer = new ResizeObserver(entries => setAvailableWidth(entries[0].contentRect.width))
    if (host.current) observer.observe(host.current)
    return () => observer.disconnect()
  }, [])
  useEffect(() => {
    setHtml(''); setError(''); setNotRendered(false)
    if (!snapshot) return
    const controller = new AbortController()
    const url = new URL(`/api/snapshots/${snapshot.snapshotId}/preview?targetWidth=${width}&v=${version}${rootId ? `&rootId=${encodeURIComponent(rootId)}` : ''}`, window.location.origin)
    const requestNonce = crypto.randomUUID()
    nonce.current = requestNonce
    fetch(url, { signal: controller.signal }).then(async response => {
      if (!response.ok) throw new Error(`Preview HTTP ${response.status}`)
      const text = await response.text()
      if (!controller.signal.aborted) setHtml(preparePreview(text, url.href, requestNonce))
    }).catch(caught => { if (!controller.signal.aborted) setError(caught.message) })
    return () => controller.abort()
  }, [snapshot, width, version, rootId])
  useEffect(() => {
    const receive = event => {
      if (event.source !== frame.current?.contentWindow || event.data?.nonce !== nonce.current) return
      if (event.data.type === 'layoutly-select' && nodeByAnchor.has(event.data.id)) onSelect(nodeByAnchor.get(event.data.id).id)
      if (event.data.type === 'layoutly-highlight-result') setNotRendered(event.data.id === null)
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
    <div className="we-canvas-tools"><div className="we-segment"><button className={`we-icon ${inspect ? 'is-active' : ''}`} type="button" title="Chọn node" aria-label="Chọn node" onClick={() => setInspect(true)}><MousePointer2 size={15} /></button><button className={`we-icon ${!inspect ? 'is-active' : ''}`} type="button" title="Tương tác với preview" aria-label="Tương tác với preview" onClick={() => setInspect(false)}><Play size={15} /></button></div><span>{width}px · {Math.round(scale * 100)}%</span>{snapshot && <a className="we-icon" href={`/preview/${snapshot.snapshotId}`} target="_blank" rel="noreferrer" title="Preview toàn màn hình" aria-label="Preview toàn màn hình"><ExternalLink size={15} /></a>}</div>
    {notRendered && <p className="we-canvas-notice">Node nằm ngoài root preview hiện tại. Thuộc tính vẫn xem được.</p>}
    <div className="we-canvas-scroll">{snapshot ? error ? <p className="we-error" role="alert">{error}</p> : html ? <div className="we-preview-frame" style={{ width: requestedWidth * scale, height: height * scale }}><iframe ref={frame} title="Snapshot preview" sandbox="allow-scripts" srcDoc={html} onLoad={syncFrame} style={{ width: requestedWidth, height, transform: `scale(${scale})` }} /></div> : <p className="we-panel-empty">Đang tải preview...</p> : <div className="we-canvas-empty"><MousePointer2 size={28} /><h2>Thiết kế của bạn ở đây</h2><p>Chưa có snapshot được nhập.</p></div>}</div>
  </section>
}
