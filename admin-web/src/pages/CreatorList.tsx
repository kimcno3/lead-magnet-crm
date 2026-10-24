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
import { useI18n } from '../i18n/context'
import { CreatorDialog } from './creator/CreatorDialog'

export function CreatorListPage() {
  const navigate = useNavigate()
  const { t } = useI18n()
  const { data, error, loading, reload } = useAsync(listCreators, [])
  const [creating, setCreating] = useState(false)

  const newButton = (
    <Button variant="primary" onClick={() => setCreating(true)}>
      {t('creatorList.new')}
    </Button>
  )

  return (
    <>
      <PageHeader title={t('nav.creators')} actions={newButton} />
      <p className="note">{t('creatorList.note')}</p>
      <ErrorMessage error={error} onRetry={reload} />
      {data && data.length === 0 ? (
        <EmptyState message={t('creatorList.empty')} />
      ) : (
        !error && (
          <TableWrap label={t('creatorList.tableLabel')}>
            <table className="table">
              <caption className="visually-hidden">{t('creatorList.tableLabel')}</caption>
              <thead>
                <tr>
                  <th scope="col">{t('common.name')}</th>
                  <th scope="col">{t('creatorList.handle')}</th>
                  <th scope="col">{t('common.status')}</th>
                  <th scope="col">{t('creatorList.createdAt')}</th>
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
        title={t('creatorList.new')}
        submitLabel={t('creatorList.register')}
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
