import type { CampaignStatus, FormStatus } from '../api/types'
import { CAMPAIGN_STATUS_LABELS, FORM_STATUS_LABELS } from '../labels'

/** 상태는 색과 함께 글자로도 표시한다(색만으로 구분하지 않는다). */
export function StatusBadge({ status }: { status: FormStatus | CampaignStatus }) {
  const label =
    status in FORM_STATUS_LABELS
      ? FORM_STATUS_LABELS[status as FormStatus]
      : CAMPAIGN_STATUS_LABELS[status as CampaignStatus]
  return <span className={`badge badge-${status}`}>{label}</span>
}
