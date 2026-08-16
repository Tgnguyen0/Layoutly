import React from 'react'
import { KeyRound, FileSearch, Boxes, Palette, Image as ImageIcon, ScanSearch, GitBranch, Code2, PackageCheck } from 'lucide-react'

const FIELD_CLASS =
  'w-full bg-graphite-900 border border-graphite-600 rounded-md px-3 py-2 text-[13px] font-mono text-ink-100 placeholder:text-ink-500 focus:outline-none focus:border-blueprint/70 focus:ring-1 focus:ring-blueprint/30 transition-colors'

function ActionButton({ icon: Icon, label, onClick, disabled, primary }) {
  return (
    <button
      onClick={onClick}
      disabled={disabled}
      className={`flex items-center gap-2 px-3 py-2 rounded-md text-[12.5px] font-medium transition-colors disabled:opacity-40 disabled:cursor-not-allowed
        ${primary
          ? 'bg-blueprint text-graphite-950 hover:bg-blueprint-soft'
          : 'bg-graphite-800 text-ink-100 border border-graphite-600 hover:border-blueprint/50 hover:text-blueprint'
        }`}
    >
      <Icon size={14} />
      {label}
    </button>
  )
}

export default function InputPanel({
  token, setToken,
  fileKey, setFileKey,
  nodeIds, setNodeIds,
  format, setFormat,
  onCall,
  loading,
}) {
  return (
    <div className="flex flex-col gap-5">
      <div>
        <label className="flex items-center gap-1.5 text-[11px] uppercase tracking-wider text-ink-500 mb-1.5">
          <KeyRound size={12} /> Figma Access Token
        </label>
        <input
          type="password"
          value={token}
          onChange={(e) => setToken(e.target.value)}
          placeholder="figd_xxxxxxxxxxxxxxxxxxxxx"
          className={FIELD_CLASS}
        />
        <p className="text-[11px] text-ink-500 mt-1.5 leading-relaxed">
          Settings → Personal access tokens trong Figma. Chỉ lưu trong trình duyệt của bạn.
        </p>
      </div>

      <div>
        <label className="text-[11px] uppercase tracking-wider text-ink-500 mb-1.5 block">
          File Key
        </label>
        <input
          value={fileKey}
          onChange={(e) => setFileKey(e.target.value)}
          placeholder="8BaX6AqXi4YPyBKZGVghBO"
          className={FIELD_CLASS}
        />
        <p className="text-[11px] text-ink-500 mt-1.5">
          figma.com/design/<span className="text-blueprint">fileKey</span>/...
        </p>
      </div>

      <div>
        <label className="text-[11px] uppercase tracking-wider text-ink-500 mb-1.5 block">
          Node IDs <span className="text-ink-500/70 normal-case">(cho nodes / ảnh)</span>
        </label>
        <input
          value={nodeIds}
          onChange={(e) => setNodeIds(e.target.value)}
          placeholder="0:1,1:23"
          className={FIELD_CLASS}
        />
        <p className="text-[11px] text-ink-500 mt-1.5">
          Đổi "-" thành ":" — node-id=0-1 trên URL → nhập 0:1
        </p>
      </div>

      <div>
        <label className="text-[11px] uppercase tracking-wider text-ink-500 mb-1.5 block">
          Format ảnh
        </label>
        <select
          value={format}
          onChange={(e) => setFormat(e.target.value)}
          className={FIELD_CLASS}
        >
          <option value="png">png</option>
          <option value="svg">svg</option>
          <option value="pdf">pdf</option>
          <option value="jpg">jpg</option>
        </select>
      </div>

      <div className="pt-1 border-t border-graphite-700" />

      <div className="grid grid-cols-2 gap-2">
        <ActionButton icon={ScanSearch} label="Test token" onClick={() => onCall('me')} disabled={loading} />
        <ActionButton icon={FileSearch} label="Xem file" onClick={() => onCall('file')} disabled={loading} primary />
        <ActionButton icon={Boxes} label="Xem nodes" onClick={() => onCall('nodes')} disabled={loading} />
        <ActionButton icon={ImageIcon} label="Xuất ảnh" onClick={() => onCall('images')} disabled={loading} />
        <ActionButton icon={Boxes} label="Components" onClick={() => onCall('components')} disabled={loading} />
        <ActionButton icon={Palette} label="Styles" onClick={() => onCall('styles')} disabled={loading} />
        <ActionButton icon={GitBranch} label="Xem cây UI" onClick={() => onCall('tree')} disabled={loading} />
        <ActionButton icon={Code2} label="Xem HTML" onClick={() => onCall('html')} disabled={loading} />
      </div>

      <div className="pt-1 border-t border-graphite-700" />

      <ActionButton icon={PackageCheck} label="Tải mã nguồn (ZIP)" onClick={() => onCall('export')} disabled={loading} primary />
    </div>
  )
}
