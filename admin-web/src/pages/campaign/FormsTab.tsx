import { Fragment, useState } from 'react'
import { useSearchParams } from 'react-router'
import { getForm, updateForm } from '../../api/forms'
import { listLinks } from '../../api/links'
import type { Campaign, FormSummary } from '../../api/types'
import { StatusBadge } from '../../components/Badge'
import { Button } from '../../components/Button'
import { ConfirmDialog } from '../../components/ConfirmDialog'
import { CopyButton } from '../../components/CopyButton'
import { EmptyState } from '../../components/EmptyState'
import { ErrorMessage } from '../../components/ErrorMessage'
import { FieldsTable } from '../../components/FieldsTable'
import { TableWrap } from '../../components/Table'
import { formatDateTime } from '../../format'
import { useAsync } from '../../hooks'
import { useI18n } from '../../i18n/context'
import { publicBaseFrom } from '../../labels'
import { FieldConfigEditor } from './FieldConfigEditor'
import { DistributionLinks } from './DistributionLinks'
import { NewFormDialog } from './NewFormDialog'

export function FormsTab({ campaign, onChanged }: { campaign: Campaign; onChanged: () => void }) {
  const { t } = useI18n()
  // 펼친 폼은 URL(?open=)에 남겨 새로고침·공유해도 같은 패널이 열린다.
  const [params, setParams] = useSearchParams()
  const openId = Number(params.get('open')) || null
  function setOpenId(id: number | null) {
    setParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        if (id) next.set('open', String(id))
        else next.delete('open')
        return next
      },
      { replace: true },
    )
  }
  const [creating, setCreating] = useState(false)
  const archived = campaign.status === 'archived'

  const newButton = (
    <Button variant="primary" onClick={() => setCreating(true)} disabled={archived} title={archived ? t('forms.archivedNoNew') : undefined}>
      {t('forms.new')}
    </Button>
  )

  return (
    <>
      <div className="section-head">
        <h2 className="section-title">{t('forms.count', { count: campaign.forms.length })}</h2>
        {newButton}
      </div>
      {archived && <p className="note">{t('forms.archivedNote')}</p>}
      {campaign.forms.length === 0 ? (
        <EmptyState
          message={archived ? t('forms.emptyArchived') : t('forms.empty')}
          action={archived ? undefined : newButton}
        />
      ) : (
        <TableWrap label={t('forms.tableLabel')}>
          <table className="table table-expandable">
            <caption className="visually-hidden">{t('forms.tableLabel')}</caption>
            <thead>
              <tr>
                <th scope="col">{t('forms.title')}</th>
                <th scope="col">slug</th>
                <th scope="col">{t('common.status')}</th>
                <th scope="col">{t('forms.publishedAt')}</th>
              </tr>
            </thead>
            <tbody>
              {campaign.forms.map((f) => {
                const open = openId === f.id
                return (
                  <Fragment key={f.id}>
                    <tr className={open ? 'is-open' : undefined}>
                      <td>
                        <button
                          type="button"
                          className="disclosure"
                          aria-expanded={open}
                          aria-controls={`form-panel-${f.id}`}
                          onClick={() => setOpenId(open ? null : f.id)}
                        >
                          <span className="disclosure-mark" aria-hidden="true">
                            {open ? '−' : '+'}
                          </span>
                          {f.title}
                        </button>
                      </td>
                      <td className="mono" translate="no">{f.slug}</td>
                      <td>
                        <StatusBadge status={f.status} />
                      </td>
                      <td className="num">{formatDateTime(f.publishedAt)}</td>
                    </tr>
                    {open && (
                      <tr className="panel-row">
                        <td colSpan={4} id={`form-panel-${f.id}`}>
                          <FormPanel summary={f} onChanged={onChanged} />
                        </td>
                      </tr>
                    )}
                  </Fragment>
                )
              })}
            </tbody>
          </table>
        </TableWrap>
      )}
      <NewFormDialog open={creating} campaignId={campaign.id} onClose={() => setCreating(false)} onCreated={(form) => {
        setCreating(false)
        setOpenId(form.id)
        onChanged()
      }} />
    </>
  )
}

function FormPanel({ summary, onChanged }: { summary: FormSummary; onChanged: () => void }) {
  const { t } = useI18n()
  const form = useAsync(() => getForm(summary.id), [summary.id, summary.status])
  const links = useAsync(() => listLinks(summary.id), [summary.id])
  const [transition, setTransition] = useState<'published' | 'closed' | null>(null)

  const publicUrl = `${publicBaseFrom(links.data?.[0]?.url)}/f/${summary.slug}`
  const status = form.data?.status ?? summary.status

  return (
    <div className="form-panel">
      <section className="panel-block" aria-label={t('common.status')}>
        <h3>{t('common.status')}</h3>
        <div className="inline-row">
          <StatusBadge status={status} />
          {status === 'draft' && (
            <Button variant="primary" size="sm" onClick={() => setTransition('published')}>
              {t('forms.publish')}
            </Button>
          )}
          {status === 'published' && (
            <Button size="sm" onClick={() => setTransition('closed')}>
              {t('forms.close')}
            </Button>
          )}
          {status === 'closed' && <span className="muted small">{t('forms.closedNote')}</span>}
          {status === 'draft' && <span className="muted small">{t('forms.draftNote')}</span>}
        </div>
      </section>

      {form.data?.publishedTemplateSha256 && form.data.publishedTemplateSha256 !== form.data.templateSha256 && (
        <p className="note" role="status">
          {t('forms.templateChanged')}
        </p>
      )}

      <section className="panel-block" aria-label={t('forms.publicUrl')}>
        <h3>{t('forms.publicUrl')}</h3>
        <div className="inline-row">
          <code className="url" translate="no">{publicUrl}</code>
          <CopyButton text={publicUrl} />
          {status === 'published' && (
            <a className="btn btn-ghost btn-sm" href={publicUrl} target="_blank" rel="noopener noreferrer">
              {t('forms.openInNewTab')}
            </a>
          )}
        </div>
      </section>

      <section className="panel-block">
        <h3>{t('forms.distributionLinks')}</h3>
        <DistributionLinks
          formId={summary.id}
          closed={status === 'closed'}
          links={links.data}
          linksError={links.error}
          onReloadLinks={links.reload}
        />
      </section>

      <section className="panel-block" aria-label={t('forms.fieldConfig')}>
        <h3>{t('forms.fieldConfig')}</h3>
        <ErrorMessage error={form.error} onRetry={form.reload} />
        {form.data &&
          (form.data.status === 'draft' ? (
            <FieldConfigEditor key={form.data.id} form={form.data} onSaved={form.reload} />
          ) : (
            <>
              <p className="muted small">{t('forms.publishedLocked')}</p>
              <FieldsTable fields={form.data.fieldConfig} caption={t('forms.fieldConfigOf', { title: summary.title })} />
            </>
          ))}
      </section>

      <ConfirmDialog
        open={transition !== null}
        title={transition === 'published' ? t('forms.publishTitle') : t('forms.closeTitle')}
        message={
          transition === 'published'
            ? t('forms.publishMessage')
            : t('forms.closeMessage')
        }
        confirmLabel={transition === 'published' ? t('forms.publishConfirm') : t('forms.closeConfirm')}
        danger={transition === 'closed'}
        onConfirm={async () => {
          if (!transition) return
          await updateForm(summary.id, { status: transition })
          onChanged()
        }}
        onClose={() => setTransition(null)}
      />
    </div>
  )
}
