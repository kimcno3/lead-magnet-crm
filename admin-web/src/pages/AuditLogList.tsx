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
import { AUDIT_ACTION_LABELS } from '../labels'

const DATE = /^\d{4}-\d{2}-\d{2}$/

function toAction(value: string | null): AuditAction | null {
  return value && (AUDIT_ACTIONS as readonly string[]).includes(value) ? (value as AuditAction) : null
}

function targetText(log: AuditLog): string {
  if (log.targetType === 'lead') return `신청 #${log.targetId}`
  if (log.targetType === 'operator') return log.targetId === null ? '-' : `운영자 #${log.targetId}`
  if (log.targetType === 'campaign') return '명단'
  return log.targetId === null ? log.targetType : `${log.targetType} #${log.targetId}`
}

export function AuditLogListPage() {
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
  const columns = 7

  return (
    <>
      <PageHeader title="감사 로그" />
      <p className="note">
        명단 조회, 원문 열람, CSV 다운로드, 로그인 성공·실패가 기록됩니다. 기록은 수정하거나 지울 수 없고, 신청자 원문 개인정보는 남기지 않습니다.
      </p>
      <div className="toolbar" role="group" aria-label="감사 로그 필터">
        <Field label="행동">
          {(p) => (
            <select {...p} value={action ?? ''} onChange={(e) => update({ action: e.target.value, page: null })}>
              <option value="">전체 행동</option>
              {AUDIT_ACTIONS.map((a) => (
                <option key={a} value={a}>
                  {AUDIT_ACTION_LABELS[a]}
                </option>
              ))}
            </select>
          )}
        </Field>
        <Field label="캠페인">
          {(p) => (
            <select {...p} value={campaignId ?? ''} onChange={(e) => update({ campaign: e.target.value, page: null })}>
              <option value="">전체 캠페인</option>
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
            총 <span className="num">{formatNumber(data.totalElements)}</span>건
          </p>
        )}
      </div>
      <PeriodFilter />

      <ErrorMessage error={error} onRetry={reload} />
      {data && data.totalElements > 0 && data.items.length === 0 ? (
        <EmptyState
          message="이 페이지에는 기록이 없습니다. 마지막 페이지를 넘었습니다."
          action={
            <Button size="sm" onClick={() => update({ page: null })}>
              첫 페이지로
            </Button>
          }
        />
      ) : data && data.totalElements === 0 ? (
        <EmptyState
          message={filtered ? '조건에 맞는 기록이 없습니다. 필터를 바꿔 보세요.' : '아직 감사 기록이 없습니다.'}
          action={
            filtered ? (
              <Button size="sm" onClick={() => update({ action: null, campaign: null, from: null, to: null, page: null })}>
                필터 지우기
              </Button>
            ) : undefined
          }
        />
      ) : (
        !(error && !data) && (
          <>
            <TableWrap label="감사 로그">
              <table className={`table${loading && data ? ' is-stale' : ''}`}>
                <caption className="visually-hidden">감사 로그</caption>
                <thead>
                  <tr>
                    <th scope="col">시각</th>
                    <th scope="col">운영자</th>
                    <th scope="col">행동</th>
                    <th scope="col">대상</th>
                    <th scope="col">캠페인</th>
                    <th scope="col">내용</th>
                    <th scope="col">IP</th>
                  </tr>
                </thead>
                <tbody aria-busy={loading}>
                  {!data && loading ? (
                    <SkeletonRows columns={columns} />
                  ) : (
                    data?.items.map((log) => (
                      <tr key={log.id}>
                        <td className="num nowrap">{formatDateTime(log.createdAt)}</td>
                        <td className="break" translate="no">
                          {log.operatorEmail ?? (log.operatorId === null ? <span className="muted">-</span> : `#${log.operatorId}`)}
                        </td>
                        <td className="nowrap">{AUDIT_ACTION_LABELS[log.action] ?? log.action}</td>
                        <td className="nowrap">{targetText(log)}</td>
                        <td>
                          {log.campaignId === null ? (
                            <span className="muted">-</span>
                          ) : (
                            (campaignNames.get(log.campaignId) ?? `#${log.campaignId}`)
                          )}
                        </td>
                        <td className="detail-list">{summarizeAuditDetail(log.detail)}</td>
                        <td className="num nowrap" title={log.userAgent ?? undefined} translate="no">
                          {log.ip ?? <span className="muted">-</span>}
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </TableWrap>
            {totalPages > 0 && (
              <nav className="pager" aria-label="감사 로그 페이지">
                <ul className="pager-list">
                  <li>
                    <Button size="sm" disabled={page <= 0} onClick={() => update({ page: page - 1 > 0 ? String(page - 1) : null })} aria-label="이전 페이지">
                      이전
                    </Button>
                  </li>
                  <li className="num" aria-current="page">
                    {page + 1} / {totalPages} 페이지
                  </li>
                  <li>
                    <Button size="sm" disabled={page + 1 >= totalPages} onClick={() => update({ page: String(page + 1) })} aria-label="다음 페이지">
                      다음
                    </Button>
                  </li>
                </ul>
                <span className="muted small">페이지당 {AUDIT_PAGE_SIZE}건</span>
              </nav>
            )}
          </>
        )
      )}
    </>
  )
}
