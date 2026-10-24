import { Fragment, useState } from 'react'
import { Link } from 'react-router'
import { creatorStats, type Period } from '../../api/stats'
import { CreatorStatusBadge } from '../../components/Badge'
import { ErrorMessage } from '../../components/ErrorMessage'
import { SkeletonRows, TableWrap } from '../../components/Table'
import { formatNumber, formatPercent } from '../../format'
import { useAsync } from '../../hooks'
import { useI18n } from '../../i18n/context'
import { channelLabel, selfLinkLabel } from '../../labels'

/**
 * 캠페인의 크리에이터별 성과(ADR 0014). 마지막 행은 크리에이터 없는 링크와 direct를 묶은 "직접·자체 링크"다.
 * 행마다 채널별 보기를 펼치면 같은 크리에이터·같은 채널 안의 지표가 아래 행으로 붙는다.
 */
export function CreatorStatsTable({ campaignId, period }: { campaignId: number; period: Period }) {
  const { t } = useI18n()
  const selfLink = selfLinkLabel()
  const result = useAsync(() => creatorStats(campaignId, period), [campaignId, period.from, period.to])
  const [open, setOpen] = useState<ReadonlySet<string>>(new Set())

  function toggle(key: string) {
    setOpen((prev) => {
      const next = new Set(prev)
      if (next.has(key)) next.delete(key)
      else next.add(key)
      return next
    })
  }

  return (
    <>
      <h2 className="section-title">{t('stats.byCreator')}</h2>
      <ErrorMessage error={result.error} onRetry={result.reload} />
      {!result.error && (
        <TableWrap label={t('stats.byCreatorTable')}>
          <table className={`table${result.loading ? ' is-stale' : ''}`}>
            <caption className="visually-hidden">{t('stats.byCreatorTable')}</caption>
            <thead>
              <tr>
                <th scope="col">{t('common.creator')}</th>
                <th scope="col" className="num">{t('common.visits')}</th>
                <th scope="col" className="num">{t('common.visitors')}</th>
                <th scope="col" className="num">{t('common.leads')}</th>
                <th scope="col" className="num">{t('common.convertedVisitors')}</th>
                <th scope="col" className="num">{t('common.conversionRate')}</th>
              </tr>
            </thead>
            <tbody>
              {!result.data
                ? result.loading && <SkeletonRows columns={6} rows={2} />
                : result.data.creators.map((row) => {
                    const key = row.creator ? String(row.creator.id) : 'self'
                    const name = row.creator?.name ?? selfLink
                    const expanded = open.has(key)
                    return (
                      <Fragment key={key}>
                        <tr className={expanded ? 'is-open' : undefined}>
                          <td>
                            <span className="inline-row">
                              <button
                                type="button"
                                className="disclosure"
                                aria-expanded={expanded}
                                aria-label={t('stats.expandChannels', { name })}
                                onClick={() => toggle(key)}
                              >
                                <span className="disclosure-mark" aria-hidden="true">
                                  {expanded ? '−' : '+'}
                                </span>
                              </button>
                              {row.creator ? (
                                <Link to={`/creators/${row.creator.id}`} className="row-link">
                                  {row.creator.name}
                                </Link>
                              ) : (
                                <span>{selfLink}</span>
                              )}
                              {row.creator?.status === 'archived' && <CreatorStatusBadge status="archived" />}
                            </span>
                          </td>
                          <td className="num">{formatNumber(row.visits)}</td>
                          <td className="num">{formatNumber(row.visitors)}</td>
                          <td className="num">{formatNumber(row.leads)}</td>
                          <td className="num">{formatNumber(row.convertedVisitors)}</td>
                          <td className="num">{formatPercent(row.conversionRate)}</td>
                        </tr>
                        {expanded &&
                          row.channels.map((c) => (
                            <tr key={`${key}-${c.channel}`} className="sub-row">
                              <th scope="row">{channelLabel(c.channel)}</th>
                              <td className="num">{formatNumber(c.visits)}</td>
                              <td className="num">{formatNumber(c.visitors)}</td>
                              <td className="num">{formatNumber(c.leads)}</td>
                              <td className="num">{formatNumber(c.convertedVisitors)}</td>
                              <td className="num">{formatPercent(c.conversionRate)}</td>
                            </tr>
                          ))}
                      </Fragment>
                    )
                  })}
            </tbody>
          </table>
        </TableWrap>
      )}
      <p className="definition">
        {t('stats.creatorDefinition', { selfLink })}
      </p>
    </>
  )
}
