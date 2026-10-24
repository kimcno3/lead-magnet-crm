import { useSearchParams } from 'react-router'
import { AUDIT_PAGE_SIZE, listAuditLogs } from '../api/audit'
import { listCampaigns } from '../api/campaigns'
import { AUDIT_ACTIONS, type AuditAction, type AuditLog } from '../api/types'
import { Button } from '../components/Button'
import { EmptyState } from '../components/EmptyState'
import { ErrorMessage } from '../components/ErrorMessage'
import { Field } from '../components/Field'
import { PageHeader } from '../components/PageHeader'
import { PeriodFilter } from '../components/PeriodFilter'
import { SkeletonRows, TableWrap } from '../components/Table'
import { formatDateTime, formatNumber, summarizeAuditDetail } from '../format'
import { useAsync } from '../hooks'
import { useI18n } from '../i18n/context'
import { translate } from '../i18n/translate'
import { auditActionLabel } from '../labels'

const DATE = /^\d{4}-\d{2}-\d{2}$/

function toAction(value: string | null): AuditAction | null {
  return value && (AUDIT_ACTIONS as readonly string[]).includes(value) ? (value as AuditAction) : null
}

const UA_PREVIEW = 32

function truncate(text: string, max: number): string {
  return text.length > max ? `${text.slice(0, max)}…` : text
}

function targetText(log: AuditLog): string {
  if (log.targetType === 'lead') return translate('auditLog.targetLead', { id: String(log.targetId) })
  if (log.targetType === 'operator') return log.targetId === null ? '-' : translate('auditLog.targetOperator', { id: String(log.targetId) })
  if (log.targetType === 'campaign') return translate('auditLog.targetLeadList')
  return log.targetId === null ? log.targetType : `${log.targetType} #${log.targetId}`
}

