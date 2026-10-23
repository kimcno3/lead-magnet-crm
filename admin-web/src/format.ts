// 화면에 보이는 숫자·날짜·전화번호 포맷. 날짜는 모두 Asia/Seoul 기준이다.

import { CHANNELS, type Channel } from './api/types'
import { CHANNEL_LABELS, RETENTION_TRIGGER_LABELS } from './labels'

const TIME_ZONE = 'Asia/Seoul'
const numberFormat = new Intl.NumberFormat('ko-KR')
const percentFormat = new Intl.NumberFormat('ko-KR', {
  style: 'percent',
  minimumFractionDigits: 1,
  maximumFractionDigits: 1,
})
const oneDecimal = new Intl.NumberFormat('ko-KR', { minimumFractionDigits: 1, maximumFractionDigits: 1 })
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
  return numberFormat.format(value)
}

/** 0~1 비율을 "66.7%"로. 백엔드 conversionRate는 소수 넷째 자리 반올림 값이다. */
export function formatPercent(rate: Maybe<number | string>): string {
  if (rate === null || rate === undefined || rate === '') return '-'
  const n = typeof rate === 'number' ? rate : Number(rate)
  if (!Number.isFinite(n)) return '-'
  return percentFormat.format(n)
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

/** date input 값(yyyy-MM-dd)을 서울 시각 기준 Instant(ISO)로. 시작은 00:00:00, 종료는 23:59:59. */
export function seoulDateToInstant(date: string, edge: 'start' | 'end'): string | null {
  if (!date) return null
  const time = edge === 'start' ? '00:00:00' : '23:59:59'
  return new Date(`${date}T${time}+09:00`).toISOString()
}

export function formatBytes(size: number): string {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${oneDecimal.format(size / 1024)} KB`
  return `${oneDecimal.format(size / (1024 * 1024))} MB`
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
        parts.push(`폼 #${String(value)}`)
        break
      case 'channel':
        parts.push((CHANNELS as readonly string[]).includes(String(value)) ? CHANNEL_LABELS[value as Channel] : String(value))
        break
      case 'page':
        parts.push(`${Number(value) + 1}페이지`)
        break
      case 'size':
        parts.push(`페이지당 ${String(value)}건`)
        break
      case 'count':
        parts.push(`${formatNumber(Number(value))}건`)
        break
      case 'reveal':
        break
      case 'email':
        parts.push(`이메일 ${String(value)}`)
        break
      case 'trigger':
        parts.push(RETENTION_TRIGGER_LABELS[String(value)] ?? String(value))
        break
      default:
        parts.push(`${key}: ${typeof value === 'object' ? JSON.stringify(value) : String(value)}`)
    }
  }
  return parts.length > 0 ? parts.join(' · ') : '-'
}
