import { useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { dashboardStats, downloadDashboardCsv } from '../api/stats'
import type { Dashboard } from '../api/types'
import { Button } from '../components/Button'
import { EmptyState } from '../components/EmptyState'
import { ErrorMessage } from '../components/ErrorMessage'
import { PageHeader } from '../components/PageHeader'
import { PeriodFilter } from '../components/PeriodFilter'
import { SkeletonRows, TableWrap } from '../components/Table'
import { addDays, formatDateTime, formatNumber, formatPercent, seoulToday } from '../format'
import { useAsync } from '../hooks'
import { useI18n } from '../i18n/context'
import type { TKey } from '../i18n/translate'
import { channelLabel, selfLinkLabel } from '../labels'
import { FormSheet } from './dashboard/FormSheet'
import { TrendChart } from './dashboard/TrendChart'

const QUICK_DAYS = [7, 30, 90] as const
const KPI_LABELS: TKey[] = ['common.visits', 'common.visitors', 'common.leads', 'common.conversionRate']

function kpiValues(d: Dashboard): string[] {
  return [
    formatNumber(d.totals.visits),
    formatNumber(d.totals.visitors),
    formatNumber(d.totals.leads),
    formatPercent(d.totals.conversionRate),
  ]
}

/**
 * 유입 현황 대시보드(로그인 뒤 첫 화면). 기간은 URL 쿼리 from·to이고 비우면 서버가 서울 오늘까지 30일로 채운다.
 * 위에서부터 기간 필터 → KPI → 일별 추이 → 폼 시트(CSV) → 채널·크리에이터 → 최근 신청(가린 이름).
 */
export function DashboardPage() {
  const { t } = useI18n()
  const [params, setParams] = useSearchParams()
  const from = params.get('from') ?? ''
  const to = params.get('to') ?? ''
  const period = { from: from || null, to: to || null }
  const { data, error, loading, reload } = useAsync(() => dashboardStats(period), [from, to])
  const [downloading, setDownloading] = useState(false)
  const [downloadError, setDownloadError] = useState<unknown>(null)

  const today = seoulToday()
  function quick(days: number) {
    setParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        next.set('from', addDays(today, -(days - 1)))
        next.set('to', today)
        return next
      },
      { replace: true },
    )
  }

  async function download() {
    setDownloading(true)
    setDownloadError(null)
    try {
      await downloadDashboardCsv(period)
    } catch (e) {
      setDownloadError(e)
    } finally {
      setDownloading(false)
    }
  }

  const failed = Boolean(error) && !data
  const stale = loading && Boolean(data)

  return (
    <>
      <PageHeader title={t('dashboard.title')} />
      <p className="note">{t('dashboard.note')}</p>

      <PeriodFilter emptyLabel={t('dashboard.defaultPeriod')}>
        <div className="quick-periods" role="group" aria-label={t('dashboard.quickPeriods')}>
          {QUICK_DAYS.map((days) => (
            <Button
              key={days}
              size="sm"
              aria-pressed={from === addDays(today, -(days - 1)) && to === today}
              onClick={() => quick(days)}
            >
              {t('dashboard.lastDays', { count: days })}
            </Button>
          ))}
        </div>
      </PeriodFilter>
      {data && (
        <p className="muted small" aria-live="polite">
          {t('dashboard.appliedPeriod', { from: data.from, to: data.to })}
        </p>
      )}

      <ErrorMessage error={error} onRetry={reload} />
      {!failed && (
        <>
          <ul className={`kpis kpis-4${stale ? ' is-stale' : ''}`} aria-label={t('stats.kpis')} aria-busy={loading}>
            {KPI_LABELS.map((key, i) => (
              <li key={key} className="kpi">
                <span className="kpi-label">{t(key)}</span>
                <span className="kpi-value">{data ? kpiValues(data)[i] : <span className="skeleton skeleton-kpi" />}</span>
              </li>
            ))}
          </ul>
          <p className="definition">{t('dashboard.definition')}</p>

          <h2 className="section-title">{t('dashboard.trend')}</h2>
          <div className={stale ? 'is-stale' : undefined}>
            {data ? <TrendChart days={data.daily} /> : <div className="trend-placeholder skeleton" aria-hidden="true" />}
          </div>

          <div className="section-head">
            <h2 className="section-title">{t('dashboard.sheet')}</h2>
            <Button size="sm" busy={downloading} disabled={!data} onClick={download}>
              {t('dashboard.downloadCsv')}
            </Button>
          </div>
          <ErrorMessage error={downloadError} />
          {!data ? (
            loading && (
              <TableWrap label={t('dashboard.sheetTable')}>
                <table className="table table-compact">
                  <tbody aria-busy="true">
                    <SkeletonRows columns={8} />
                  </tbody>
                </table>
              </TableWrap>
            )
          ) : data.forms.length === 0 ? (
            <EmptyState message={t('dashboard.sheetEmpty')} />
          ) : (
            <>
              <div className={stale ? 'is-stale' : undefined}>
                <FormSheet forms={data.forms} totals={data.totals} />
              </div>
              <p className="definition">
                {t('dashboard.sortHint')} {t('dashboard.totalNote')}
              </p>
            </>
          )}

          {data && <Breakdowns data={data} stale={stale} />}

          <h2 className="section-title">{t('dashboard.recentLeads')}</h2>
          {data &&
            (data.recentLeads.length === 0 ? (
              <EmptyState message={t('dashboard.recentEmpty')} />
            ) : (
              <ul className={`recent-list${stale ? ' is-stale' : ''}`} aria-label={t('dashboard.recentLeads')}>
                {data.recentLeads.map((l) => (
                  <li key={l.id}>
                    <Link className="row-link" to={`/campaigns/${l.campaignId}?tab=leads`}>
                      {l.name ?? t('dashboard.noName')}
                    </Link>
                    <span className="recent-meta">
                      {[l.campaignName, l.formTitle, channelLabel(l.channel), l.creatorName].filter(Boolean).join(' · ')}
                    </span>
                    <time className="recent-time" dateTime={l.createdAt}>
                      {formatDateTime(l.createdAt)}
                    </time>
                  </li>
                ))}
              </ul>
            ))}
        </>
      )}
    </>
  )
}

