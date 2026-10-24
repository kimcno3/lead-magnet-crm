import { useRef, useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import { uploadTemplateVersion } from '../api/templates'
import type { TemplateVersionResult } from '../api/types'
import { Button } from '../components/Button'
import { Dialog } from '../components/Dialog'
import { Field } from '../components/Field'
import { InspectionReport } from '../components/InspectionReport'
import { errorText } from '../errors'
import { formatBytes } from '../format'
import { useI18n } from '../i18n/context'
import { templateErrorGuide } from '../labels'

interface Props {
  open: boolean
  templateId: number
  onClose: () => void
  /** 새 버전이 적용된 뒤(결과를 본 다음 닫을 때) 부른다. 화면이 새 내용을 다시 읽는다. */
  onApplied: () => void
}

/** 같은 템플릿에 새 파일을 올린다. 성공하면 검사 결과와 새 필드로 바뀐 초안 폼 수를 보여준다 (ADR 0018). */
export function NewVersionDialog({ open, templateId, onClose, onApplied }: Props) {
  const { t, rich } = useI18n()
  const [file, setFile] = useState<File | null>(null)
  const [fileError, setFileError] = useState<string>()
  const [failure, setFailure] = useState<{ guide?: string; detail: string; code?: string } | null>(null)
  const [busy, setBusy] = useState(false)
  const [result, setResult] = useState<TemplateVersionResult | null>(null)
  const resultHeading = useRef<HTMLHeadingElement>(null)
  // 올리는 중에 다이얼로그를 닫아도 요청은 계속되므로, 끝나면 결과를 못 봐도 화면은 새로 읽는다.
  const closedWhileBusy = useRef(false)

  function close() {
    const applied = result !== null
    closedWhileBusy.current = busy
    setFile(null)
    setFileError(undefined)
    setFailure(null)
    setResult(null)
    onClose()
    if (applied) onApplied()
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    if (!file) {
      setFileError(t('newVersion.pickFile'))
      return
    }
    setBusy(true)
    setFailure(null)
    setFileError(undefined)
    closedWhileBusy.current = false
    try {
      const applied = await uploadTemplateVersion(templateId, file)
      if (closedWhileBusy.current) {
        closedWhileBusy.current = false
        onApplied()
        return
      }
      setResult(applied)
      queueMicrotask(() => resultHeading.current?.focus())
    } catch (err) {
      if (err instanceof ApiError) {
        const guide = (err.code && templateErrorGuide(err.code)) || (err.status === 413 ? templateErrorGuide('template_too_large') : undefined)
        setFailure({ guide, detail: err.message, code: err.code })
      } else setFailure({ detail: errorText(err) })
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog
      open={open}
      title={t('newVersion.title')}
      onClose={close}
      footer={
        result ? (
          <Button variant="primary" onClick={close}>
            {t('common.close')}
          </Button>
        ) : (
          <>
            <Button onClick={close}>{t('common.cancel')}</Button>
            <Button variant="primary" type="submit" form="new-version-form" busy={busy}>
              {busy ? t('newVersion.uploading') : t('newVersion.submit')}
            </Button>
          </>
        )
      }
    >
      {result ? (
        <section aria-labelledby="version-result-heading" role="status">
          <h3 id="version-result-heading" className="section-title" tabIndex={-1} ref={resultHeading}>
            {t('newVersion.applied', { version: result.version })}
          </h3>
          <p className="note">
            {rich('newVersion.result', {
              fields: String(result.fields.length),
              size: (
                <span key="size" className="num">
                  {formatBytes(result.size)}
                </span>
              ),
              drafts: <strong key="drafts">{t('newVersion.drafts', { count: result.affectedDraftForms })}</strong>,
            })}
          </p>
          <InspectionReport warnings={result.inspection} />
        </section>
      ) : (
        <form id="new-version-form" onSubmit={onSubmit} noValidate>
          <p className="note">
            {t('newVersion.intro')}
          </p>
          <Field label={t('common.htmlFile')} error={fileError} hint={t('newVersion.fileHint')}>
            {(p) => (
              <input
                {...p}
                type="file"
                accept=".html,.htm,text/html"
                onChange={(e) => {
                  setFile(e.target.files?.[0] ?? null)
                  setFailure(null)
                  setFileError(undefined)
                }}
              />
            )}
          </Field>
          {failure && (
            <div className="error-box" role="alert">
              {failure.guide && <p className="error-guide">{failure.guide}</p>}
              <p>{failure.detail}</p>
              {failure.code && <p className="error-code">{t('common.errorCode', { code: failure.code })}</p>}
            </div>
          )}
        </form>
      )}
    </Dialog>
  )
}
