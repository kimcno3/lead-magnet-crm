import { useState } from 'react'
import { Button } from './Button'
import { Dialog } from './Dialog'
import { errorText } from '../errors'

interface ConfirmDialogProps {
  open: boolean
  title: string
  message: string
  confirmLabel: string
  danger?: boolean
  onConfirm: () => Promise<void>
  onClose: () => void
}

/** 되돌리기 어려운 행동 확인. 실패하면 닫지 않고 오류를 보여준다. */
export function ConfirmDialog({ open, title, message, confirmLabel, danger = true, onConfirm, onClose }: ConfirmDialogProps) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  function close() {
    setError(null)
    onClose()
  }

  async function confirm() {
    setBusy(true)
    setError(null)
    try {
      await onConfirm()
      close()
    } catch (e) {
      setError(errorText(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog
      open={open}
      title={title}
      onClose={close}
      width="sm"
      footer={
        <>
          <Button onClick={close}>취소</Button>
          <Button variant={danger ? 'danger' : 'primary'} onClick={confirm} busy={busy}>
            {confirmLabel}
          </Button>
        </>
      }
    >
      <p>{message}</p>
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
    </Dialog>
  )
}
