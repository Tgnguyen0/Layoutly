import { MoreHorizontal, Folder } from 'lucide-react'
import { useLayoutEffect, useRef, useState } from 'react'
import { snapshotDate } from '../../lib/workspace.js'
export default function ProjectCard({ project, onAction, busy }) {
  const [menu, setMenu] = useState(false)
  const [scale, setScale] = useState(.17)
  const thumbnail = useRef(null)
  useLayoutEffect(() => {
    const observer = new ResizeObserver(entries => setScale(entries[0].contentRect.width / 1440))
    if (thumbnail.current) observer.observe(thumbnail.current)
    return () => observer.disconnect()
  }, [])
  const url = '/?projectId=' + encodeURIComponent(project.id) + '#/workspace'
  return <article className="db-card">
    <a ref={thumbnail} className="db-thumbnail" href={url} aria-label={'Mở ' + project.name}>
      {project.snapshotId ? <iframe title={'Thumbnail ' + project.name} sandbox="allow-scripts" src={'/api/snapshots/' + project.snapshotId + '/preview?targetWidth=1440'} style={{ transform: 'scale(' + scale + ')' }} tabIndex={-1} loading="lazy" /> : <Folder size={40} />}
    </a>
    <div className="db-card-content"><div><a href={url}><h2>{project.name}</h2></a><button type="button" className="we-icon" title="Menu dự án" aria-label={'Menu ' + project.name} aria-expanded={menu} onClick={() => setMenu(!menu)}><MoreHorizontal size={16} /></button></div>
      <span className="db-format">{project.framework === 'HTML' ? 'HTML/CSS' : 'React'}</span><p>{snapshotDate(project.updatedAt)}</p>
      {menu && <div className="db-card-menu"><a href={url}>Mở dự án</a>{[['rename', 'Đổi tên'], ['edit', 'Sửa mô tả'], ['duplicate', 'Nhân bản'], ['history', 'Lịch sử xuất'], ['delete', 'Xóa dự án']].map(([action, label]) => <button key={action} type="button" disabled={busy} onClick={() => { setMenu(false); onAction(action, project) }}>{label}</button>)}</div>}
    </div>
  </article>
}
