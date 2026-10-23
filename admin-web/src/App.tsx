import { useEffect, useState } from 'react'

type HealthState = 'checking' | 'up' | 'down'

const LABELS: Record<HealthState, string> = {
  checking: '확인 중',
  up: '정상',
  down: '연결 실패',
}

export default function App() {
  const [health, setHealth] = useState<HealthState>('checking')

  useEffect(() => {
    const controller = new AbortController()
    fetch('/actuator/health', { signal: controller.signal })
      .then((res) => (res.ok ? res.json() : Promise.reject(new Error(String(res.status)))))
      .then((body: { status?: string }) => setHealth(body.status === 'UP' ? 'up' : 'down'))
      .catch((err: unknown) => {
        if (!(err instanceof DOMException && err.name === 'AbortError')) setHealth('down')
      })
    return () => controller.abort()
  }, [])

  return (
    <main>
      <h1>리드마그넷 CRM 관리자</h1>
      <p>
        백엔드 상태: <strong data-testid="health">{LABELS[health]}</strong>
      </p>
    </main>
  )
}
