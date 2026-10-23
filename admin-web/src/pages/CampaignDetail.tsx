import { useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { archiveCampaign, getCampaign } from '../api/campaigns'
import { StatusBadge } from '../components/Badge'
import { Button } from '../components/Button'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { ErrorMessage } from '../components/ErrorMessage'
import { PageHeader } from '../components/PageHeader'
import { Tabs, type TabItem } from '../components/Tabs'
import { formatPeriod } from '../format'
import { useAsync } from '../hooks'
import { FormsTab } from './campaign/FormsTab'
import { LeadsTab } from './campaign/LeadsTab'
import { StatsTab } from './campaign/StatsTab'

type TabKey = 'stats' | 'forms' | 'leads'
const TABS: readonly TabItem<TabKey>[] = [
  { key: 'stats', label: '성과' },
  { key: 'forms', label: '폼' },
  { key: 'leads', label: '명단' },
]

function isTab(value: string | null): value is TabKey {
  return value === 'stats' || value === 'forms' || value === 'leads'
}

export function CampaignDetailPage() {
  const id = Number(useParams().id)
  const [params, setParams] = useSearchParams()
  const tabParam = params.get('tab')
  const tab: TabKey = isTab(tabParam) ? tabParam : 'stats'
  const { data: campaign, error, loading, reload } = useAsync(() => getCampaign(id), [id])
  const [confirmArchive, setConfirmArchive] = useState(false)

  function selectTab(key: TabKey) {
    // 탭마다 쓰는 쿼리가 다르므로 탭을 바꾸면 탭 값만 남긴다.
    setParams(key === 'stats' ? {} : { tab: key })
  }

  return (
    <>
      <nav className="crumbs" aria-label="경로">
        <Link to="/campaigns">캠페인</Link>
      </nav>
      <PageHeader
        title={campaign?.name ?? (loading ? '불러오는 중…' : '캠페인')}
        actions={
          campaign?.status === 'active' && (
            <Button onClick={() => setConfirmArchive(true)}>보관</Button>
          )
        }
      >
        {campaign && (
          <p className="page-meta">
            <StatusBadge status={campaign.status} />
            <span className="num">{formatPeriod(campaign.startsAt, campaign.endsAt)}</span>
            {campaign.description && <span>{campaign.description}</span>}
          </p>
        )}
      </PageHeader>
      <ErrorMessage error={error} onRetry={reload} />
      {campaign && (
        <>
          <Tabs items={TABS} active={tab} onChange={selectTab} idPrefix="campaign" label="캠페인 보기" />
          <section
            role="tabpanel"
            id={`campaign-panel-${tab}`}
            aria-labelledby={`campaign-tab-${tab}`}
            className="tab-panel"
          >
            {tab === 'stats' && <StatsTab campaignId={id} />}
            {tab === 'forms' && <FormsTab campaign={campaign} onChanged={reload} />}
            {tab === 'leads' && <LeadsTab campaign={campaign} />}
          </section>
          <ConfirmDialog
            open={confirmArchive}
            title="캠페인 보관"
            message="보관하면 이 캠페인에 새 폼을 만들 수 없습니다. 이미 게시한 폼과 링크, 명단은 그대로 남습니다. 되돌릴 수 없습니다."
            confirmLabel="보관하기"
            onConfirm={async () => {
              await archiveCampaign(id)
              reload()
            }}
            onClose={() => setConfirmArchive(false)}
          />
        </>
      )}
    </>
  )
}
