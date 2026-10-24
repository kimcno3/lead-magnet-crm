import { useEffect, useRef, useState, type DragEvent, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { ApiError } from '../api/client'
import { inspectTemplate, listTemplates, uploadTemplate } from '../api/templates'
import type { TemplateInspection } from '../api/types'
import { AI_FORM_PROMPT } from '../aiFormPrompt'
import { InspectionReport } from '../components/InspectionReport'
import { Button } from '../components/Button'
import { Dialog } from '../components/Dialog'
import { EmptyState } from '../components/EmptyState'
import { ErrorMessage } from '../components/ErrorMessage'
import { errorText } from '../errors'
import { Field } from '../components/Field'
import { PageHeader } from '../components/PageHeader'
import { SkeletonRows, TableWrap } from '../components/Table'
import { formatBytes, formatDateTime, formatNumber } from '../format'
import { useAsync, useFocusFirstInvalid } from '../hooks'
import { useI18n } from '../i18n/context'
import { templateErrorGuide } from '../labels'

export function TemplateListPage() {
  const { t } = useI18n()
  const { data, error, loading, reload } = useAsync(listTemplates, [])
  const [uploadOpen, setUploadOpen] = useState(false)
  const [copyStatus, setCopyStatus] = useState<string>()
  const [copyFailed, setCopyFailed] = useState(false)

  async function copyPrompt() {
    // 먼저 비워서 같은 문구를 다시 띄워도 화면 읽기 도구가 다시 읽게 한다.
    setCopyStatus(undefined)
    setCopyFailed(false)
    try {
      await navigator.clipboard.writeText(AI_FORM_PROMPT)
      setCopyStatus(t('templateList.copied'))
    } catch {
      setCopyFailed(true)
      setCopyStatus(t('templateList.copyFailed'))
    }
  }

  return (
    <>
      <PageHeader
        title={t('nav.templates')}
        actions={
          <>
            <span className="copy-status" role="status">
              {copyStatus}
            </span>
            <Button onClick={copyPrompt}>{t('templateList.copyPrompt')}</Button>
            <Button variant="primary" onClick={() => setUploadOpen(true)}>
              {t('templateList.upload')}
            </Button>
          </>
        }
      />
      {copyFailed && (
        <div className="note">
          <label htmlFor="ai-prompt-text">{t('templateList.promptLabel')}</label>
          <textarea id="ai-prompt-text" readOnly rows={8} value={AI_FORM_PROMPT} onFocus={(e) => e.currentTarget.select()} style={{ width: '100%' }} />
        </div>
      )}
      <ErrorMessage error={error} onRetry={reload} />
      {data && data.length === 0 ? (
        <EmptyState
          message={t('templateList.empty')}
          action={
            <Button variant="primary" onClick={() => setUploadOpen(true)}>
              {t('templateList.uploadFirst')}
            </Button>
          }
        />
      ) : (
        !error && (
          <TableWrap label={t('templateList.tableLabel')}>
            <table className="table">
              <caption className="visually-hidden">{t('templateList.tableLabel')}</caption>
              <thead>
                <tr>
                  <th scope="col">{t('common.name')}</th>
                  <th scope="col" className="num">
                    {t('templateList.size')}
                  </th>
                  <th scope="col" className="num">
                    {t('templateList.fieldCount')}
                  </th>
                  <th scope="col" className="num">
                    {t('templateList.version')}
                  </th>
                  <th scope="col">{t('templateList.updatedAt')}</th>
                </tr>
              </thead>
              <tbody aria-busy={loading}>
                {!data && loading ? (
                  <SkeletonRows columns={5} />
                ) : (
                  data?.map((t) => (
                    <tr key={t.id}>
                      <td>
                        <Link to={`/templates/${t.id}`} className="row-link">
                          {t.name}
                        </Link>
                      </td>
                      <td className="num">{formatBytes(t.size)}</td>
                      <td className="num">{formatNumber(t.fieldCount)}</td>
                      <td className="num">v{t.version}</td>
                      <td className="num">{formatDateTime(t.updatedAt)}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </TableWrap>
        )
      )}
      <UploadDialog open={uploadOpen} onClose={() => setUploadOpen(false)} />
    </>
  )
}

function UploadDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { t, rich } = useI18n()
  const { formRef, focusFirstInvalid } = useFocusFirstInvalid()
  const navigate = useNavigate()
  const [file, setFile] = useState<File | null>(null)
  const [name, setName] = useState('')
  const [dragging, setDragging] = useState(false)
  const [fileError, setFileError] = useState<string>()
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [failure, setFailure] = useState<{ guide?: string; detail: string; code?: string } | null>(null)
  const [busy, setBusy] = useState(false)
  const [report, setReport] = useState<TemplateInspection | null>(null)
  const [inspecting, setInspecting] = useState(false)
  const reportHeading = useRef<HTMLHeadingElement>(null)

  // 검사 결과가 나타나면 제목으로 포커스를 옮겨 읽게 한다.
  useEffect(() => {
    if (report) reportHeading.current?.focus()
  }, [report])

  function close() {
    setReport(null)
    setFile(null)
    setName('')
    setFileError(undefined)
    setFieldErrors({})
    setFailure(null)
    onClose()
  }

  function pick(next: File | undefined) {
    setReport(null)
    setFailure(null)
    setFileError(undefined)
    setFile(next ?? null)
  }

  function onDrop(e: DragEvent) {
    e.preventDefault()
    setDragging(false)
    pick(e.dataTransfer.files[0])
  }

  function showFailure(err: unknown) {
    if (err instanceof ApiError && err.errors.length) {
      // 필드 오류(file, name)는 해당 입력 아래에 붙인다.
      const { file: fileMessage, ...rest } = err.fieldErrors
      setFileError(fileMessage)
      setFieldErrors(rest)
      focusFirstInvalid()
    } else if (err instanceof ApiError) {
      // nginx 기본 413 페이지처럼 ProblemDetail이 아닌 응답도 상태로 크기 안내를 고른다.
      const guide = (err.code && templateErrorGuide(err.code)) || (err.status === 413 ? templateErrorGuide('template_too_large') : undefined)
      setFailure({ guide, detail: err.message, code: err.code })
    } else setFailure({ detail: errorText(err) })
  }

  async function run(action: (f: File) => Promise<void>) {
    if (!file) {
      setFileError(t('templateList.pickFile'))
      focusFirstInvalid()
      return
    }
    setBusy(true)
    setFailure(null)
    setFieldErrors({})
    setReport(null)
    try {
      await action(file)
    } catch (err) {
      showFailure(err)
    } finally {
      setBusy(false)
    }
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault()
    return run(async (f) => {
      const created = await uploadTemplate(f, name)
      close()
      navigate(`/templates/${created.id}`)
    })
  }

  /** 아무것도 저장하지 않고 검사 결과만 다이얼로그에 보여준다. */
  async function onInspect() {
    setInspecting(true)
    try {
      await run(async (f) => setReport(await inspectTemplate(f, name)))
    } finally {
      setInspecting(false)
    }
  }

  return (
    <Dialog
      open={open}
      title={t('templateList.dialogTitle')}
      onClose={close}
      footer={
        <>
          <Button onClick={close}>{t('common.cancel')}</Button>
          <Button onClick={onInspect} busy={inspecting} disabled={busy}>
            {inspecting ? t('templateList.inspecting') : t('templateList.inspectOnly')}
          </Button>
          <Button variant="primary" type="submit" form="upload-form" busy={busy}>
            {busy ? t('templateList.uploading') : t('templateList.submit')}
          </Button>
        </>
      }
    >
      <form ref={formRef} id="upload-form" onSubmit={onSubmit} noValidate>
        <div
          className={`dropzone${dragging ? ' is-dragging' : ''}`}
          data-testid="dropzone"
          onDragOver={(e) => {
            e.preventDefault()
            setDragging(true)
          }}
          onDragLeave={() => setDragging(false)}
          onDrop={onDrop}
        >
          <Field label={t('common.htmlFile')} error={fileError} hint={t('templateList.fileHint')}>
            {(p) => <input {...p} type="file" accept=".html,.htm,text/html" onChange={(e) => pick(e.target.files?.[0])} />}
          </Field>
          {file && (
            <p className="dropzone-file">
              {rich('templateList.selectedFile', { name: <strong key="name">{file.name}</strong> })}{' '}
              <span className="num">({formatBytes(file.size)})</span>
            </p>
          )}
        </div>
        <Field label={t('common.name')} optional error={fieldErrors.name} hint={t('templateList.nameHint')}>
          {(p) => <input {...p} type="text" name="name" autoComplete="off" value={name} onChange={(e) => setName(e.target.value)} />}
        </Field>
        {report && (
          <section aria-labelledby="inspect-heading" className="inspect-result" role="status">
            <h3 id="inspect-heading" className="section-title" tabIndex={-1} ref={reportHeading}>
              {t('templateList.inspectResult')}
            </h3>
            <p className="note">
              {t('templateList.inspectNote', { count: report.fields.length })}
            </p>
            <InspectionReport warnings={report.inspection} />
          </section>
        )}
        {failure && (
          <div className="error-box" role="alert">
            {failure.guide && <p className="error-guide">{failure.guide}</p>}
            <p>{failure.detail}</p>
            {failure.code && <p className="error-code">{t('common.errorCode', { code: failure.code })}</p>}
          </div>
        )}
      </form>
    </Dialog>
  )
}
