import { Link, useParams } from 'react-router'
import { getTemplate } from '../api/templates'
import { ErrorMessage } from '../components/ErrorMessage'
import { FieldsTable } from '../components/FieldsTable'
import { SandboxPreview } from '../components/SandboxPreview'
import { PageHeader } from '../components/Table'
import { formatBytes, formatDateTime } from '../format'
import { useAsync } from '../hooks'

export function TemplateDetailPage() {
  const id = Number(useParams().id)
  const { data, error, loading, reload } = useAsync(() => getTemplate(id), [id])

  return (
    <>
      <nav className="crumbs" aria-label="경로">
        <Link to="/templates">템플릿</Link>
      </nav>
      <PageHeader title={data?.name ?? (loading ? '불러오는 중…' : '템플릿')}>
        {data && (
          <p className="page-meta">
            <span className="num">{formatBytes(data.size)}</span>
            <span>필드 {data.fields.length}개</span>
            <span>등록 {formatDateTime(data.createdAt)}</span>
          </p>
        )}
      </PageHeader>
      <ErrorMessage error={error} onRetry={reload} />
      {data && (
        <div className="split">
          <section aria-labelledby="fields-heading" className="split-main">
            <h2 id="fields-heading" className="section-title">
              추출된 필드
            </h2>
            <FieldsTable fields={data.fields} caption="추출된 필드" />
          </section>
          <section aria-labelledby="preview-heading" className="split-side">
            <h2 id="preview-heading" className="section-title">
              미리보기
            </h2>
            <p className="note">관리자 화면에서는 스크립트 없이 정적으로 표시됩니다. 실제 동작은 폼을 게시한 뒤 공개 URL에서 확인하세요.</p>
            <SandboxPreview html={data.content} title={`${data.name} 미리보기`} />
          </section>
        </div>
      )}
    </>
  )
}
