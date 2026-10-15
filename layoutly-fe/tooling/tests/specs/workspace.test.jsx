import { act, renderHook, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flattenNodes, nodeAnchor, parseDesignTarget } from '../../../src/lib/workspace.js'
import { preparePreview } from '../../../src/components/workspace/PreviewCanvas.jsx'
import useSnapshotWorkspace from '../../../src/hooks/useSnapshotWorkspace.js'
import { snapshotApi } from '../../../src/lib/api.js'

vi.mock('../../../src/lib/api.js', () => ({ snapshotApi: { bridgeStatus: vi.fn(), get: vi.fn(), tree: vi.fn(), html: vi.fn(), css: vi.fn(), react: vi.fn() }, figmaApi: { me: vi.fn() }, downloadSnapshotExport: vi.fn() }))
beforeEach(() => {
  sessionStorage.clear()
  window.history.replaceState(null, '', '/')
  snapshotApi.bridgeStatus.mockResolvedValue({ json: { status: 'READY' } })
})

describe('workspace data and isolated preview', () => {
  it('parses selected Figma URL and file key without accepting unrelated URLs', () => {
    expect(parseDesignTarget('https://www.figma.com/design/abc123/title?node-id=2-3')).toEqual({ fileKey: 'abc123', nodeId: '2:3' })
    expect(parseDesignTarget('abc123')).toEqual({ fileKey: 'abc123', nodeId: '' })
    expect(parseDesignTarget('https://evil.test/design/private')).toEqual({ fileKey: '', nodeId: '' })
    expect(parseDesignTarget('')).toEqual({ fileKey: '', nodeId: '' })
  })
  it('keeps tree identity/depth and matches the backend anchor convention', () => {
    const leaf = { id: '2:3', name: 'Button' }
    const root = { id: '0:1', children: [leaf] }
    expect(flattenNodes(root)).toEqual([{ node: root, depth: 0 }, { node: leaf, depth: 1 }])
    expect(flattenNodes(null)).toEqual([])
    expect(nodeAnchor('I2:3;4:5')).toBe('node-i2-3-4-5')
  })
  it('replaces untrusted base and adds only a scoped selection bridge', () => {
    const result = preparePreview('<html><head><base href="https://wrong.test/"></head><body><button class="figma-node" id="node-2-3">Hello</button></body></html>', 'http://localhost/api/snapshots/id/preview', 'test-nonce')
    const document = new DOMParser().parseFromString(result, 'text/html')
    expect(document.querySelectorAll('base')).toHaveLength(1)
    expect(document.querySelector('base').href).toBe('http://localhost/api/snapshots/id/preview')
    expect(document.querySelector('#node-2-3').textContent).toBe('Hello')
    expect(document.querySelector('script').textContent).toContain('e.source!==parent')
    expect(document.querySelector('script').textContent).toContain('test-nonce')
  })
  it('restores a real snapshot and ignores an old code-tab response', async () => {
    sessionStorage.setItem('layoutly_snapshot_id', 'real-id')
    snapshotApi.get.mockResolvedValue({ json: { snapshotId: 'real-id', fileName: 'Real file' } })
    snapshotApi.tree.mockResolvedValue({ json: { id: '0:1', children: [] } })
    let finishHTML
    snapshotApi.html.mockImplementation(() => new Promise(resolve => { finishHTML = resolve }))
    snapshotApi.css.mockResolvedValue({ raw: '.actual { color: red; }' })
    const { result } = renderHook(() => useSnapshotWorkspace())
    await waitFor(() => expect(snapshotApi.html).toHaveBeenCalled())
    act(() => result.current.setTab('CSS'))
    await waitFor(() => expect(result.current.code).toBe('.actual { color: red; }'))
    await act(async () => finishHTML({ raw: 'STALE HTML' }))
    expect(result.current.code).toBe('.actual { color: red; }')
    expect(sessionStorage.getItem('layoutly_snapshot_id')).toBe('real-id')
  })
  it('does not republish a restore after unmount', async () => {
    sessionStorage.setItem('layoutly_snapshot_id', 'pending-id')
    let finish
    snapshotApi.get.mockImplementation(() => new Promise(resolve => { finish = resolve }))
    const { unmount } = renderHook(() => useSnapshotWorkspace())
    unmount()
    await act(async () => finish({ json: { snapshotId: 'new-id' } }))
    expect(sessionStorage.getItem('layoutly_snapshot_id')).toBe('pending-id')
  })
})
