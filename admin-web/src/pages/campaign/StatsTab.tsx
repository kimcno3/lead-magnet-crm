import { useSearchParams } from 'react-router'
import { campaignStats, channelStats } from '../../api/stats'
import { ErrorMessage } from '../../components/ErrorMessage'
import { PeriodFilter } from '../../components/PeriodFilter'
import { SkeletonRows, TableWrap } from '../../components/Table'
import { formatNumber, formatPercent } from '../../format'
import { useAsync } from '../../hooks'
import { useI18n } from '../../i18n/context'
import type { TKey } from '../../i18n/translate'
import { channelLabel } from '../../labels'
import { CreatorStatsTable } from './CreatorStatsTable'

const KPI_LABELS: TKey[] = ['common.visits', 'common.visitors', 'common.leads', 'common.convertedVisitors', 'common.conversionRate']

export function StatsTab({ campaignId }: { campaignId: number }) {
  const { t } = useI18n()
  const [params] = useSearchParams()
  const from = params.get('from') ?? ''
  const to = params.get('to') ?? ''
  const period = { from: from || null, to: to || null }

  const total = useAsync(() => campaignStats(campaignId, period), [campaignId, from, to])
  const byChannel = useAsync(() => channelStats(campaignId, period), [campaignId, from, to])

  const kpis = total.data
    ? [
        formatNumber(total.data.visits),
        formatNumber(total.data.visitors),
        formatNumber(total.data.leads),
        formatNumber(total.data.convertedVisitors),
        formatPercent(total.data.conversionRate),
      ].map((value, i) => [t(KPI_LABELS[i]), value])
    : null
  const maxLeads = Math.max(1, ...(byChannel.data?.channels.map((c) => c.leads) ?? [0]))

  return (
    <>
      <PeriodFilter />

      <ErrorMessage error={total.error} onRetry={total.reload} />
      <ul className={`kpis${total.loading ? ' is-stale' : ''}`} aria-label={t('stats.kpis')} aria-busy={total.loading}>
        {kpis
          ? kpis.map(([label, value]) => (
              <li key={label} className="kpi">
                <span className="kpi-label">{label}</span>
                <span className="kpi-value">{value}</span>
              </li>
            ))
          : KPI_LABELS.map((key) => t(key)).map((label) => (
              <li key={label} className="kpi">
                <span className="kpi-label">{label}</span>
                <span className="kpi-value">
                  <span className="skeleton skeleton-kpi" />
                </span>
              </li>
            ))}
      </ul>
      <p className="definition">
        {t('stats.definition')}
      </p>

      <h2 className="section-title">{t('stats.byChannel')}</h2>
      <ErrorMessage error={byChannel.error} onRetry={byChannel.reload} />
      {!byChannel.error && (
        <>
      <TableWrap label={t('stats.byChannelTable')}>
        <table className={`table${byChannel.loading ? ' is-stale' : ''}`}>
          <caption className="visually-hidden">{t('stats.byChannelTable')}</caption>
          <thead>
            <tr>
              <th scope="col">{t('common.channel')}</th>
              <th scope="col" className="num">{t('common.visits')}</th>
              <th scope="col" className="num">{t('common.visitors')}</th>
              <th scope="col" className="num">{t('common.leads')}</th>
              <th scope="col" className="num">{t('common.conversionRate')}</th>
              <th scope="col" className="bar-col">
                {t('stats.compareLeads')}
              </th>
            </tr>
          </thead>
          <tbody>
            {!byChannel.data ? (
              byChannel.loading && <SkeletonRows columns={6} rows={5} />
            ) : (
              byChannel.data.channels.map((c) => (
                <tr key={c.channel}>
                  <td>{channelLabel(c.channel)}</td>
                  <td className="num">{formatNumber(c.visits)}</td>
                  <td className="num">{formatNumber(c.visitors)}</td>
                  <td className="num">{formatNumber(c.leads)}</td>
                  <td className="num">{formatPercent(c.conversionRate)}</td>
                  <td className="bar-col" aria-hidden="true">
                    <span className="bar" style={{ width: `${(c.leads / maxLeads) * 100}%` }} />
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </TableWrap>
        </>
      )}

      <CreatorStatsTable campaignId={campaignId} period={period} />
    </>
  )
}
