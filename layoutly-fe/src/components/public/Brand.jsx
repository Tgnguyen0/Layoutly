import { Code2 } from 'lucide-react'

export default function Brand() {
  return (
    <a href="/" className="ly-brand" aria-label="Layoutly, trang chủ">
      <span className="ly-brand-mark"><Code2 size={24} strokeWidth={2} /></span>
      <span><strong>Layoutly</strong><small>FIGMA TO CODE</small></span>
    </a>
  )
}
