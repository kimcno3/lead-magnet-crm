import { useState, type FormEvent } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { ApiError } from '../api/client'
import { archiveCampaign, getCampaign, updateCampaign } from '../api/campaigns'
import type { Campaign } from '../api/types'
import { StatusBadge } from '../components/Badge'
import { Button } from '../components/Button'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { Dialog } from '../components/Dialog'
import { RetentionDaysField } from '../components/RetentionDaysField'
import { errorText } from '../errors'
import { RETENTION_DAYS } from '../labels'
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
  const [editingRetention, setEditingRetention] = useState(false)

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
          campaign && (
            <>
              <Button onClick={() => setEditingRetention(true)}>보존 기간 변경</Button>
              {campaign.status === 'active' && <Button onClick={() => setConfirmArchive(true)}>보관</Button>}
            </>
          )
        }
      >
        {campaign && (
          <p className="page-meta">
            <StatusBadge status={campaign.status} />
            <span className="num">{formatPeriod(campaign.startsAt, campaign.endsAt)}</span>
            <span className="num">보존 {campaign.retentionDays}일</span>
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
          {editingRetention && (
            <RetentionDialog
              campaign={campaign}
              onClose={() => setEditingRetention(false)}
              onSaved={() => {
                setEditingRetention(false)
                reload()
              }}
            />
          )}
        </>
      )}
    </>
  )
}

/**
 * 보존 기간만 바꾸는 대화상자 (ADR 0020). 줄이면 다음 파기 실행(매일 03:00)에서 바로 파기될 수 있어 확인을 한 번 더 받는다.
 * 범위를 벗어난 값은 확인 없이 보내 서버 오류를 칸에 보인다. 변경은 감사 로그(보존 기간 변경)에 남는다.
 */
function RetentionDialog({ campaign, onClose, onSaved }: { campaign: Campaign; onClose: () => void; onSaved: () => void }) {
  const [days, setDays] = useState(String(campaign.retentionDays))
  const [error, setError] = useState<string | undefined>()
  const [formError, setFormError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [confirmShorten, setConfirmShorten] = useState(false)
  const next = Number(days)
  const shortens =
    Number.isInteger(next) && next >= RETENTION_DAYS.min && next <= RETENTION_DAYS.max && next < campaign.retentionDays

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (shortens) {
      setConfirmShorten(true)
      return
    }
    setBusy(true)
    setError(undefined)
    setFormError(null)
    try {
      await updateCampaign(campaign.id, { retentionDays: Number(days) })
      onSaved()
    } catch (err) {
      if (err instanceof ApiError && err.fieldErrors.retentionDays) setError(err.fieldErrors.retentionDays)
      else setFormError(errorText(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog
      open
      title="보존 기간 변경"
      width="sm"
      onClose={onClose}
      footer={
        <>
          <Button onClick={onClose}>취소</Button>
          <Button variant="primary" type="submit" form="retention-days" busy={busy}>
            저장
          </Button>
        </>
      }
    >
      <form id="retention-days" onSubmit={submit} noValidate>
        <RetentionDaysField value={days} onChange={setDays} error={error} />
        <p className="muted small">기간을 줄이면 이미 그 기간이 지난 신청은 다음 파기 실행 때 바로 파기되며 되돌릴 수 없습니다.</p>
        {formError && (
          <p className="form-error" role="alert">
            {formError}
          </p>
        )}
      </form>
      <ConfirmDialog
        open={confirmShorten}
        title="보존 기간 단축"
        message={`보존 기간을 ${campaign.retentionDays}일 → ${next}일로 줄입니다. 신청 후 ${next}일이 지난 신청은 다음 파기 실행(매일 03:00)에서 이름·연락처가 파기되며 되돌릴 수 없습니다.`}
        confirmLabel="줄이기"
        onConfirm={async () => {
          await updateCampaign(campaign.id, { retentionDays: next })
          onSaved()
        }}
        onClose={() => setConfirmShorten(false)}
      />
    </Dialog>
  )
}
