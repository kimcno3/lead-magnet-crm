import { useSearchParams } from 'react-router'
import { campaignStats, channelStats } from '../../api/stats'
import { ErrorMessage } from '../../components/ErrorMessage'
import { PeriodFilter } from '../../components/PeriodFilter'
import { SkeletonRows, TableWrap } from '../../components/Table'
import { formatNumber, formatPercent } from '../../format'
import { useAsync } from '../../hooks'
import { CHANNEL_LABELS } from '../../labels'
import { CreatorStatsTable } from './CreatorStatsTable'

export function StatsTab({ campaignId }: { campaignId: number }) {
  const [params] = useSearchParams()
  const from = params.get('from') ?? ''
  const to = params.get('to') ?? ''
  const period = { from: from || null, to: to || null }

  const total = useAsync(() => campaignStats(campaignId, period), [campaignId, from, to])
  const byChannel = useAsync(() => channelStats(campaignId, period), [campaignId, from, to])

  const kpis = total.data
    ? [
        ['방문', formatNumber(total.data.visits)],
        ['방문자', formatNumber(total.data.visitors)],
        ['신청', formatNumber(total.data.leads)],
        ['전환 방문자', formatNumber(total.data.convertedVisitors)],
        ['전환율', formatPercent(total.data.conversionRate)],
      ]
    : null
  const maxLeads = Math.max(1, ...(byChannel.data?.channels.map((c) => c.leads) ?? [0]))

  return (
    <>
      <PeriodFilter />

      <ErrorMessage error={total.error} onRetry={total.reload} />
      <ul className={`kpis${total.loading ? ' is-stale' : ''}`} aria-label="핵심 지표" aria-busy={total.loading}>
        {kpis
          ? kpis.map(([label, value]) => (
              <li key={label} className="kpi">
                <span className="kpi-label">{label}</span>
                <span className="kpi-value">{value}</span>
              </li>
            ))
          : ['방문', '방문자', '신청', '전환 방문자', '전환율'].map((label) => (
              <li key={label} className="kpi">
                <span className="kpi-label">{label}</span>
                <span className="kpi-value">
                  <span className="skeleton skeleton-kpi" />
                </span>
              </li>
            ))}
      </ul>
      <p className="definition">
        방문은 공개 폼 페이지 조회 수, 방문자는 고유 방문자 수입니다. 전환율 = 신청한 방문자 / 방문자.
      </p>

      <h2 className="section-title">채널별</h2>
      <ErrorMessage error={byChannel.error} onRetry={byChannel.reload} />
      {!byChannel.error && (
        <>
      <TableWrap label="채널별 성과">
        <table className={`table${byChannel.loading ? ' is-stale' : ''}`}>
          <caption className="visually-hidden">채널별 성과</caption>
          <thead>
            <tr>
              <th scope="col">채널</th>
              <th scope="col" className="num">방문</th>
              <th scope="col" className="num">방문자</th>
              <th scope="col" className="num">신청</th>
              <th scope="col" className="num">전환율</th>
              <th scope="col" className="bar-col">
                신청 수 비교
              </th>
            </tr>
          </thead>
          <tbody>
            {!byChannel.data ? (
              byChannel.loading && <SkeletonRows columns={6} rows={5} />
            ) : (
              byChannel.data.channels.map((c) => (
                <tr key={c.channel}>
                  <td>{CHANNEL_LABELS[c.channel]}</td>
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