/** 채널별·크리에이터별 표. 넓은 화면에서는 나란히, 좁으면 위아래로 쌓는다. */
function Breakdowns({ data, stale }: { data: Dashboard; stale: boolean }) {
  const { t } = useI18n()
  const head = (first: TKey) => (
    <thead>
      <tr>
        <th scope="col">{t(first)}</th>
        <th scope="col" className="num shrink">{t('common.visits')}</th>
        <th scope="col" className="num shrink">{t('common.visitors')}</th>
        <th scope="col" className="num shrink">{t('common.leads')}</th>
        <th scope="col" className="num shrink">{t('common.conversionRate')}</th>
      </tr>
    </thead>
  )
  const cells = (m: Dashboard['totals']) => (
    <>
      <td className="num">{formatNumber(m.visits)}</td>
      <td className="num">{formatNumber(m.visitors)}</td>
      <td className="num">{formatNumber(m.leads)}</td>
      <td className="num">{formatPercent(m.conversionRate)}</td>
    </>
  )
  return (
    <div className={`dash-split${stale ? ' is-stale' : ''}`}>
      <section>
        <h2 className="section-title">{t('stats.byChannel')}</h2>
        <TableWrap label={t('stats.byChannelTable')}>
          <table className="table table-compact">
            <caption className="visually-hidden">{t('stats.byChannelTable')}</caption>
            {head('common.channel')}
            <tbody>
              {data.channels.map((c) => (
                <tr key={c.channel}>
                  <td>{channelLabel(c.channel)}</td>
                  {cells(c)}
                </tr>
              ))}
            </tbody>
          </table>
        </TableWrap>
      </section>
      <section>
        <h2 className="section-title">{t('stats.byCreator')}</h2>
        <TableWrap label={t('stats.byCreatorTable')}>
          <table className="table table-compact">
            <caption className="visually-hidden">{t('stats.byCreatorTable')}</caption>
            {head('common.creator')}
            <tbody>
              {data.creators.map((c) => (
                <tr key={c.creator?.id ?? 'self'}>
                  <td>
                    {c.creator ? (
                      <Link className="row-link" to={`/creators/${c.creator.id}`}>
                        {c.creator.name}
                      </Link>
                    ) : (
                      selfLinkLabel()
                    )}
                  </td>
                  {cells(c)}
                </tr>
              ))}
            </tbody>
          </table>
        </TableWrap>
      </section>
    </div>
  )
}
