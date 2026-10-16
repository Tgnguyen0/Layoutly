import { useEffect, useState } from 'react'
import { FilePlus2, Menu, Plus, Search, X } from 'lucide-react'
import DashboardSidebar from '../components/dashboard/DashboardSidebar.jsx'
import ProjectCard from '../components/dashboard/ProjectCard.jsx'
import { projectApi } from '../lib/projects.js'
import { initials, useAccount } from '../contexts/AccountContext.jsx'
import '../styles/editor.css'

export default function DashboardPage() {
  const { user } = useAccount()
  const [all, setAll] = useState([])
  const [projects, setProjects] = useState([])
  const [loading, setLoading] = useState(true)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [query, setQuery] = useState('')
  const [filter, setFilter] = useState('ALL')
  const [sort, setSort] = useState('newest')
  const [drawer, setDrawer] = useState(false)
  const [refresh, setRefresh] = useState(0)
  const [history, setHistory] = useState(null)
  useEffect(() => {
    let active = true
    setLoading(true); setError('')
    const timer = setTimeout(() => {
      Promise.all([projectApi.list(query, filter === 'ALL' || filter === 'SHARED' ? '' : filter), projectApi.list()])
        .then(([result, total]) => { if (active) { setProjects(filter === 'SHARED' ? [] : result.projects); setAll(total.projects) } })
        .catch(caught => { if (active) setError(caught.message) }).finally(() => { if (active) setLoading(false) })
    }, 200)
    return () => { active = false; clearTimeout(timer) }
  }, [query, filter, refresh, user.id])
  useEffect(() => {
    if (!drawer) return
    const previous = document.activeElement
    const panel = document.querySelector('.db-sidebar')
    const focusable = () => [...panel.querySelectorAll('a[href],button:not(:disabled)')].filter(element => element.getClientRects().length)
    focusable()[0]?.focus()
    const close = event => {
      if (event.key === 'Escape') setDrawer(false)
      if (event.key === 'Tab') {
        const items = focusable(), first = items[0], last = items.at(-1)
        if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus() }
        if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus() }
      }
    }
    document.addEventListener('keydown', close)
    return () => { document.removeEventListener('keydown', close); previous?.focus?.() }
  }, [drawer])
  async function create() {
    const name = window.prompt('Tên dự án mới:', 'Thiết kế mới')
    if (!name?.trim() || busy) return
    setBusy(true); setError('')
    try {
      const { project } = await projectApi.create({ name: name.trim(), framework: 'REACT' })
      window.location.href = '/?projectId=' + project.id + '#/workspace?import=1'
    } catch (caught) { setError(caught.message) } finally { setBusy(false) }
  }
  async function action(kind, project) {
    if (busy) return
    let body
    if (kind === 'rename') {
      const name = window.prompt('Tên dự án:', project.name)
      if (!name?.trim()) return
      body = { name: name.trim(), version: project.version }
    }
    if (kind === 'edit') {
      const description = window.prompt('Mô tả dự án:', project.description)
      if (description === null) return
      body = { description, version: project.version }
    }
    if (kind === 'delete' && !window.confirm('Xóa dự án “' + project.name + '” khỏi dashboard?')) return
    setBusy(true); setError('')
    try {
      if (body) await projectApi.update(project.id, body)
      else if (kind === 'delete') await projectApi.remove(project.id)
      else if (kind === 'duplicate') await projectApi.duplicate(project.id)
      else if (kind === 'history') {
        const result = await projectApi.exports(project.id)
        setHistory({ name: project.name, items: result.exports })
      }
      setRefresh(value => value + 1)
    } catch (caught) { setError(caught.message) } finally { setBusy(false) }
  }
  const sorted = [...projects].sort((a, b) => sort === 'name' ? a.name.localeCompare(b.name, 'vi') : (sort === 'oldest' ? 1 : -1) * (new Date(a.updatedAt) - new Date(b.updatedAt)))
  return <main id="public-main" className={'dashboard-page ' + (drawer ? 'db-drawer-open' : '')}>
    {drawer && <button className="db-backdrop" type="button" aria-label="Đóng sidebar" onClick={() => setDrawer(false)} />}
    <DashboardSidebar onClose={() => setDrawer(false)} />
    <section className="db-main"><div className="db-topbar"><a href="/">Không gian làm việc / <strong>Dự án</strong></a><a className="db-topbar-account" href="/#/account"><span className="ac-avatar">{initials(user.displayName)}</span><span>Tài khoản của tôi</span></a></div>
      <div className="db-heading"><div><button type="button" className="we-icon db-menu" aria-label="Mở sidebar" onClick={() => setDrawer(true)}><Menu size={20} /></button><span className="db-welcome">{'Xin chào, ' + user.displayName.split(' ').at(-1)}</span><h1>Dự án của tôi</h1><p>Tiếp tục từ thiết kế gần nhất, hoặc bắt đầu một ý tưởng mới.</p></div><label className="db-search"><Search size={16} /><input placeholder="Tìm theo tên thiết kế…" aria-label="Tìm dự án" value={query} onChange={e => setQuery(e.target.value)} /></label><button className="ly-button ly-button-primary" type="button" disabled={busy} onClick={create}><Plus size={17} />Tạo dự án mới</button></div>
      <div className="db-filters"><div className="we-segment">{[['ALL', 'Tất cả (' + all.length + ')'], ['REACT', 'React (' + all.filter(p => p.framework === 'REACT').length + ')'], ['HTML', 'HTML/CSS (' + all.filter(p => p.framework === 'HTML').length + ')'], ['SHARED', 'Đã chia sẻ (0)']].map(([value, label]) => <button type="button" key={value} className={filter === value ? 'is-active' : ''} onClick={() => setFilter(value)}>{label}</button>)}</div><select aria-label="Sắp xếp dự án" value={sort} onChange={e => setSort(e.target.value)}><option value="newest">Mới nhất</option><option value="oldest">Cũ nhất</option><option value="name">Tên A–Z</option></select></div>
      <p className="db-local-notice">Dự án được lưu trong tài khoản của bạn · Mở lại thiết kế, phiên bản và lịch sử xuất bất cứ lúc nào.</p>
      {error && <p className="we-error" role="alert">{error}</p>}
      {loading ? <p className="we-panel-empty" role="status">Đang tải dự án…</p> : sorted.length ? <div className="db-grid">{sorted.map(project => <ProjectCard key={project.id} project={project} onAction={action} busy={busy} />)}<button className="db-new-card" type="button" disabled={busy} onClick={create}><Plus size={26} /><strong>Tạo dự án mới</strong><span>Nhập Figma URL hoặc dùng Bridge từ plugin</span></button></div> : <div className="db-empty"><FilePlus2 size={42} /><h2>{all.length ? 'Không tìm thấy dự án' : 'Bắt đầu với thiết kế đầu tiên'}</h2><p>{all.length ? 'Thử một từ khóa hoặc bộ lọc khác.' : 'Tạo dự án và import thiết kế Figma của bạn.'}</p><button className="ly-button ly-button-primary" type="button" disabled={busy} onClick={create}><Plus size={16} />Tạo dự án</button></div>}
      {history && <section className="ly-form-notice" aria-label="Lịch sử xuất"><button className="we-icon" aria-label="Đóng lịch sử" onClick={() => setHistory(null)}><X size={18} /></button><h2>{'Lịch sử xuất · ' + history.name}</h2>{history.items.length ? <ul>{history.items.map(item => <li key={item.id}>{item.format} ZIP · {new Date(item.createdAt).toLocaleString('vi-VN')} · {item.byteSize.toLocaleString('vi-VN')} bytes{item.revisionId ? ' · ' + item.revisionId : ''}</li>)}</ul> : <p>Chưa có lần xuất nào.</p>}</section>}
    </section>
  </main>
}
