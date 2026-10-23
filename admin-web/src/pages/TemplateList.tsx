import { useState, type DragEvent, type FormEvent } from 'react'
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
import { TEMPLATE_ERROR_GUIDES } from '../labels'

export function TemplateListPage() {
  const { data, error, loading, reload } = useAsync(listTemplates, [])
  const [uploadOpen, setUploadOpen] = useState(false)
  const [copyStatus, setCopyStatus] = useState<string>()

  async function copyPrompt() {
    try {
      await navigator.clipboard.writeText(AI_FORM_PROMPT)
      setCopyStatus('복사했습니다')
    } catch {
      setCopyStatus('복사하지 못했습니다. docs/AI_FORM_PROMPT.md에서 직접 복사해 주세요')
    }
  }

  return (
    <>
      <PageHeader
        title="템플릿"
        actions={
          <>
            <span className="copy-status" role="status">
              {copyStatus}
            </span>
            <Button onClick={copyPrompt}>AI 프롬프트 복사</Button>
            <Button variant="primary" onClick={() => setUploadOpen(true)}>
              HTML 업로드
            </Button>
          </>
        }
      />
      <ErrorMessage error={error} onRetry={reload} />
      {data && data.length === 0 ? (
        <EmptyState
          message="아직 등록한 템플릿이 없습니다. AI로 만든 HTML 폼 파일을 올리면 폼을 만들 수 있습니다."
          action={
            <Button variant="primary" onClick={() => setUploadOpen(true)}>
              템플릿 올리기
            </Button>
          }
        />
      ) : (
        !error && (
          <TableWrap label="템플릿 목록">
            <table className="table">
              <caption className="visually-hidden">템플릿 목록</caption>
              <thead>
                <tr>
                  <th scope="col">이름</th>
                  <th scope="col" className="num">
                    크기
                  </th>
                  <th scope="col" className="num">
                    필드 수
                  </th>
                  <th scope="col">등록일</th>
                </tr>
              </thead>
              <tbody aria-busy={loading}>
                {!data && loading ? (
                  <SkeletonRows columns={4} />
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
                      <td className="num">{formatDateTime(t.createdAt)}</td>
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
      const guide = (err.code && TEMPLATE_ERROR_GUIDES[err.code]) || (err.status === 413 ? TEMPLATE_ERROR_GUIDES.template_too_large : undefined)
      setFailure({ guide, detail: err.message, code: err.code })
    } else setFailure({ detail: errorText(err) })
  }

  async function run(action: (f: File) => Promise<void>) {
    if (!file) {
      setFileError('업로드할 HTML 파일을 골라 주세요')
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
  function onInspect() {
    return run(async (f) => setReport(await inspectTemplate(f, name)))
  }

  return (
    <Dialog
      open={open}
      title="HTML 템플릿 업로드"
      onClose={close}
      footer={
        <>
          <Button onClick={close}>취소</Button>
          <Button onClick={onInspect} disabled={busy}>
            검사만 하기
          </Button>
          <Button variant="primary" type="submit" form="upload-form" busy={busy}>
            {busy ? '업로드 중…' : '업로드'}
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
          <Field label="HTML 파일" error={fileError} hint="파일을 이 영역에 끌어다 놓아도 됩니다. .html 또는 .htm, 512KB 이하">
            {(p) => <input {...p} type="file" accept=".html,.htm,text/html" onChange={(e) => pick(e.target.files?.[0])} />}
          </Field>
          {file && (
            <p className="dropzone-file">
              선택한 파일: <strong>{file.name}</strong> <span className="num">({formatBytes(file.size)})</span>
            </p>
          )}
        </div>
        <Field label="이름" optional error={fieldErrors.name} hint="비우면 파일 이름을 씁니다">
          {(p) => <input {...p} type="text" name="name" autoComplete="off" value={name} onChange={(e) => setName(e.target.value)} />}
        </Field>
        {report && (
          <section aria-labelledby="inspect-heading" className="inspect-result">
            <h3 id="inspect-heading" className="section-title">
              검사 결과
            </h3>
            <p className="note">
              추출될 필드 {report.fields.length}개. 아무것도 저장하지 않았습니다. 고쳐서 다시 검사하거나, 이대로 괜찮으면 업로드를 누르세요.
            </p>
            <InspectionReport warnings={report.inspection} />
          </section>
        )}
        {failure && (
          <div className="error-box" role="alert">
            {failure.guide && <p className="error-guide">{failure.guide}</p>}
            <p>{failure.detail}</p>
            {failure.code && <p className="error-code">코드: {failure.code}</p>}
          </div>
        )}
      </form>
    </Dialog>
  )
}
