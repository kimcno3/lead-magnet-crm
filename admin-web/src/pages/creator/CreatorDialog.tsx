import { useState, type FormEvent } from 'react'
import { ApiError } from '../../api/client'
import type { Creator } from '../../api/types'
import { Button } from '../../components/Button'
import { Dialog } from '../../components/Dialog'
import { Field } from '../../components/Field'
import { errorText } from '../../errors'
import { useFocusFirstInvalid } from '../../hooks'
import { useI18n } from '../../i18n/context'

export interface CreatorValues {
  name: string
  handle: string
  memo: string
}

interface CreatorDialogProps {
  open: boolean
  title: string
  submitLabel: string
  /** 수정이면 현재 값, 새로 만들면 없음 */
  initial?: Creator
  onSubmit: (values: CreatorValues) => Promise<void>
  onClose: () => void
}

/** 크리에이터 등록·수정 공용 다이얼로그. 서버 검증 오류는 해당 입력 옆에 붙인다. */
export function CreatorDialog({ open, title, submitLabel, initial, onSubmit, onClose }: CreatorDialogProps) {
  if (!open) return null
  return <CreatorDialogBody title={title} submitLabel={submitLabel} initial={initial} onSubmit={onSubmit} onClose={onClose} />
}

function CreatorDialogBody({ title, submitLabel, initial, onSubmit, onClose }: Omit<CreatorDialogProps, 'open'>) {
  const { t } = useI18n()
  const { formRef, focusFirstInvalid } = useFocusFirstInvalid()
  const [name, setName] = useState(initial?.name ?? '')
  const [handle, setHandle] = useState(initial?.handle ?? '')
  const [memo, setMemo] = useState(initial?.memo ?? '')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setErrors({})
    setFormError(null)
    try {
      await onSubmit({ name: name.trim(), handle: handle.trim(), memo: memo.trim() })
    } catch (err) {
      if (err instanceof ApiError && err.errors.length) {
        setErrors(err.fieldErrors)
        focusFirstInvalid()
      } else setFormError(errorText(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog
      open
      title={title}
      onClose={onClose}
      footer={
        <>
          <Button onClick={onClose}>{t('common.cancel')}</Button>
          <Button variant="primary" type="submit" form="creator-form" busy={busy}>
            {submitLabel}
          </Button>
        </>
      }
    >
      <form ref={formRef} id="creator-form" onSubmit={submit} noValidate>
        <Field label={t('common.name')} error={errors.name}>
          {(p) => <input {...p} type="text" value={name} autoComplete="off" maxLength={100} onChange={(e) => setName(e.target.value)} required />}
        </Field>
        <Field label={t('creatorList.handle')} optional hint={t('creatorDialog.handleHint')} error={errors.handle}>
          {(p) => <input {...p} type="text" value={handle} autoComplete="off" maxLength={100} onChange={(e) => setHandle(e.target.value)} />}
        </Field>
        <Field label={t('creatorDialog.memo')} optional hint={t('creatorDialog.memoHint')} error={errors.memo}>
          {(p) => <textarea {...p} rows={3} value={memo} maxLength={2000} onChange={(e) => setMemo(e.target.value)} />}
        </Field>
        {formError && (
          <p className="form-error" role="alert">
            {formError}
          </p>
        )}
      </form>
    </Dialog>
  )
}
