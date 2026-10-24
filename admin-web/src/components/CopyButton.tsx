import { useEffect, useState } from 'react'
import { useI18n } from '../i18n/context'
import { Button } from './Button'

export function CopyButton({ text, label: labelProp }: { text: string; label?: string }) {
  const { t } = useI18n()
  const label = labelProp ?? t('components.copy')
  const [state, setState] = useState<'idle' | 'copied' | 'failed'>('idle')

  useEffect(() => {
    if (state === 'idle') return
    const timer = setTimeout(() => setState('idle'), 2000)
    return () => clearTimeout(timer)
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
        {state === 'copied' ? t('components.copied') : label}
      </Button>
      <span className="visually-hidden" aria-live="polite">
        {state === 'copied' ? t('components.copiedAnnounce') : state === 'failed' ? t('components.copyFailed') : ''}
      </span>
    </span>
  )
}
