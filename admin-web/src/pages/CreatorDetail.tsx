import { useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { archiveCreator, creatorCampaignStats, getCreator, updateCreator } from '../api/creators'
import { CreatorStatusBadge, StatusBadge } from '../components/Badge'
import { Button } from '../components/Button'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { EmptyState } from '../components/EmptyState'
import { ErrorMessage } from '../components/ErrorMessage'
import { PageHeader } from '../components/PageHeader'
import { SkeletonRows, TableWrap } from '../components/Table'
import { formatNumber, formatPercent } from '../format'
import { useAsync } from '../hooks'
import { useI18n } from '../i18n/context'
import { PeriodFilter } from '../components/PeriodFilter'
import { CreatorDialog } from './creator/CreatorDialog'

export function CreatorDetailPage() {
  const { t } = useI18n()
  const id = Number(useParams().id)
  const [params] = useSearchParams()
  const from = params.get('from') ?? ''
  const to = params.get('to') ?? ''
  const creator = useAsync(() => getCreator(id), [id])
  const stats = useAsync(() => creatorCampaignStats(id, { from: from || null, to: to || null }), [id, from, to])
  const [editing, setEditing] = useState(false)
  const [confirmArchive, setConfirmArchive] = useState(false)
  const c = creator.data

  return (
    <>
      <nav className="crumbs" aria-label={t('common.breadcrumb')}>
        <Link to="/creators">{t('nav.creators')}</Link>
      </nav>
      <PageHeader
        title={c?.name ?? (creator.loading ? t('common.loading') : t('common.creator'))}
        actions={
          c && (
            <>
              <Button onClick={() => setEditing(true)}>{t('common.edit')}</Button>
              {c.status === 'active' && <Button onClick={() => setConfirmArchive(true)}>{t('common.archive')}</Button>}
            </>
          )
        }
      >
        {c && (
          <p className="page-meta">
            <CreatorStatusBadge status={c.status} />
            {c.handle && <span translate="no">{c.handle}</span>}
            {c.memo && <span>{c.memo}</span>}
          </p>
        )}
      </PageHeader>
      <ErrorMessage error={creator.error} onRetry={creator.reload} />
      {c?.status === 'archived' && <p className="note">{t('creatorDetail.archivedNote')}</p>}

      {c && (
        <>
          <h2 className="section-title">{t('creatorDetail.byCampaign')}</h2>
          <PeriodFilter />
          <ErrorMessage error={stats.error} onRetry={stats.reload} />
          {stats.data && stats.data.campaigns.length === 0 ? (
            <EmptyState message={t('creatorDetail.empty')} />
          ) : (
            !stats.error && (
              <TableWrap label={t('creatorDetail.byCampaign')}>
                <table className={`table${stats.loading ? ' is-stale' : ''}`}>
                  <caption className="visually-hidden">{t('creatorDetail.byCampaign')}</caption>
                  <thead>
                    <tr>
                      <th scope="col">{t('common.campaign')}</th>
                      <th scope="col">{t('common.status')}</th>
                      <th scope="col" className="num">{t('common.visits')}</th>
                      <th scope="col" className="num">{t('common.visitors')}</th>
                      <th scope="col" className="num">{t('common.leads')}</th>
                      <th scope="col" className="num">{t('common.convertedVisitors')}</th>
                      <th scope="col" className="num">{t('common.conversionRate')}</th>
                    </tr>
                  </thead>
                  <tbody aria-busy={stats.loading}>
                    {!stats.data ? (
                      stats.loading && <SkeletonRows columns={7} rows={2} />
                    ) : (
                      stats.data.campaigns.map((row) => (
                        <tr key={row.campaignId}>
                          <td>
                            <Link to={`/campaigns/${row.campaignId}`} className="row-link">
                              {row.name}
                            </Link>
                          </td>
                          <td>
                            <StatusBadge status={row.status} />
                          </td>
                          <td className="num">{formatNumber(row.visits)}</td>
                          <td className="num">{formatNumber(row.visitors)}</td>
                          <td className="num">{formatNumber(row.leads)}</td>
                          <td className="num">{formatNumber(row.convertedVisitors)}</td>
                          <td className="num">{formatPercent(row.conversionRate)}</td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </TableWrap>
            )
          )}
          <p className="definition">
            {t('creatorDetail.definition')}
          </p>
        </>
      )}

      <CreatorDialog
        open={editing}
        title={t('creatorDetail.editTitle')}
        submitLabel={t('common.save')}
        initial={c}
        onClose={() => setEditing(false)}
        onSubmit={async (v) => {
          await updateCreator(id, v)
          setEditing(false)
          creator.reload()
        }}
      />
      <ConfirmDialog
        open={confirmArchive}
        title={t('creatorDetail.archiveTitle')}
        message={t('creatorDetail.archiveMessage')}
        confirmLabel={t('common.archiveConfirm')}
        onConfirm={async () => {
          await archiveCreator(id)
          creator.reload()
        }}
        onClose={() => setConfirmArchive(false)}
      />
    </>
  )
}
