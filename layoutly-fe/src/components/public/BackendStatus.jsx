import { useEffect, useState } from 'react'
import { RefreshCw } from 'lucide-react'

export default function BackendStatus() {
  const [attempt, setAttempt] = useState(0)
  const [status, setStatus] = useState('checking')
  useEffect(() => {
    const controller = new AbortController()
    let active = true
    const timeout = setTimeout(() => controller.abort(), 8000)
    setStatus('checking')
    fetch('/api/bridge/status', { signal: controller.signal })
      .then(async (response) => {
        if (!response.ok || (await response.json()).status !== 'READY') throw new Error('Unavailable')
        if (active) setStatus('ready')
      })
      .catch(() => { if (active) setStatus('offline') })
      .finally(() => clearTimeout(timeout))
    return () => { active = false; controller.abort(); clearTimeout(timeout) }
  }, [attempt])
  return <div className={`ly-backend-status ly-backend-${status}`} role="status">
    <span className="ly-status-dot" />
    {status === 'ready' ? 'Figma Bridge sẵn sàng' : status === 'checking' ? 'Đang kiểm tra kết nối' : 'Backend chưa kết nối'}
    {status === 'offline' && <button type="button" className="ly-icon-button" title="Kiểm tra lại kết nối" aria-label="Kiểm tra lại kết nối" onClick={() => setAttempt(x => x + 1)}><RefreshCw size={15} /></button>}
  </div>
}
