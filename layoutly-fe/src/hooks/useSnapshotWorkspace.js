import { useCallback, useEffect, useRef, useState } from 'react'
import { downloadAiExport, downloadSnapshotExport, figmaApi, snapshotApi } from '../lib/api.js'
import { parseDesignTarget } from '../lib/workspace.js'
import { projectApi } from '../lib/projects.js'

export default function useSnapshotWorkspace() {
  const [snapshot, setSnapshot] = useState(null)
  const [project, setProject] = useState(null)
  const projectRef = useRef(null)
  function rememberProject(value) {
    projectRef.current = value
    setProject(value)
    sessionStorage.setItem('layoutly_project_id', value.id)
  }
  const [tree, setTree] = useState(null)
  const [code, setCode] = useState('')
  const [tab, setTab] = useState('HTML')
  const [busy, setBusy] = useState('')
  const [error, setError] = useState('')
  const [status, setStatus] = useState('')
  const [version, setVersion] = useState(0)
  const [token, setToken] = useState('')
  const [bridge, setBridge] = useState(null)
  const [acceptedRevision, setAcceptedRevision] = useState(null)
  const [viewRevision, setViewRevision] = useState(null)
  const [files, setFiles] = useState([])
  const [selectedFile, setSelectedFile] = useState('')
  const [comparisonDraft, setComparisonDraft] = useState(null)
  const generation = useRef(0)
  const alive = useRef(true)
  const codeRequest = useRef(0)
  const linkedRevision = useRef(new URLSearchParams(window.location.search))

  const accept = useCallback(data => {
    generation.current += 1
    setSnapshot(data); setTree(null); setCode(''); setTab('HTML'); setVersion(value => value + 1)
    setAcceptedRevision(null); setViewRevision(null); setFiles([]); setSelectedFile(''); setComparisonDraft(null)
    sessionStorage.setItem('layoutly_snapshot_id', data.snapshotId)
  }, [])

  useEffect(() => {
    alive.current = true
    const parameters = new URLSearchParams(window.location.search)
    const fresh = new URLSearchParams(window.location.hash.split('?')[1]).get('import') === '1'
    const projectId = parameters.get('projectId') || (!fresh && !parameters.has('snapshotId') ? sessionStorage.getItem('layoutly_project_id') : null)
    const id = parameters.get('snapshotId') || (!fresh ? sessionStorage.getItem('layoutly_snapshot_id') : null)
    const epoch = generation.current
    if (projectId || id) {
      setBusy('restore')
      const restore = projectId ? projectApi.reopen(projectId).then(result => {
        if (!alive.current || epoch !== generation.current) return null
        rememberProject(result.project)
        return result.project.snapshotId ? snapshotApi.get(result.project.snapshotId) : null
      }) : snapshotApi.get(id)
      restore.then(response => { if (response && alive.current && epoch === generation.current) accept(response.json) })
        .catch(caught => { if (alive.current && epoch === generation.current) setError(caught.message) })
        .finally(() => { if (alive.current) setBusy('') })
    }
    snapshotApi.bridgeStatus().then(response => { if (alive.current) setBridge(response.json) }).catch(() => { if (alive.current) setBridge({ status: 'OFFLINE' }) })
    return () => { alive.current = false; generation.current += 1; codeRequest.current += 1 }
  }, [accept])

  useEffect(() => {
    if (!snapshot || !snapshotApi.aiRevisions) return
    let active = true
    Promise.all([snapshotApi.aiRevisions(snapshot.snapshotId), snapshotApi.aiAccepted ? snapshotApi.aiAccepted(snapshot.snapshotId) : Promise.resolve({ json: null })]).then(([response, current]) => {
      if (!active) return
      const accepted = current.json?.revisionId && current.json?.status === 'ACCEPTED' ? current.json : (response.json || []).filter(item => item.status === 'ACCEPTED').at(-1)
      if (accepted) setAcceptedRevision(accepted)
      const requestedId = linkedRevision.current.get('revisionId')
      if (requestedId) {
        setViewRevision({ revisionId: requestedId, targetFramework: linkedRevision.current.get('framework') || 'REACT', status: 'PREVIEW' })
        linkedRevision.current.delete('revisionId')
      } else if (accepted) setViewRevision(accepted)
    }).catch(caught => { if (active) setError(caught.message) })
    return () => { active = false }
  }, [snapshot])

  useEffect(() => {
    if (!snapshot || !snapshotApi.aiFiles) return
    let active = true
    setFiles([])
    snapshotApi.aiFiles(snapshot.snapshotId, viewRevision?.revisionId, viewRevision?.targetFramework || 'REACT')
      .then(response => { if (active) setFiles(response.json || []) })
      .catch(caught => { if (active) setError(caught.message) })
    return () => { active = false }
  }, [snapshot, viewRevision, version])

  useEffect(() => {
    if (!snapshot) return
    let active = true
    snapshotApi.tree(snapshot.snapshotId).then(response => { if (active) setTree(response.json) }).catch(caught => { if (active) setError(caught.message) })
    return () => { active = false }
  }, [snapshot, version])

  useEffect(() => {
    if (!snapshot) return
    let active = true
    const requestId = ++codeRequest.current
    setCode('')
    const source = viewRevision && tab !== 'JSON'
      ? (selectedFile || files.find(file => tab === 'CSS' ? file.endsWith('.css') : tab === 'HTML' ? file.endsWith('.html') : file === 'src/App.jsx') || files.find(file => /\.(jsx?|tsx?)$/.test(file)))
      : selectedFile
    const loading = source && tab !== 'JSON' ? snapshotApi.aiFile(snapshot.snapshotId, source, viewRevision?.revisionId, viewRevision?.targetFramework || 'REACT')
      : viewRevision && tab !== 'JSON' ? Promise.resolve({ raw: files.length ? 'Không có file phù hợp trong revision này.' : 'Đang tải files của revision…' })
      : snapshotApi[tab === 'JSON' ? 'tree' : tab.toLowerCase()](snapshot.snapshotId)
    loading
      .then(response => { if (active && requestId === codeRequest.current) setCode(tab === 'JSON' ? JSON.stringify(response.json, null, 2) : response.raw) })
      .catch(caught => { if (active) setError(caught.message) })
    return () => { active = false }
  }, [snapshot, version, tab, viewRevision, selectedFile, files])

  async function operation(name, callback) {
    if (busy) return false
    const epoch = ++generation.current
    setBusy(name); setError(''); setStatus('')
    try {
      const result = await callback()
      if (!alive.current || epoch !== generation.current) return false
      if (result?.snapshotId) accept(result)
      return true
    } catch (caught) {
      if (alive.current && epoch === generation.current) setError(caught.message)
      return false
    } finally { if (alive.current) setBusy('') }
  }

  async function importDesign(input) {
    const target = parseDesignTarget(input)
    if (!target.fileKey || !token.trim()) { setError('Nhập URL Figma hợp lệ và personal access token.'); return false }
    return operation('import', async () => persistSnapshot((await snapshotApi.importFigma(token.trim(), { ...target, figmaUrl: input })).json))
  }
  async function persistSnapshot(data) {
    const current = projectRef.current
    const result = current
      ? await projectApi.update(current.id, { snapshotId: data.snapshotId, version: current.version })
      : await projectApi.create({ name: (data.fileName || 'Thiết kế mới').slice(0, 160), snapshotId: data.snapshotId, framework: 'REACT' })
    rememberProject(result.project)
    window.history.replaceState(null, '', '/?projectId=' + result.project.id + '#/workspace')
    return data
  }
  async function openSnapshot(id) {
    if (!/^[a-zA-Z0-9-]+$/.test(id.trim())) { setError('Snapshot ID không hợp lệ.'); return false }
    return operation('open', async () => persistSnapshot((await snapshotApi.get(id.trim())).json))
  }
  async function refresh() {
    if (!snapshot || !token.trim() || !window.confirm('Refresh sẽ thay thế snapshot và mã sinh ra. Tiếp tục?')) return
    await operation('refresh', async () => persistSnapshot((await snapshotApi.refresh(token.trim(), snapshot.snapshotId)).json))
  }
  async function saveProject() {
    if (!snapshot) return
    const success = await operation('save', async () => {
      if (!projectRef.current) await persistSnapshot(snapshot)
      const result = await projectApi.saveVersion(projectRef.current.id, acceptedRevision?.revisionId)
      rememberProject(result.project)
    })
    if (success) setStatus('Đã lưu dự án và phiên bản hiện tại.')
  }
  async function exportProject(format, type = 'AUTO') {
    if (!snapshot) return
    const success = await operation('export', async () => {
      if (!projectRef.current) await persistSnapshot(snapshot)
      return acceptedRevision
        ? downloadAiExport(snapshot.snapshotId, acceptedRevision.revisionId, acceptedRevision.targetFramework)
        : downloadSnapshotExport(snapshot.snapshotId, format, type)
    })
    if (success) setStatus(`Đã tải ZIP ${format}.`)
  }
  async function validateToken() {
    if (!token.trim()) { setError('Nhập token trước khi kiểm tra.'); return }
    const success = await operation('token', () => figmaApi.me(token.trim()))
    if (success) setStatus('Figma đã xác nhận token hợp lệ.')
  }
  async function saveFixture() {
    if (!snapshot) return
    const name = window.prompt('Tên fixture (không ghi đè fixture đang có):')
    if (!name) return
    const success = await operation('fixture', () => snapshotApi.saveFixture(snapshot.snapshotId, name))
    if (success) setStatus('Backend đã lưu fixture.')
  }
  const refreshCode = useCallback(() => setVersion(v => v + 1), [])
  function changeTab(next) { setTab(next); setSelectedFile('') }
  function showRevision(revision) { setSelectedFile(''); setViewRevision(revision); if (revision?.status === 'DRAFT') setComparisonDraft(revision); setVersion(v => v + 1) }
  function acceptRevision(revision) { setAcceptedRevision(revision); showRevision(revision) }
  function rejectRevisionView(revision) { if (viewRevision?.revisionId === revision.revisionId) showRevision(acceptedRevision); if (comparisonDraft?.revisionId === revision.revisionId) setComparisonDraft(null) }
  function selectFile(file) { setTab(file.endsWith('.css') ? 'CSS' : file.endsWith('.html') ? 'HTML' : 'REACT'); setSelectedFile(file) }
  return { project, saveProject, snapshot, tree, code, tab, setTab: changeTab, busy, error, status, version, token, setToken, bridge, importDesign, openSnapshot, refresh, exportProject, validateToken, saveFixture, refreshCode, files, selectedFile, setSelectedFile: selectFile, acceptedRevision, viewRevision, setViewRevision: showRevision, acceptRevision, rejectRevisionView, comparisonDraft }
}
