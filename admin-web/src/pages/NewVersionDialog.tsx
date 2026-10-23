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
import { TEMPLATE_ERROR_GUIDES } from '../labels'

interface Props {
  open: boolean
  templateId: number
  onClose: () => void
  /** 새 버전이 적용된 뒤(결과를 본 다음 닫을 때) 부른다. 화면이 새 내용을 다시 읽는다. */
  onApplied: () => void
}

/** 같은 템플릿에 새 파일을 올린다. 성공하면 검사 결과와 새 필드로 바뀐 초안 폼 수를 보여준다 (ADR 0018). */
export function NewVersionDialog({ open, templateId, onClose, onApplied }: Props) {
  const [file, setFile] = useState<File | null>(null)
  const [fileError, setFileError] = useState<string>()
  const [failure, setFailure] = useState<{ guide?: string; detail: string; code?: string } | null>(null)
  const [busy, setBusy] = useState(false)
  const [result, setResult] = useState<TemplateVersionResult | null>(null)
  const resultHeading = useRef<HTMLHeadingElement>(null)

  function close() {
    const applied = result !== null
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
      setFileError('올릴 HTML 파일을 골라 주세요')
      return
    }
    setBusy(true)
    setFailure(null)
    setFileError(undefined)
    try {
      setResult(await uploadTemplateVersion(templateId, file))
      queueMicrotask(() => resultHeading.current?.focus())
    } catch (err) {
      if (err instanceof ApiError) {
        const guide = (err.code && TEMPLATE_ERROR_GUIDES[err.code]) || (err.status === 413 ? TEMPLATE_ERROR_GUIDES.template_too_large : undefined)
        setFailure({ guide, detail: err.message, code: err.code })
      } else setFailure({ detail: errorText(err) })
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog
      open={open}
      title="새 버전 업로드"
      onClose={close}
      footer={
        result ? (
          <Button variant="primary" onClick={close}>
            닫기
          </Button>
        ) : (
          <>
            <Button onClick={close}>취소</Button>
            <Button variant="primary" type="submit" form="new-version-form" busy={busy}>
              {busy ? '올리는 중…' : '새 버전 올리기'}
            </Button>
          </>
        )
      }
    >
      {result ? (
        <section aria-labelledby="version-result-heading" role="status">
          <h3 id="version-result-heading" className="section-title" tabIndex={-1} ref={resultHeading}>
            v{result.version}으로 바꿨습니다
          </h3>
          <p className="note">
            필드 {result.fields.length}개, <span className="num">{formatBytes(result.size)}</span>. 새 필드로 다시 만든 초안 폼은{' '}
            <strong>{result.affectedDraftForms}개</strong>입니다. 이미 게시했거나 마감한 폼은 게시 당시 내용 그대로입니다.
          </p>
          <InspectionReport warnings={result.inspection} />
        </section>
      ) : (
        <form id="new-version-form" onSubmit={onSubmit} noValidate>
          <p className="note">
            같은 템플릿의 내용을 새 파일로 바꿉니다. 이 템플릿을 쓰는 초안 폼은 새 필드로 다시 만들어지고(직접 바꾼 라벨·필수 여부는 같은 이름이면 유지),
            게시했거나 마감한 폼은 바뀌지 않습니다.
          </p>
          <Field label="HTML 파일" error={fileError} hint=".html 또는 .htm, 512KB 이하">
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
              {failure.code && <p className="error-code">코드: {failure.code}</p>}
            </div>
          )}
        </form>
      )}
    </Dialog>
  )
}