export function AuditLogListPage() {
  const { t, rich } = useI18n()
  const [params, setParams] = useSearchParams()
  const page = Math.max(0, Number(params.get('page')) || 0)
  const action = toAction(params.get('action'))
  const campaignId = Number(params.get('campaign')) || null
  const from = DATE.test(params.get('from') ?? '') ? params.get('from') : null
  const to = DATE.test(params.get('to') ?? '') ? params.get('to') : null
  const filtered = Boolean(action || campaignId || from || to)

  const { data, error, loading, reload } = useAsync(
    () => listAuditLogs({ page, action, campaignId, from, to }),
    [page, action, campaignId, from, to],
  )
  // 캠페인 이름은 필터와 표에만 쓴다. 불러오지 못해도 기록은 id로 보여준다.
  const campaigns = useAsync(listCampaigns, [])
  const campaignNames = new Map((campaigns.data ?? []).map((c) => [c.id, c.name]))

  function update(changes: Record<string, string | null>) {
    setParams((prev) => {
      const next = new URLSearchParams(prev)
      for (const [k, v] of Object.entries(changes)) {
        if (v) next.set(k, v)
        else next.delete(k)
      }
      return next
    })
  }

  const totalPages = data?.totalPages ?? 0
  const columns = 8

  return (
    <>
      <PageHeader title={t('auditLog.title')} />
      <p className="note">
        {t('auditLog.note')}
      </p>
      <div className="toolbar" role="group" aria-label={t('auditLog.filters')}>
        <Field label={t('auditLog.action')}>
          {(p) => (
            <select {...p} value={action ?? ''} onChange={(e) => update({ action: e.target.value, page: null })}>
              <option value="">{t('auditLog.allActions')}</option>
              {AUDIT_ACTIONS.map((a) => (
                <option key={a} value={a}>
                  {auditActionLabel(a)}
                </option>
              ))}
            </select>
          )}
        </Field>
        <Field label={t('common.campaign')}>
          {(p) => (
            <select {...p} value={campaignId ?? ''} onChange={(e) => update({ campaign: e.target.value, page: null })}>
              <option value="">{t('auditLog.allCampaigns')}</option>
              {campaigns.data?.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          )}
        </Field>
        {data && (
          <p className="toolbar-note muted small" aria-live="polite">
            {rich('auditLog.total', { count: <span key="count" className="num">{formatNumber(data.totalElements)}</span> }, data.totalElements)}
          </p>
        )}
      </div>
      <PeriodFilter resetParams={['page']} />

      <ErrorMessage error={error} onRetry={reload} />
      {data && data.totalElements > 0 && data.items.length === 0 ? (
        <EmptyState
          message={t('auditLog.pastLastPage')}
          action={
            <Button size="sm" onClick={() => update({ page: null })}>
              {t('common.firstPage')}
            </Button>
          }
        />
      ) : data && data.totalElements === 0 ? (
        <EmptyState
          message={filtered ? t('auditLog.noMatch') : t('auditLog.empty')}
          action={
            filtered ? (
              <Button size="sm" onClick={() => update({ action: null, campaign: null, from: null, to: null, page: null })}>
                {t('common.clearFilters')}
              </Button>
            ) : undefined
          }
        />
      ) : (
        !(error && !data) && (
          <>
            <TableWrap label={t('auditLog.title')}>
              <table className={`table${loading && data ? ' is-stale' : ''}`}>
                <caption className="visually-hidden">{t('auditLog.title')}</caption>
                <thead>
                  <tr>
                    <th scope="col">{t('auditLog.time')}</th>
                    <th scope="col">{t('auditLog.operator')}</th>
                    <th scope="col">{t('auditLog.action')}</th>
                    <th scope="col">{t('auditLog.target')}</th>
                    <th scope="col">{t('common.campaign')}</th>
                    <th scope="col">{t('auditLog.detail')}</th>
                    <th scope="col">IP</th>
                    <th scope="col">{t('auditLog.browser')}</th>
                  </tr>
                </thead>
                <tbody aria-busy={loading}>
                  {!data && loading ? (
                    <SkeletonRows columns={columns} />
                  ) : (
                    data?.items.map((log) => (
                      <tr key={log.id}>
                        <td className="num nowrap">{formatDateTime(log.createdAt)}</td>
                        <td className="email" translate="no">
                          {log.operatorEmail ?? (log.operatorId === null ? <span className="muted">-</span> : `#${log.operatorId}`)}
                        </td>
                        <td className="nowrap">{auditActionLabel(log.action)}</td>
                        <td className="nowrap">{targetText(log)}</td>
                        <td className="text-cell">
                          {log.campaignId === null ? (
                            <span className="muted">-</span>
                          ) : (
                            (campaignNames.get(log.campaignId) ?? `#${log.campaignId}`)
                          )}
                        </td>
                        <td className="detail-list">{summarizeAuditDetail(log.detail)}</td>
                        <td className="num nowrap" translate="no">
                          {log.ip ?? <span className="muted">-</span>}
                        </td>
                        <td className="user-agent" translate="no">
                          {log.userAgent ? (
                            <details>
                              <summary>{truncate(log.userAgent, UA_PREVIEW)}</summary>
                              <p className="detail-list">{log.userAgent}</p>
                            </details>
                          ) : (
                            <span className="muted">-</span>
                          )}
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </TableWrap>
            {totalPages > 0 && (
              <nav className="pager" aria-label={t('auditLog.pager')}>
                <ul className="pager-list">
                  <li>
                    <Button size="sm" disabled={page <= 0} onClick={() => update({ page: page - 1 > 0 ? String(page - 1) : null })} aria-label={t('common.prevPage')}>
                      {t('common.prev')}
                    </Button>
                  </li>
                  <li className="num" aria-current="page">
                    {t('common.pageOf', { page: page + 1, total: totalPages })}
                  </li>
                  <li>
                    <Button size="sm" disabled={page + 1 >= totalPages} onClick={() => update({ page: String(page + 1) })} aria-label={t('common.nextPage')}>
                      {t('common.next')}
                    </Button>
                  </li>
                </ul>
                <span className="muted small">{t('auditLog.pageSize', { size: AUDIT_PAGE_SIZE })}</span>
              </nav>
            )}
          </>
        )
      )}
    </>
  )
}
