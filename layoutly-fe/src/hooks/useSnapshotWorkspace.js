import { useCallback, useEffect, useRef, useState } from 'react'
import { downloadSnapshotExport, figmaApi, snapshotApi } from '../lib/api.js'
import { parseDesignTarget } from '../lib/workspace.js'

export default function useSnapshotWorkspace() {
  const [snapshot, setSnapshot] = useState(null)
  const [tree, setTree] = useState(null)
  const [code, setCode] = useState('')
  const [tab, setTab] = useState('HTML')
  const [busy, setBusy] = useState('')
  const [error, setError] = useState('')
  const [status, setStatus] = useState('')
  const [version, setVersion] = useState(0)
  const [token, setToken] = useState('')
  const [bridge, setBridge] = useState(null)
  const generation = useRef(0)
  const alive = useRef(true)
  const codeRequest = useRef(0)

  const accept = useCallback(data => {
    generation.current += 1
    setSnapshot(data); setTree(null); setCode(''); setTab('HTML'); setVersion(value => value + 1)
    sessionStorage.setItem('layoutly_snapshot_id', data.snapshotId)
    if (new URLSearchParams(window.location.search).has('snapshotId')) {
      window.history.replaceState(null, '', '/#workspace')
    }
  }, [])

  useEffect(() => {
    alive.current = true
    const id = new URLSearchParams(window.location.search).get('snapshotId') || sessionStorage.getItem('layoutly_snapshot_id')
    const epoch = generation.current
    if (id) {
      setBusy('restore')
      snapshotApi.get(id).then(response => { if (alive.current && epoch === generation.current) accept(response.json) })
        .catch(caught => { if (alive.current && epoch === generation.current) setError(caught.message) })
        .finally(() => { if (alive.current) setBusy('') })
    }
    snapshotApi.bridgeStatus().then(response => { if (alive.current) setBridge(response.json) }).catch(() => { if (alive.current) setBridge({ status: 'OFFLINE' }) })
    return () => { alive.current = false; generation.current += 1; codeRequest.current += 1 }
  }, [accept])

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
    snapshotApi[tab === 'JSON' ? 'tree' : tab.toLowerCase()](snapshot.snapshotId)
      .then(response => { if (active && requestId === codeRequest.current) setCode(tab === 'JSON' ? JSON.stringify(response.json, null, 2) : response.raw) })
      .catch(caught => { if (active) setError(caught.message) })
    return () => { active = false }
  }, [snapshot, version, tab])

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
    return operation('import', async () => (await snapshotApi.importFigma(token.trim(), { ...target, figmaUrl: input })).json)
  }
  async function openSnapshot(id) {
    if (!/^[a-zA-Z0-9-]+$/.test(id.trim())) { setError('Snapshot ID không hợp lệ.'); return false }
    return operation('open', async () => (await snapshotApi.get(id.trim())).json)
  }
  async function refresh() {
    if (!snapshot || !token.trim() || !window.confirm('Refresh sẽ thay thế snapshot và mã sinh ra. Tiếp tục?')) return
    await operation('refresh', async () => (await snapshotApi.refresh(token.trim(), snapshot.snapshotId)).json)
  }
  async function exportProject(format, type = 'AUTO') {
    if (!snapshot) return
    const success = await operation('export', () => downloadSnapshotExport(snapshot.snapshotId, format, type))
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
  return { snapshot, tree, code, tab, setTab, busy, error, status, version, token, setToken, bridge, importDesign, openSnapshot, refresh, exportProject, validateToken, saveFixture, refreshCode }
}
