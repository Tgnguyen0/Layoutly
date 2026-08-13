import React, { useState } from 'react'
import {
  ChevronRight,
  Layers,
  Component,
  Type as TypeIcon,
  PenTool,
  Square,
  Circle,
  Hexagon,
  FileStack,
  Combine,
  Minus,
  Box,
} from 'lucide-react'

// Mau + icon rieng cho tung loai node Figma, giup quet mat nhanh trong cay lon
const FIGMA_TYPE_META = {
  DOCUMENT: { icon: FileStack, color: 'text-blueprint' },
  CANVAS: { icon: Layers, color: 'text-cyan-accent' },
  FRAME: { icon: Square, color: 'text-cyan-accent' },
  GROUP: { icon: Layers, color: 'text-ink-300' },
  COMPONENT: { icon: Component, color: 'text-blueprint' },
  COMPONENT_SET: { icon: Component, color: 'text-blueprint-soft' },
  INSTANCE: { icon: Component, color: 'text-ink-300' },
  TEXT: { icon: TypeIcon, color: 'text-emerald-400' },
  VECTOR: { icon: PenTool, color: 'text-fuchsia-400' },
  RECTANGLE: { icon: Square, color: 'text-ink-300' },
  ELLIPSE: { icon: Circle, color: 'text-ink-300' },
  REGULAR_POLYGON: { icon: Hexagon, color: 'text-ink-300' },
  BOOLEAN_OPERATION: { icon: Combine, color: 'text-fuchsia-400' },
  LINE: { icon: Minus, color: 'text-ink-300' },
}

function isFigmaNode(value) {
  return (
    value &&
    typeof value === 'object' &&
    !Array.isArray(value) &&
    typeof value.type === 'string' &&
    FIGMA_TYPE_META[value.type]
  )
}

function valueLabel(value) {
  if (value === null) return { text: 'null', className: 'text-ink-500 italic' }
  if (typeof value === 'string') return { text: `"${value}"`, className: 'text-emerald-400' }
  if (typeof value === 'number') return { text: String(value), className: 'text-cyan-accent' }
  if (typeof value === 'boolean') return { text: String(value), className: 'text-blueprint' }
  return null
}

function EntryRow({ keyName, value, depth, defaultExpandedDepth }) {
  const isContainer =
    value !== null && typeof value === 'object' && (Array.isArray(value) ? value.length > 0 : Object.keys(value).length > 0)

  const [open, setOpen] = useState(depth < defaultExpandedDepth)

  if (!isContainer) {
    const label = valueLabel(value)
    return (
      <div
        className="flex items-baseline gap-2 py-[3px] pl-5 hover:bg-graphite-800/60 rounded"
        style={{ marginLeft: depth * 16 }}
      >
        {keyName !== null && <span className="text-ink-500 shrink-0">{keyName}:</span>}
        <span className={`font-mono text-[12.5px] break-all ${label?.className || 'text-ink-300'}`}>
          {label ? label.text : Array.isArray(value) ? '[]' : '{}'}
        </span>
      </div>
    )
  }

  const entries = Array.isArray(value) ? value.map((v, i) => [i, v]) : Object.entries(value)
  const figmaNode = isFigmaNode(value)
  const meta = figmaNode ? FIGMA_TYPE_META[value.type] : null
  const Icon = meta?.icon || Box

  const summary = Array.isArray(value)
    ? `${entries.length} item${entries.length !== 1 ? 's' : ''}`
    : `${entries.length} field${entries.length !== 1 ? 's' : ''}`

  return (
    <div>
      <button
        onClick={() => setOpen((o) => !o)}
        className="w-full flex items-center gap-1.5 py-[3px] pl-1 rounded hover:bg-graphite-800/60 text-left group"
        style={{ marginLeft: depth * 16 }}
      >
        <ChevronRight
          size={13}
          className={`shrink-0 text-ink-500 transition-transform duration-150 ${open ? 'rotate-90' : ''}`}
        />
        {figmaNode && <Icon size={13} className={`shrink-0 ${meta.color}`} />}
        {keyName !== null && (
          <span className="text-ink-500 font-mono text-[12.5px] shrink-0">{keyName}:</span>
        )}
        {figmaNode ? (
          <>
            <span className={`font-mono text-[11px] px-1.5 py-[1px] rounded border border-graphite-600 ${meta.color} shrink-0`}>
              {value.type}
            </span>
            <span className="font-body text-[12.5px] text-ink-100 truncate">{value.name}</span>
          </>
        ) : (
          <span className="font-mono text-[12px] text-ink-500 group-hover:text-ink-300">{summary}</span>
        )}
      </button>
      {open && (
        <div>
          {entries.map(([k, v]) => (
            <EntryRow
              key={k}
              keyName={typeof k === 'number' ? null : k}
              value={v}
              depth={depth + 1}
              defaultExpandedDepth={defaultExpandedDepth}
            />
          ))}
        </div>
      )}
    </div>
  )
}

export default function JsonTree({ data, defaultExpandedDepth = 2 }) {
  if (data === undefined) return null
  return (
    <div className="text-[12.5px] leading-relaxed">
      <EntryRow keyName={null} value={data} depth={0} defaultExpandedDepth={defaultExpandedDepth} />
    </div>
  )
}
