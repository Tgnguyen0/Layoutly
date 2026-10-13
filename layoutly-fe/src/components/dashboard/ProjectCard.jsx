import { MoreHorizontal } from 'lucide-react'
import { useLayoutEffect, useRef, useState } from 'react'
import { snapshotDate } from '../../lib/workspace.js'

export default function ProjectCard({ snapshot }) {
  const [menu, setMenu] = useState(false)
  const [scale, setScale] = useState(.17)
  const thumbnail = useRef(null)
  useLayoutEffect(() => {
    const observer = new ResizeObserver(entries => setScale(entries[0].contentRect.width / 1440))
    if (thumbnail.current) observer.observe(thumbnail.current)
    return () => observer.disconnect()
  }, [])
  const url = `/?snapshotId=${encodeURIComponent(snapshot.snapshotId)}`
  return <article className="db-card"><a ref={thumbnail} className="db-thumbnail" href={url} aria-label={`Mở ${snapshot.fileName}`}><iframe title={`Thumbnail ${snapshot.fileName}`} sandbox="allow-scripts" src={`/api/snapshots/${snapshot.snapshotId}/preview?targetWidth=1440`} style={{ transform: `scale(${scale})` }} tabIndex={-1} loading="lazy" /></a><div className="db-card-content"><div><a href={url}><h2>{snapshot.fileName}</h2></a><button type="button" className="we-icon" title="Menu snapshot" aria-label="Menu snapshot" aria-expanded={menu} onClick={() => setMenu(!menu)}><MoreHorizontal size={16} /></button></div><span className="db-format">HTML / React</span><p>{snapshotDate(snapshot.fetchedAt)}</p>{menu && <div className="db-card-menu"><a href={url}>Mở snapshot</a><a href={`/preview/${snapshot.snapshotId}`} target="_blank" rel="noreferrer">Mở preview</a><button type="button" disabled>Đổi tên · chưa khả dụng</button><button type="button" disabled>Xóa · chưa khả dụng</button></div>}</div></article>
}
