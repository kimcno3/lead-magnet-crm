import { useEffect, useState } from 'react'
import { Button } from './Button'

export function CopyButton({ text, label = '복사' }: { text: string; label?: string }) {
  const [state, setState] = useState<'idle' | 'copied' | 'failed'>('idle')

  useEffect(() => {
    if (state === 'idle') return
    const t = setTimeout(() => setState('idle'), 2000)
    return () => clearTimeout(t)
  }, [state])

  async function copy() {
    try {
      await navigator.clipboard.writeText(text)
      setState('copied')
    } catch {
      setState('failed')
    }
  }

  return (
    <span className="copy">
      <Button size="sm" onClick={copy} aria-label={`${label}: ${text}`}>
        {state === 'copied' ? '복사됨' : label}
      </Button>
      <span className="visually-hidden" aria-live="polite">
        {state === 'copied' ? '클립보드에 복사했습니다' : state === 'failed' ? '복사하지 못했습니다. 직접 선택해 복사해 주세요' : ''}
      </span>
    </span>
  )
}
