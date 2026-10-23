import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { createCreator, listCreators } from '../api/creators'
import { CreatorStatusBadge } from '../components/Badge'
import { Button } from '../components/Button'
import { EmptyState } from '../components/EmptyState'
import { ErrorMessage } from '../components/ErrorMessage'
import { PageHeader } from '../components/PageHeader'
import { SkeletonRows, TableWrap } from '../components/Table'
import { formatDateTime } from '../format'
import { useAsync } from '../hooks'
import { CreatorDialog } from './creator/CreatorDialog'

export function CreatorListPage() {
  const navigate = useNavigate()
  const { data, error, loading, reload } = useAsync(listCreators, [])
  const [creating, setCreating] = useState(false)

  const newButton = (
    <Button variant="primary" onClick={() => setCreating(true)}>
      새 크리에이터
    </Button>
  )

  return (
    <>
      <PageHeader title="크리에이터" actions={newButton} />
      <p className="note">크리에이터를 등록한 뒤 폼의 배포 링크를 크리에이터별로 만들면, 캠페인 성과에서 크리에이터가 데려온 방문자와 신청을 따로 볼 수 있습니다.</p>
      <ErrorMessage error={error} onRetry={reload} />
      {data && data.length === 0 ? (
        <EmptyState message="아직 등록한 크리에이터가 없습니다. 수익 배분으로 협업하는 크리에이터를 등록해 보세요." />
      ) : (
        !error && (
          <TableWrap label="크리에이터 목록">
            <table className="table">
              <caption className="visually-hidden">크리에이터 목록</caption>
              <thead>
                <tr>
                  <th scope="col">이름</th>
                  <th scope="col">핸들</th>
                  <th scope="col">상태</th>
                  <th scope="col">등록일</th>
                </tr>
              </thead>
              <tbody aria-busy={loading}>
                {!data && loading ? (
                  <SkeletonRows columns={4} />
                ) : (
                  data?.map((c) => (
                    <tr key={c.id}>
                      <td>
                        <Link to={`/creators/${c.id}`} className="row-link">
                          {c.name}
                        </Link>
                      </td>
                      <td translate="no">{c.handle ?? <span className="muted">-</span>}</td>
                      <td>
                        <CreatorStatusBadge status={c.status} />
                      </td>
                      <td className="num">{formatDateTime(c.createdAt)}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </TableWrap>
        )
      )}
      <CreatorDialog
        open={creating}
        title="새 크리에이터"
        submitLabel="등록"
        onClose={() => setCreating(false)}
        onSubmit={async (v) => {
          const created = await createCreator({ name: v.name, handle: v.handle || null, memo: v.memo || null })
          setCreating(false)
          navigate(`/creators/${created.id}`)
        }}
      />
    </>
  )
}
