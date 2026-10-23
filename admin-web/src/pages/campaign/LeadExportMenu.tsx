import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react'
import { downloadLeadsCsv } from '../../api/leads'
import type { Channel } from '../../api/types'
import { Button } from '../../components/Button'
import { ConfirmDialog } from '../../components/ConfirmDialog'
import { errorText } from '../../errors'

interface LeadExportMenuProps {
  campaignId: number
  formId: number | null
  channel: Channel | null
  /** 확인 대화상자에 보여줄 현재 필터 범위(예: "폼 세미나 신청 · 채널 인스타그램" 또는 "전체") */
  scope: string
}

/**
 * 명단 CSV 다운로드 메뉴. 마스킹(가린 값)은 바로 내려받고, 원문은 감사 로그에 남는다는 확인을 거친다 (ADR 0016).
 * 필터(폼·채널)는 화면과 같은 값을 쓴다.
 */
export function LeadExportMenu({ campaignId, formId, channel, scope }: LeadExportMenuProps) {
  const [open, setOpen] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const menuId = useId()
  const rootRef = useRef<HTMLDivElement>(null)
  const buttonRef = useRef<HTMLButtonElement>(null)
  const menuRef = useRef<HTMLUListElement>(null)

  useEffect(() => {
    if (!open) return
    menuRef.current?.querySelector<HTMLButtonElement>('[role="menuitem"]')?.focus()
    function onPointerDown(e: PointerEvent) {
      if (!rootRef.current?.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('pointerdown', onPointerDown)
    return () => document.removeEventListener('pointerdown', onPointerDown)
  }, [open])

  function close() {
    setOpen(false)
    buttonRef.current?.focus()
  }

  function onMenuKeyDown(e: KeyboardEvent<HTMLUListElement>) {
    const items = Array.from(menuRef.current?.querySelectorAll<HTMLButtonElement>('[role="menuitem"]') ?? [])
    const index = items.indexOf(document.activeElement as HTMLButtonElement)
    if (e.key === 'Escape') {
      e.preventDefault()
      close()
    } else if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
      e.preventDefault()
      const step = e.key === 'ArrowDown' ? 1 : -1
      items[(index + step + items.length) % items.length]?.focus()
    } else if (e.key === 'Tab') {
      setOpen(false)
    }
  }

  function download(reveal: boolean) {
    return downloadLeadsCsv(campaignId, { formId, channel, reveal })
  }

  async function downloadMasked() {
    close()
    setBusy(true)
    setError(null)
    try {
      await download(false)
    } catch (e) {
      setError(e)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="menu-root" ref={rootRef}>
      <Button
        ref={buttonRef}
        size="sm"
        busy={busy}
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={open ? menuId : undefined}
        onClick={() => setOpen((v) => !v)}
      >
        CSV 다운로드
      </Button>
      {open && (
        <ul className="menu" id={menuId} role="menu" aria-label="CSV 다운로드" ref={menuRef} onKeyDown={onMenuKeyDown}>
          <li role="none">
            <button type="button" role="menuitem" className="menu-item" onClick={downloadMasked}>
              마스킹
              <span className="menu-hint">이름·연락처를 가린 값</span>
            </button>
          </li>
          <li role="none">
            <button
              type="button"
              role="menuitem"
              className="menu-item"
              onClick={() => {
                setOpen(false)
                setError(null)
                setConfirming(true)
              }}
            >
              원문
              <span className="menu-hint">확인 후 내려받고 감사 로그에 기록</span>
            </button>
          </li>
        </ul>
      )}
      {error !== null && (
        <p className="small" role="alert">
          {errorText(error)}
        </p>
      )}
      <ConfirmDialog
        open={confirming}
        title="원문 CSV 다운로드"
        message={`범위: ${scope}. 이 범위의 신청자 이름·전화번호·이메일 원문이 담긴 파일을 내려받습니다. 다운로드는 운영자·시각·IP·건수와 함께 감사 로그에 기록됩니다. 파일은 필요한 곳에만 쓰고 다 쓰면 지워 주세요.`}
        confirmLabel="원문 다운로드"
        onConfirm={() => download(true)}
        onClose={() => setConfirming(false)}
      />
    </div>
  )
}
