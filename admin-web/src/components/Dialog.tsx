import { useEffect, useId, useRef, type ReactNode } from 'react'

interface DialogProps {
  open: boolean
  title: string
  onClose: () => void
  children: ReactNode
  /** 폼 하단 버튼 줄 */
  footer?: ReactNode
  width?: 'md' | 'sm'
}

/** 네이티브 <dialog>. showModal이 포커스 가두기와 Esc 닫기를 맡는다. 닫히면 연 요소로 포커스를 돌려준다. */
export function Dialog({ open, title, onClose, children, footer, width = 'md' }: DialogProps) {
  const ref = useRef<HTMLDialogElement>(null)
  const titleId = useId()

  useEffect(() => {
    const el = ref.current
    if (!el || !open) return
    const opener = document.activeElement as HTMLElement | null
    if (typeof el.showModal === 'function') {
      if (!el.open) el.showModal()
    } else {
      el.setAttribute('open', '')
    }
    const first = el.querySelector<HTMLElement>('input, select, textarea, button:not(.dialog-close)')
    first?.focus()
    return () => {
      if (typeof el.close === 'function' && el.open) el.close()
      else el.removeAttribute('open')
      opener?.focus?.()
    }
  }, [open])

  if (!open) return null
  return (
    <dialog
      ref={ref}
      className={`dialog dialog-${width}`}
      aria-labelledby={titleId}
      onCancel={(e) => {
        e.preventDefault()
        onClose()
      }}
    >
      <div className="dialog-head">
        <h2 id={titleId}>{title}</h2>
        <button type="button" className="dialog-close" onClick={onClose} aria-label="닫기">
          ×
        </button>
      </div>
      <div className="dialog-body">{children}</div>
      {footer && <div className="dialog-foot">{footer}</div>}
    </dialog>
  )
}
