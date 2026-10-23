import { useState, type DragEvent, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { ApiError } from '../api/client'
import { listTemplates, uploadTemplate } from '../api/templates'
import { Button } from '../components/Button'
import { Dialog } from '../components/Dialog'
import { EmptyState } from '../components/EmptyState'
import { ErrorMessage } from '../components/ErrorMessage'
import { errorText } from '../errors'
import { focusFirstInvalid } from '../dom'
import { Field } from '../components/Field'
import { PageHeader, SkeletonRows, TableWrap } from '../components/Table'
import { formatBytes, formatDateTime, formatNumber } from '../format'
import { useAsync } from '../hooks'
import { TEMPLATE_ERROR_GUIDES } from '../labels'

export function TemplateListPage() {
  const { data, error, loading, reload } = useAsync(listTemplates, [])
  const [uploadOpen, setUploadOpen] = useState(false)

  return (
    <>
      <PageHeader
        title="템플릿"
        actions={
          <Button variant="primary" onClick={() => setUploadOpen(true)}>
            HTML 업로드
          </Button>
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
  const navigate = useNavigate()
  const [file, setFile] = useState<File | null>(null)
  const [name, setName] = useState('')
  const [dragging, setDragging] = useState(false)
  const [fileError, setFileError] = useState<string>()
  const [failure, setFailure] = useState<{ guide?: string; detail: string; code?: string } | null>(null)
  const [busy, setBusy] = useState(false)

  function close() {
    setFile(null)
    setName('')
    setFileError(undefined)
    setFailure(null)
    onClose()
  }

  function pick(next: File | undefined) {
    setFailure(null)
    setFileError(undefined)
    setFile(next ?? null)
  }

  function onDrop(e: DragEvent) {
    e.preventDefault()
    setDragging(false)
    pick(e.dataTransfer.files[0])
  }

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    if (!file) {
      setFileError('업로드할 HTML 파일을 골라 주세요')
      focusFirstInvalid(e.currentTarget)
      return
    }
    setBusy(true)
    setFailure(null)
    try {
      const created = await uploadTemplate(file, name)
      close()
      navigate(`/templates/${created.id}`)
    } catch (err) {
      if (err instanceof ApiError) {
        setFailure({ guide: err.code ? TEMPLATE_ERROR_GUIDES[err.code] : undefined, detail: err.message, code: err.code })
      } else setFailure({ detail: errorText(err) })
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog
      open={open}
      title="HTML 템플릿 업로드"
      onClose={close}
      footer={
        <>
          <Button onClick={close}>취소</Button>
          <Button variant="primary" type="submit" form="upload-form" busy={busy}>
            {busy ? '업로드 중…' : '업로드'}
          </Button>
        </>
      }
    >
      <form id="upload-form" onSubmit={onSubmit} noValidate>
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
        <Field label="이름" optional hint="비우면 파일 이름을 씁니다">
          {(p) => <input {...p} type="text" name="name" autoComplete="off" value={name} onChange={(e) => setName(e.target.value)} />}
        </Field>
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
