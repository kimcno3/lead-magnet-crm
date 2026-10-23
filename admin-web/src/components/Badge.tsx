import type { CampaignStatus, CreatorStatus, FormStatus } from '../api/types'
import { CAMPAIGN_STATUS_LABELS, CREATOR_STATUS_LABELS, FORM_STATUS_LABELS } from '../labels'

/** 상태는 색과 함께 글자로도 표시한다(색만으로 구분하지 않는다). */
export function StatusBadge({ status }: { status: FormStatus | CampaignStatus }) {
  const label =
    status in FORM_STATUS_LABELS
      ? FORM_STATUS_LABELS[status as FormStatus]
      : CAMPAIGN_STATUS_LABELS[status as CampaignStatus]
  return <span className={`badge badge-${status}`}>{label}</span>
}

/** 크리에이터 상태. 캠페인과 값은 같지만 문구가 다르다(협업 중·보관됨). */
export function CreatorStatusBadge({ status }: { status: CreatorStatus }) {
  return <span className={`badge badge-${status}`}>{CREATOR_STATUS_LABELS[status]}</span>
}
