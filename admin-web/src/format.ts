// 화면에 보이는 숫자·날짜·전화번호 포맷. 날짜는 모두 Asia/Seoul 기준이다.
// 숫자는 현재 화면 언어의 로케일(ko-KR, en-US)을 따른다. 날짜는 두 언어 모두 yyyy-MM-dd HH:mm으로 같다.

import { CHANNELS, type Channel } from './api/types'
import { activeLang, localeOf, t, type Lang } from './i18n/translate'
import { channelLabel, loginLockScopeLabel, retentionTriggerLabel } from './labels'

const TIME_ZONE = 'Asia/Seoul'

interface NumberFormats {
  number: Intl.NumberFormat
  percent: Intl.NumberFormat
  oneDecimal: Intl.NumberFormat
}

const formatsByLang = new Map<Lang, NumberFormats>()

function formats(): NumberFormats {
  const lang = activeLang()
  let f = formatsByLang.get(lang)
  if (!f) {
    const locale = localeOf(lang)
    f = {
      number: new Intl.NumberFormat(locale),
      percent: new Intl.NumberFormat(locale, { style: 'percent', minimumFractionDigits: 1, maximumFractionDigits: 1 }),
      oneDecimal: new Intl.NumberFormat(locale, { minimumFractionDigits: 1, maximumFractionDigits: 1 }),
    }
    formatsByLang.set(lang, f)
  }
  return f
}
const partsFormat = new Intl.DateTimeFormat('en-CA', {
  timeZone: TIME_ZONE,
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})

type Maybe<T> = T | null | undefined

export function formatNumber(value: number): string {
  return formats().number.format(value)
}

/** 0~1 비율을 "66.7%"로. 백엔드 conversionRate는 소수 넷째 자리 반올림 값이다. */
export function formatPercent(rate: Maybe<number | string>): string {
  if (rate === null || rate === undefined || rate === '') return '-'
  const n = typeof rate === 'number' ? rate : Number(rate)
  if (!Number.isFinite(n)) return '-'
  return formats().percent.format(n)
}

function seoulParts(iso: string) {
  const parts = Object.fromEntries(partsFormat.formatToParts(new Date(iso)).map((p) => [p.type, p.value]))
  return parts as Record<'year' | 'month' | 'day' | 'hour' | 'minute', string>
}

export function formatDate(iso: Maybe<string>): string {
  if (!iso) return '-'
  const p = seoulParts(iso)
  return `${p.year}-${p.month}-${p.day}`
}

export function formatDateTime(iso: Maybe<string>): string {
  if (!iso) return '-'
  const p = seoulParts(iso)
  return `${p.year}-${p.month}-${p.day} ${p.hour}:${p.minute}`
}

export function formatPeriod(start: Maybe<string>, end: Maybe<string>): string {
  if (!start && !end) return '-'
  return `${start ? formatDate(start) : ''} ~${end ? ` ${formatDate(end)}` : ''}`.trim()
}

const seoulDateFormat = new Intl.DateTimeFormat('en-CA', { timeZone: TIME_ZONE, year: 'numeric', month: '2-digit', day: '2-digit' })

/** 지금의 서울 날짜(yyyy-MM-dd). */
export function seoulToday(now: Date = new Date()): string {
  return seoulDateFormat.format(now)
}

/** yyyy-MM-dd 날짜에 days일을 더한다(음수면 뺀다). 시간대와 무관한 달력 계산이다. */
export function addDays(date: string, days: number): string {
  const d = new Date(`${date}T00:00:00Z`)
  d.setUTCDate(d.getUTCDate() + days)
  return d.toISOString().slice(0, 10)
}

/** date input 값(yyyy-MM-dd)을 서울 시각 기준 Instant(ISO)로. 시작은 00:00:00, 종료는 23:59:59. */
export function seoulDateToInstant(date: string, edge: 'start' | 'end'): string | null {
  if (!date) return null
  const time = edge === 'start' ? '00:00:00' : '23:59:59'
  return new Date(`${date}T${time}+09:00`).toISOString()
}

