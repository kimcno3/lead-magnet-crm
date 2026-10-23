import type { AuditAction, CampaignStatus, Channel, CreatorStatus, FormStatus } from './api/types'

export const CHANNEL_LABELS: Record<Channel, string> = {
  instagram: '인스타그램',
  x: 'X',
  youtube: '유튜브',
  threads: '스레드',
  direct: '직접 유입',
}

export const CAMPAIGN_STATUS_LABELS: Record<CampaignStatus, string> = {
  active: '진행 중',
  archived: '보관됨',
}

export const CREATOR_STATUS_LABELS: Record<CreatorStatus, string> = {
  active: '협업 중',
  archived: '보관됨',
}

/** 크리에이터별 지표에서 크리에이터 없는 링크와 direct를 묶은 행의 이름. */
export const SELF_LINK_LABEL = '직접·자체 링크'

export const FORM_STATUS_LABELS: Record<FormStatus, string> = {
  draft: '초안',
  published: '게시됨',
  closed: '마감',
}

/** 템플릿 업로드 거절 코드별 안내. 백엔드 detail과 함께 보여준다. */
export const TEMPLATE_ERROR_GUIDES: Record<string, string> = {
  template_contact_field_missing: 'phone 또는 email 이름의 입력이 있어야 합니다',
  template_form_count_invalid: '문서에 <form>이 정확히 하나 있어야 합니다',
  template_forbidden_field: 'type="file", type="password" 입력은 쓸 수 없습니다. 지우고 다시 올려 주세요',
  template_field_invalid: '입력 필드 규칙을 지키지 않았습니다. 아래 상세 내용을 확인해 주세요',
  template_not_utf8: 'UTF-8로 저장한 파일이어야 합니다',
  template_invalid_extension: '.html 또는 .htm 파일만 올릴 수 있습니다',
  template_empty: '내용이 없는 파일입니다',
  template_too_large: '512KB 이하 파일만 올릴 수 있습니다',
  payload_too_large: '512KB 이하 파일만 올릴 수 있습니다',
}

/** VITE_PUBLIC_BASE_URL(빌드 시 주입). 공개 폼 주소 앞부분이다. */
export const PUBLIC_BASE_URL: string = (import.meta.env.VITE_PUBLIC_BASE_URL || 'http://localhost:8081').replace(/\/$/, '')

/** 링크 응답 url({base}/l/{code})이 있으면 거기서 base를 얻는다. 백엔드 설정이 기준이기 때문이다. */
export function publicBaseFrom(linkUrl: string | undefined): string {
  if (linkUrl) {
    const i = linkUrl.lastIndexOf('/l/')
    if (i > 0) return linkUrl.slice(0, i)
  }
  return PUBLIC_BASE_URL
}

/** 감사 로그 action 이름 (ADR 0016). */
export const AUDIT_ACTION_LABELS: Record<AuditAction, string> = {
  'lead.list': '명단 조회',
  'lead.contact.view': '원문 열람',
  'lead.export': 'CSV 다운로드(마스킹)',
  'lead.export.reveal': 'CSV 다운로드(원문)',
  'auth.login.success': '로그인 성공',
  'auth.login.failure': '로그인 실패',
  'retention.anonymize': '보존 기간 파기',
  'campaign.retention.update': '보존 기간 변경',
}

/** 보존 기간 파기 감사 기록의 실행 방식 (ADR 0020). */
export const RETENTION_TRIGGER_LABELS: Record<string, string> = {
  scheduled: '자동 실행(매일 03:00)',
  manual: '수동 실행',
}

/** 캠페인 보존 기간 기본값·범위(일). 백엔드 Campaign과 같다. */
export const RETENTION_DAYS = { default: 365, min: 1, max: 3650 } as const

/** 보존 기간이 지나 파기된 신청 표시. */
export const ANONYMIZED_LABEL = '파기됨'
