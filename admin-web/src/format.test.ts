import { describe, expect, it } from 'vitest'
import {
  formatBytes,
  formatDate,
  formatDateTime,
  formatNumber,
  formatPercent,
  formatPeriod,
  formatPhone,
  seoulDateToInstant,
  summarizeAuditDetail,
  summarizePayload,
} from './format'

describe('formatNumber', () => {
  it('천 단위 구분 기호를 넣는다', () => {
    expect(formatNumber(1234567)).toBe('1,234,567')
    expect(formatNumber(0)).toBe('0')
  })
})

describe('formatPercent', () => {
  it('비율을 소수 첫째 자리 퍼센트로 바꾼다', () => {
    expect(formatPercent(0.6667)).toBe('66.7%')
    expect(formatPercent(0)).toBe('0.0%')
    expect(formatPercent(1)).toBe('100.0%')
  })

  it('문자열 숫자도 받는다', () => {
    expect(formatPercent('0.1234')).toBe('12.3%')
  })

  it('값이 없으면 대시를 낸다', () => {
    expect(formatPercent(null)).toBe('-')
    expect(formatPercent(undefined)).toBe('-')
  })
})

describe('formatPhone', () => {
  it('휴대전화 11자리를 3-4-4로 끊는다', () => {
    expect(formatPhone('01012345678')).toBe('010-1234-5678')
  })

  it('10자리 휴대전화는 3-3-4로 끊는다', () => {
    expect(formatPhone('0111234567')).toBe('011-123-4567')
  })

  it('서울 지역번호 02를 처리한다', () => {
    expect(formatPhone('0212345678')).toBe('02-1234-5678')
    expect(formatPhone('021234567')).toBe('02-123-4567')
  })

  it('이미 하이픈이 있으면 숫자만 보고 다시 끊는다', () => {
    expect(formatPhone('010-1234-5678')).toBe('010-1234-5678')
  })

  it('알 수 없는 형식은 그대로 둔다', () => {
    expect(formatPhone('+82 10 1234')).toBe('+82 10 1234')
  })

  it('값이 없으면 대시', () => {
    expect(formatPhone(null)).toBe('-')
  })
})

describe('날짜', () => {
  it('Asia/Seoul 기준 날짜로 표시한다(UTC 자정 직전은 서울 다음 날)', () => {
    expect(formatDate('2026-10-22T15:30:00Z')).toBe('2026-10-23')
  })

  it('날짜와 시각을 24시간제로 표시한다', () => {
    expect(formatDateTime('2026-10-22T15:05:00Z')).toBe('2026-10-23 00:05')
  })

  it('값이 없으면 대시', () => {
    expect(formatDate(null)).toBe('-')
    expect(formatDateTime(undefined)).toBe('-')
  })

  it('기간은 시작~종료로 보이고 한쪽만 있어도 표시한다', () => {
    expect(formatPeriod('2026-10-01T00:00:00+09:00', '2026-10-31T23:59:59+09:00')).toBe('2026-10-01 ~ 2026-10-31')
    expect(formatPeriod('2026-10-01T00:00:00+09:00', null)).toBe('2026-10-01 ~')
    expect(formatPeriod(null, null)).toBe('-')
  })

  it('date input 값을 서울 시각 Instant로 바꾼다', () => {
    expect(seoulDateToInstant('2026-10-01', 'start')).toBe('2026-09-30T15:00:00.000Z')
    expect(seoulDateToInstant('2026-10-31', 'end')).toBe('2026-10-31T14:59:59.000Z')
    expect(seoulDateToInstant('', 'start')).toBeNull()
  })
})

describe('formatBytes', () => {
  it('바이트를 읽기 쉬운 단위로', () => {
    expect(formatBytes(512)).toBe('512 B')
    expect(formatBytes(2048)).toBe('2.0 KB')
    expect(formatBytes(1536 * 1024)).toBe('1.5 MB')
  })
})

describe('summarizePayload', () => {
  it('추가 항목을 "키: 값"으로 잇고 배열은 쉼표로 합친다', () => {
    expect(summarizePayload({ company: '하늘상사', interests: ['교육', '세미나'] })).toBe(
      'company: 하늘상사 / interests: 교육, 세미나',
    )
  })

  it('비어 있으면 대시', () => {
    expect(summarizePayload({})).toBe('-')
    expect(summarizePayload(null)).toBe('-')
  })
})

describe('summarizeAuditDetail', () => {
  it('필터·페이지·건수·가린 이메일을 한 줄로 보여주고 빈 필터는 뺀다', () => {
    expect(summarizeAuditDetail({ formId: 11, channel: 'instagram', page: 0, size: 20 })).toBe(
      '폼 #11 · 인스타그램 · 1페이지 · 페이지당 20건',
    )
    expect(summarizeAuditDetail({ formId: null, channel: null, reveal: true, count: 1234 })).toBe('1,234건')
    expect(summarizeAuditDetail({ email: 'o***@example.com' })).toBe('이메일 o***@example.com')
  })

  it('보존 기간 파기는 실행 방식과 건수를 보여준다', () => {
    expect(summarizeAuditDetail({ trigger: 'scheduled', count: 3 })).toBe('자동 실행(매일 03:00) · 3건')
    expect(summarizeAuditDetail({ trigger: 'manual', count: 0, completed: true, interrupted: false })).toBe('수동 실행 · 0건')
    expect(summarizeAuditDetail({ trigger: 'manual', count: 0, completed: true, interrupted: true })).toBe(
      '수동 실행 · 0건 · 다른 실행이 처리 중이라 멈춤',
    )
    expect(
      summarizeAuditDetail({ trigger: 'scheduled', count: 2, completed: false, interrupted: false, error: 'org.x.DbDown' }),
    ).toBe('자동 실행(매일 03:00) · 2건 · 실패로 중단 · 오류 org.x.DbDown')
  })

  it('보존 기간 변경은 이전 값과 새 값을 보여준다', () => {
    expect(summarizeAuditDetail({ oldRetentionDays: 365, newRetentionDays: 30 })).toBe('보존 365일 → 30일')
  })

  it('로그인 시도 제한 시작은 가린 이메일과 범위·창 길이를 보여준다', () => {
    expect(summarizeAuditDetail({ email: 'o***@example.com', scopes: ['email', 'ip'], windowMinutes: 15 })).toBe(
      '이메일 o***@example.com · 기준 이메일·IP · 15분 창',
    )
  })

  it('비어 있으면 대시, 모르는 키는 그대로', () => {
    expect(summarizeAuditDetail({})).toBe('-')
    expect(summarizeAuditDetail({ extra: 'x' })).toBe('extra: x')
  })
})
