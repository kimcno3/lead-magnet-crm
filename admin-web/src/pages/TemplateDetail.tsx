import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { getTemplate } from '../api/templates'
import { Button } from '../components/Button'
import { ErrorMessage } from '../components/ErrorMessage'
import { FieldsTable } from '../components/FieldsTable'
import { InspectionReport } from '../components/InspectionReport'
import { SandboxPreview } from '../components/SandboxPreview'
import { PageHeader } from '../components/PageHeader'
import { formatBytes, formatDateTime } from '../format'
import { useAsync } from '../hooks'
import { useI18n } from '../i18n/context'
import { NewVersionDialog } from './NewVersionDialog'

export function TemplateDetailPage() {
  const { t } = useI18n()
  const id = Number(useParams().id)
  const { data, error, loading, reload } = useAsync(() => getTemplate(id), [id])
  const [versionOpen, setVersionOpen] = useState(false)

  return (
    <>
      <nav className="crumbs" aria-label={t('common.breadcrumb')}>
        <Link to="/templates">{t('nav.templates')}</Link>
      </nav>
      <PageHeader
        title={data?.name ?? (loading ? t('common.loading') : t('newForm.template'))}
        actions={data && <Button onClick={() => setVersionOpen(true)}>{t('templateDetail.newVersion')}</Button>}
      >
        {data && (
          <p className="page-meta">
            <span className="num">{formatBytes(data.size)}</span>
            <span>{t('templateDetail.fields', { count: data.fields.length })}</span>
            <span className="num">v{data.version}</span>
            <span>{t('templateDetail.created', { when: formatDateTime(data.createdAt) })}</span>
            <span>{t('templateDetail.updated', { when: formatDateTime(data.updatedAt) })}</span>
          </p>
        )}
      </PageHeader>
      <ErrorMessage error={error} onRetry={reload} />
      <NewVersionDialog open={versionOpen} templateId={id} onClose={() => setVersionOpen(false)} onApplied={reload} />
      {data && (
        <div className="split">
          <section aria-labelledby="fields-heading" className="split-main">
            <h2 id="fields-heading" className="section-title">
              {t('templateDetail.extractedFields')}
            </h2>
            <FieldsTable fields={data.fields} caption={t('templateDetail.extractedFields')} />
            <h2 id="inspection-heading" className="section-title">
              {t('templateList.inspectResult')}
            </h2>
            <p className="note">{t('templateDetail.inspectNote')}</p>
            <InspectionReport warnings={data.inspection} />
          </section>
          <section aria-labelledby="preview-heading" className="split-side">
            <h2 id="preview-heading" className="section-title">
              {t('templateDetail.preview')}
            </h2>
            <p className="note">{t('templateDetail.previewNote')}</p>
            <SandboxPreview html={data.content} title={t('templateDetail.previewOf', { name: data.name })} />
          </section>
        </div>
      )}
    </>
  )
}
