import { useEffect, useState } from 'react'
import { FilePlus2, Menu, Plus, Search } from 'lucide-react'
import DashboardSidebar from '../components/dashboard/DashboardSidebar.jsx'
import ProjectCard from '../components/dashboard/ProjectCard.jsx'
import { snapshotApi } from '../lib/api.js'
import '../styles/editor.css'

export default function DashboardPage() {
  const [snapshot, setSnapshot] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [query, setQuery] = useState('')
  const [filter, setFilter] = useState('ALL')
  const [sort, setSort] = useState('newest')
  const [drawer, setDrawer] = useState(false)
  useEffect(() => {
    let active = true
    const id = sessionStorage.getItem('layoutly_snapshot_id')
    if (!id) { setLoading(false); return }
    snapshotApi.get(id).then(response => { if (active) setSnapshot(response.json) }).catch(caught => { if (active) setError(caught.message) }).finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [])
  useEffect(() => {
    if (!drawer) return
    const previous = document.activeElement
    const panel = document.querySelector('.db-sidebar')
    const focusable = () => [...panel.querySelectorAll('a[href],button:not(:disabled)')].filter(element => element.getClientRects().length)
    focusable()[0]?.focus()
    const close = event => {
      if (event.key === 'Escape') setDrawer(false)
      if (event.key === 'Tab') {
        const items = focusable()
        const first = items[0], last = items.at(-1)
        if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
        if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
      }
    }
    document.addEventListener('keydown', close)
    return () => { document.removeEventListener('keydown', close); previous?.focus?.() }
  }, [drawer])
  const count = snapshot ? 1 : 0
  const projects = snapshot && filter !== 'SHARED' && snapshot.fileName.toLowerCase().includes(query.toLowerCase()) ? [snapshot] : []
  projects.sort((a, b) => sort === 'name' ? a.fileName.localeCompare(b.fileName, 'vi') : (sort === 'oldest' ? 1 : -1) * (new Date(a.fetchedAt) - new Date(b.fetchedAt)))
  return <main id="public-main" className={`dashboard-page ${drawer ? 'db-drawer-open' : ''}`}>
    {drawer && <button className="db-backdrop" type="button" aria-label="Đóng sidebar" onClick={() => setDrawer(false)} />}
    <DashboardSidebar onClose={() => setDrawer(false)} />
    <section className="db-main"><div className="db-heading"><div><button type="button" className="we-icon db-menu" aria-label="Mở sidebar" onClick={() => setDrawer(true)}><Menu size={20} /></button><h1>Dự án của tôi</h1><p>Quản lý và tiếp tục với thiết kế đã nhập.</p></div><label className="db-search"><Search size={16} /><input placeholder="Tìm dự án, thư mục..." aria-label="Tìm dự án" value={query} onChange={e => setQuery(e.target.value)} /></label><a className="ly-button ly-button-primary" href="/#/workspace?import=1"><Plus size={17} />Tạo dự án mới</a></div>
      <div className="db-filters"><div className="we-segment">{[['ALL', `Tất cả (${count})`], ['REACT', `React (${count})`], ['HTML', `HTML/CSS (${count})`], ['SHARED', 'Đã chia sẻ (0)']].map(([value, label]) => <button type="button" key={value} className={filter === value ? 'is-active' : ''} onClick={() => setFilter(value)}>{label}</button>)}</div><select aria-label="Sắp xếp dự án" value={sort} onChange={e => setSort(e.target.value)}><option value="newest">Mới nhất</option><option value="oldest">Cũ nhất</option><option value="name">Tên A–Z</option></select></div>
      <p className="db-local-notice">Snapshot cục bộ trong phiên này. Lưu dự án theo tài khoản chưa khả dụng; một snapshot có thể xuất cả HTML và React.</p>
      {error && <p className="we-error" role="alert">{error}</p>}
      {loading ? <p className="we-panel-empty">Đang tải snapshot...</p> : projects.length ? <div className="db-grid">{projects.map(project => <ProjectCard key={project.snapshotId} snapshot={project} />)}<a className="db-new-card" href="/#/workspace?import=1"><Plus size={26} /><strong>Tạo dự án mới</strong><span>Nhập Figma URL hoặc dùng Bridge từ plugin</span></a></div> : <div className="db-empty"><FilePlus2 size={42} /><h2>{count ? 'Không tìm thấy dự án' : 'Bắt đầu với thiết kế đầu tiên'}</h2><p>{count ? 'Thử một từ khóa hoặc bộ lọc khác.' : 'Chưa có snapshot trong phiên làm việc này.'}</p><a className="ly-button ly-button-primary" href="/#/workspace?import=1"><Plus size={16} />Import Figma</a></div>}
    </section>
  </main>
}
