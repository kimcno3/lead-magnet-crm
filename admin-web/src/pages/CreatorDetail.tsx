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
import { PeriodFilter } from '../components/PeriodFilter'
import { CreatorDialog } from './creator/CreatorDialog'

export function CreatorDetailPage() {
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
      <nav className="crumbs" aria-label="경로">
        <Link to="/creators">크리에이터</Link>
      </nav>
      <PageHeader
        title={c?.name ?? (creator.loading ? '불러오는 중…' : '크리에이터')}
        actions={
          c && (
            <>
              <Button onClick={() => setEditing(true)}>수정</Button>
              {c.status === 'active' && <Button onClick={() => setConfirmArchive(true)}>보관</Button>}
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
      {c?.status === 'archived' && <p className="note">보관된 크리에이터입니다. 새 링크를 만들 수 없고, 이미 만든 링크와 성과는 그대로 남습니다.</p>}

      {c && (
        <>
          <h2 className="section-title">캠페인별 성과</h2>
          <PeriodFilter />
          <ErrorMessage error={stats.error} onRetry={stats.reload} />
          {stats.data && stats.data.campaigns.length === 0 ? (
            <EmptyState message="아직 이 크리에이터의 배포 링크가 없습니다. 캠페인의 폼 탭에서 크리에이터 링크를 만드세요." />
          ) : (
            !stats.error && (
              <TableWrap label="캠페인별 성과">
                <table className={`table${stats.loading ? ' is-stale' : ''}`}>
                  <caption className="visually-hidden">캠페인별 성과</caption>
                  <thead>
                    <tr>
                      <th scope="col">캠페인</th>
                      <th scope="col">상태</th>
                      <th scope="col" className="num">방문</th>
                      <th scope="col" className="num">방문자</th>
                      <th scope="col" className="num">신청</th>
                      <th scope="col" className="num">전환 방문자</th>
                      <th scope="col" className="num">전환율</th>
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
            이 크리에이터의 배포 링크로 들어온 방문과 신청만 셉니다. 전환 방문자는 같은 캠페인에서 이 크리에이터 링크로 방문하고 신청한
            사람이며, 캠페인 행끼리 방문자를 더하지 않습니다.
          </p>
        </>
      )}

      <CreatorDialog
        open={editing}
        title="크리에이터 수정"
        submitLabel="저장"
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
        title="크리에이터 보관"
        message="보관하면 이 크리에이터로 새 배포 링크를 만들 수 없습니다. 이미 만든 링크는 계속 동작하고 성과도 그대로 남습니다. 되돌릴 수 없습니다."
        confirmLabel="보관하기"
        onConfirm={async () => {
          await archiveCreator(id)
          creator.reload()
        }}
        onClose={() => setConfirmArchive(false)}
      />
    </>
  )
}