export function formatBytes(size: number): string {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${formats().oneDecimal.format(size / 1024)} KB`
  return `${formats().oneDecimal.format(size / (1024 * 1024))} MB`
}

/** 국내 전화번호 숫자열을 하이픈 형식으로. 모르는 형식은 원문 그대로. */
export function formatPhone(phone: Maybe<string>): string {
  if (!phone) return '-'
  const digits = phone.replace(/-/g, '')
  if (!/^0\d{8,10}$/.test(digits)) return phone
  if (digits.startsWith('02')) {
    const rest = digits.slice(2)
    const mid = rest.length === 8 ? 4 : 3
    return `02-${rest.slice(0, mid)}-${rest.slice(mid)}`
  }
  const rest = digits.slice(3)
  const mid = rest.length === 8 ? 4 : 3
  return `${digits.slice(0, 3)}-${rest.slice(0, mid)}-${rest.slice(mid)}`
}

/** 명단의 추가 항목(payload)을 한 줄로. */
export function summarizePayload(payload: Maybe<Record<string, unknown>>): string {
  if (!payload) return '-'
  const entries = Object.entries(payload).filter(([, v]) => v !== null && v !== undefined && v !== '')
  if (entries.length === 0) return '-'
  return entries
    .map(([k, v]) => `${k}: ${Array.isArray(v) ? v.join(', ') : typeof v === 'object' ? JSON.stringify(v) : String(v)}`)
    .join(' / ')
}

/** 감사 로그 detail을 한 줄로 (ADR 0016). 모르는 키는 key: value로 보여준다. reveal은 행동 이름에 이미 드러나 생략한다. */
export function summarizeAuditDetail(detail: Record<string, unknown>): string {
  const parts: string[] = []
  for (const [key, value] of Object.entries(detail)) {
    if (value === null || value === undefined) continue
    switch (key) {
      case 'formId':
        parts.push(t('auditDetail.form', { id: String(value) }))
        break
      case 'channel':
        parts.push((CHANNELS as readonly string[]).includes(String(value)) ? channelLabel(value as Channel) : String(value))
        break
      case 'page':
        parts.push(t('auditDetail.page', { page: Number(value) + 1 }))
        break
      case 'size':
        parts.push(t('auditDetail.pageSize', { size: String(value) }))
        break
      case 'count':
        parts.push(t('auditDetail.count', { count: Number(value) }))
        break
      case 'reveal':
        break
      case 'email':
        parts.push(t('auditDetail.email', { email: String(value) }))
        break
      case 'trigger':
        parts.push(retentionTriggerLabel(String(value)))
        break
      case 'completed':
        if (value === false) parts.push(t('auditDetail.failed'))
        break
      case 'interrupted':
        if (value === true) parts.push(t('auditDetail.interrupted'))
        break
      case 'error':
        parts.push(t('auditDetail.error', { error: String(value) }))
        break
      case 'unreadable':
        if (Number(value) > 0) parts.push(t('auditDetail.unreadable', { count: Number(value) }))
        break
      case 'remaining':
        parts.push(t('auditDetail.remaining', { count: Number(value) }))
        break
      case 'oldRetentionDays':
        parts.push(t('auditDetail.retention', { old: String(value), new: String(detail.newRetentionDays) }))
        break
      case 'newRetentionDays':
        break
      case 'scopes':
        if (Array.isArray(value)) {
          parts.push(t('auditDetail.scopes', { scopes: value.map((v) => loginLockScopeLabel(String(v))).join('·') }))
        }
        break
      case 'slug':
        parts.push(`slug ${String(value)}`)
        break
      case 'templateId':
        parts.push(t('auditDetail.template', { id: String(value) }))
        break
      case 'version':
        parts.push(`v${String(value)}`)
        break
      case 'affectedDraftForms':
        parts.push(t('auditDetail.draftForms', { count: Number(value) }))
        break
      case 'creatorId':
        parts.push(t('auditDetail.creator', { id: String(value) }))
        break
      case 'from':
        parts.push(t('auditDetail.period', { from: String(value), to: String(detail.to ?? '') }))
        break
      case 'to':
        break
      case 'windowMinutes':
        parts.push(t('auditDetail.window', { minutes: String(value) }))
        break
      default:
        parts.push(`${key}: ${typeof value === 'object' ? JSON.stringify(value) : String(value)}`)
    }
  }
  return parts.length > 0 ? parts.join(' · ') : '-'
}

/** 선택지 목록을 쉼표로 잇는다. 값이 빈 선택지는 select의 안내 문구("선택해 주세요")라 고를 수 있는 값이 아니어서 뺀다. */
export function formatOptions(options: string[] | null | undefined): string {
  return (options ?? []).filter((o) => o !== '').join(', ')
}
