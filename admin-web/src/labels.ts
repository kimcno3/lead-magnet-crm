// 코드 값(채널·상태·감사 행동·오류 코드)을 화면 문구로 바꾼다. 문구는 i18n 사전에 있고, 부를 때의 언어로 돌려준다.
import type { AuditAction, Channel, FormStatus } from './api/types'
import { ko } from './i18n/ko'
import { t, type TKey } from './i18n/translate'

/** 사전에 있는 코드면 그 문구, 없으면 undefined. */
function known(group: Record<string, string>, prefix: string, code: string): string | undefined {
  return Object.hasOwn(group, code) ? t(`${prefix}.${code}` as TKey) : undefined
}

/** 모르는 채널 값은 그대로 보여준다. */
export function channelLabel(channel: Channel | string): string {
  return known(ko.labels.channel, 'labels.channel', channel) ?? channel
}

export function isFormStatus(status: string): status is FormStatus {
  return status in ko.labels.formStatus
}

/** 크리에이터별 지표에서 크리에이터 없는 링크와 direct를 묶은 행의 이름. */
export function selfLinkLabel(): string {
  return t('labels.selfLink')
}

/** 보존 기간이 지나 파기된 신청 표시. */
export function anonymizedLabel(): string {
  return t('labels.anonymized')
}

/** 템플릿 업로드 거절 코드별 안내. 백엔드 detail과 함께 보여준다. 모르는 코드는 undefined. */
export function templateErrorGuide(code: string): string | undefined {
  return known(ko.labels.templateError, 'labels.templateError', code)
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

/** 감사 로그 action 이름 (ADR 0016). 모르는 값은 그대로 보여준다. */
export function auditActionLabel(action: AuditAction | string): string {
  return known(ko.labels.auditAction, 'labels.auditAction', action) ?? action
}

/** 로그인 시도 제한 감사 기록의 범위 (ADR 0021). */
export function loginLockScopeLabel(scope: string): string {
  return known(ko.labels.loginLockScope, 'labels.loginLockScope', scope) ?? scope
}

/** 보존 기간 파기 감사 기록의 실행 방식 (ADR 0020). */
export function retentionTriggerLabel(trigger: string): string {
  return known(ko.labels.retentionTrigger, 'labels.retentionTrigger', trigger) ?? trigger
}

/** 캠페인 보존 기간 기본값·범위(일). 백엔드 Campaign과 같다. */
export const RETENTION_DAYS = { default: 365, min: 1, max: 3650 } as const
