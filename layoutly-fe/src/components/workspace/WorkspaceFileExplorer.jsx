import { useMemo, useState } from 'react'
import { ChevronDown, ChevronRight, FileCode2, Folder, FolderOpen } from 'lucide-react'

export default function WorkspaceFileExplorer({ files, selected, onSelect, hasSnapshot }) {
  const [closed, setClosed] = useState(new Set())
  const tree = useMemo(() => {
    const root = { children: new Map() }
    for (const file of files) {
      let parent = root
      const segments = file.split('/')
      segments.forEach((name, index) => {
        if (!parent.children.has(name)) parent.children.set(name, { name, path: segments.slice(0, index + 1).join('/'), directory: index < segments.length - 1, children: new Map() })
        parent = parent.children.get(name)
      })
    }
    return root
  }, [files])
  function toggle(path) { setClosed(current => { const next = new Set(current); next.has(path) ? next.delete(path) : next.add(path); return next }) }
  function entries(parent, depth = 0) {
    return [...parent.children.values()].sort((a,b) => Number(b.directory) - Number(a.directory) || a.name.localeCompare(b.name)).map(entry => entry.directory
      ? <div key={entry.path}><button type="button" style={{ paddingLeft: 6 + depth * 12 }} aria-label={`Folder ${entry.path}`} aria-expanded={!closed.has(entry.path)} onClick={() => toggle(entry.path)}>{closed.has(entry.path) ? <ChevronRight size={11} /> : <ChevronDown size={11} />}{closed.has(entry.path) ? <Folder size={12} /> : <FolderOpen size={12} />}<span>{entry.name}</span></button>{!closed.has(entry.path) && entries(entry, depth + 1)}</div>
      : <button type="button" key={entry.path} style={{ paddingLeft: 21 + depth * 12 }} aria-label={entry.path} title={entry.path} className={selected === entry.path ? 'is-active' : ''} onClick={() => onSelect(entry.path)}><FileCode2 size={12} /><span>{entry.name}</span></button>)
  }
  return <nav className="we-file-explorer" aria-label="Project files"><h3>Explorer</h3>{files.length ? entries(tree) : <p className="we-panel-empty">{hasSnapshot ? 'Đang đọc files…' : 'Chưa có files.'}</p>}</nav>
}
