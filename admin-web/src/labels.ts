import type { CampaignStatus, Channel, CreatorStatus, FormStatus } from './api/types'

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
