import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearSession } from '../api/client'
import type { AuditLog, AuditLogPage } from '../api/types'
import { json, mockBackend, requestsTo } from '../test/http'
import { renderPage } from '../test/render'
import { AuditLogListPage } from './AuditLogList'

beforeEach(() => clearSession())
afterEach(() => vi.unstubAllGlobals())

const campaigns = [{ id: 7, name: '가을 세미나', status: 'active', startsAt: null, endsAt: null, formCount: 1, createdAt: '2026-09-01T00:00:00Z' }]

const reveal: AuditLog = {
  id: 3,
  operatorId: 1,
  operatorEmail: 'ops@example.com',
  action: 'lead.contact.view',
  targetType: 'lead',
  targetId: 101,
  campaignId: 7,
  detail: {},
  ip: '203.0.113.7',
  userAgent: 'Mozilla/5.0',
  createdAt: '2026-10-23T01:05:00Z',
}
const exported: AuditLog = {
  ...reveal,
  id: 2,
  action: 'lead.export.reveal',
  targetType: 'campaign',
  targetId: 7,
  detail: { formId: null, channel: 'instagram', reveal: true, count: 12 },
}
const failedLogin: AuditLog = {
  ...reveal,
  id: 1,
  operatorId: null,
  operatorEmail: null,
  action: 'auth.login.failure',
  targetType: 'operator',
  targetId: null,
  campaignId: null,
  detail: { email: 's***@example.com' },
}

function page(items: AuditLog[], total = items.length, n = 0): AuditLogPage {
  return { items, page: n, size: 20, totalElements: total, totalPages: Math.ceil(total / 20) }
}

function backend(result: (url: URL) => Response = () => json(page([reveal, exported, failedLogin]))) {
  return mockBackend((url) => {
    if (url.pathname === '/api/admin/audit-logs') return result(url)
    if (url.pathname === '/api/admin/campaigns') return json(campaigns)
    return undefined
  })
}

describe('감사 로그', () => {
  it('시각·운영자·행동·대상·캠페인·내용·IP를 표로 보여준다', async () => {
    backend()
    renderPage('/audit-logs', <AuditLogListPage />)
    const table = await screen.findByRole('table', { name: '감사 로그' })
    await within(table).findByText('원문 열람')
    const rows = within(table).getAllByRole('row').slice(1)

    expect(within(rows[0]).getByText('2026-10-23 10:05')).toBeInTheDocument()
    expect(within(rows[0]).getByText('ops@example.com')).toBeInTheDocument()
    expect(within(rows[0]).getByText('원문 열람')).toBeInTheDocument()
    expect(within(rows[0]).getByText('신청 #101')).toBeInTheDocument()
    expect(await within(rows[0]).findByText('가을 세미나')).toBeInTheDocument()
    expect(within(rows[0]).getByText('203.0.113.7')).toBeInTheDocument()
    expect(within(rows[1]).getByText('CSV 다운로드(원문)')).toBeInTheDocument()
    expect(within(rows[1]).getByText('인스타그램 · 12건')).toBeInTheDocument()
    expect(within(rows[2]).getByText('로그인 실패')).toBeInTheDocument()
    expect(within(rows[2]).getByText('이메일 s***@example.com')).toBeInTheDocument()
    expect(screen.getByText('3')).toBeInTheDocument()
  })

  it('행동·캠페인·기간 필터를 파라미터로 보내고 첫 페이지로 돌아간다', async () => {
    const fetchMock = backend((url) => json(page([reveal], 45, Number(url.searchParams.get('page') ?? 0))))
    renderPage('/audit-logs', <AuditLogListPage />, '/audit-logs?page=1')
    await screen.findByRole('table', { name: '감사 로그' })
    await screen.findByRole('option', { name: '가을 세미나' })

    fireEvent.change(screen.getByLabelText('행동'), { target: { value: 'lead.export.reveal' } })
    await waitFor(() => expect(requestsTo(fetchMock, '/api/admin/audit-logs')).toHaveLength(2))
    fireEvent.change(screen.getByLabelText('캠페인'), { target: { value: '7' } })
    await waitFor(() => expect(requestsTo(fetchMock, '/api/admin/audit-logs')).toHaveLength(3))
    fireEvent.change(screen.getByLabelText('시작일'), { target: { value: '2026-10-01' } })
    await waitFor(() => expect(requestsTo(fetchMock, '/api/admin/audit-logs')).toHaveLength(4))

    const requests = requestsTo(fetchMock, '/api/admin/audit-logs')
    expect(requests[0].searchParams.get('page')).toBe('1')
    const last = requests[3]
    expect(last.searchParams.get('page')).toBe('0')
    expect(last.searchParams.get('action')).toBe('lead.export.reveal')
    expect(last.searchParams.get('campaignId')).toBe('7')
    expect(last.searchParams.get('from')).toBe('2026-10-01')
    expect(last.searchParams.get('size')).toBe('20')
  })

  it('User-Agent는 앞부분을 보여주고 펼치면 전체를 보여준다(마우스 없이도)', async () => {
    const longUa = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/141.0 Safari/537.36'
    backend(() => json(page([{ ...reveal, userAgent: longUa }])))
    renderPage('/audit-logs', <AuditLogListPage />)
    const table = await screen.findByRole('table', { name: '감사 로그' })
    await within(table).findByText('원문 열람')

    const summary = within(table).getByText(`${longUa.slice(0, 32)}…`)
    expect(summary.tagName).toBe('SUMMARY')
    const details = summary.closest('details')!
    expect(details.open).toBe(false)
    expect(within(details).getByText(longUa)).toBeInTheDocument()
    fireEvent.click(summary)
    expect(details.open).toBe(true)
  })

  it('기간을 바꾸면 첫 페이지로 돌아간다', async () => {
    const fetchMock = backend((url) => json(page([reveal], 45, Number(url.searchParams.get('page') ?? 0))))
    renderPage('/audit-logs', <AuditLogListPage />, '/audit-logs?page=2')
    await screen.findByText('3 / 3 페이지')

    fireEvent.change(screen.getByLabelText('종료일'), { target: { value: '2026-10-31' } })

    await waitFor(() => expect(requestsTo(fetchMock, '/api/admin/audit-logs')).toHaveLength(2))
    const last = requestsTo(fetchMock, '/api/admin/audit-logs')[1]
    expect(last.searchParams.get('to')).toBe('2026-10-31')
    expect(last.searchParams.get('page')).toBe('0')
  })

  it('다음 페이지로 넘긴다', async () => {
    const fetchMock = backend((url) => json(page([reveal], 45, Number(url.searchParams.get('page') ?? 0))))
    renderPage('/audit-logs', <AuditLogListPage />)
    await screen.findByText('1 / 3 페이지')

    fireEvent.click(screen.getByRole('button', { name: '다음 페이지' }))

    await screen.findByText('2 / 3 페이지')
    expect(requestsTo(fetchMock, '/api/admin/audit-logs').map((u) => u.searchParams.get('page'))).toEqual(['0', '1'])
  })

  it('기록이 없으면 빈 상태를, 필터가 있으면 필터 지우기를 보여준다', async () => {
    const fetchMock = backend(() => json(page([])))
    renderPage('/audit-logs', <AuditLogListPage />, '/audit-logs?action=lead.list')

    expect(await screen.findByText(/조건에 맞는 기록이 없습니다/)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '필터 지우기' }))

    expect(await screen.findByText('아직 감사 기록이 없습니다.')).toBeInTheDocument()
    expect(requestsTo(fetchMock, '/api/admin/audit-logs').at(-1)!.searchParams.has('action')).toBe(false)
  })

  it('불러오지 못하면 오류와 다시 시도를 보여주고 표는 숨긴다', async () => {
    let fail = true
    backend(() => (fail ? json({ code: 'internal_error', detail: '서버 오류가 발생했습니다' }, 500) : json(page([reveal]))))
    renderPage('/audit-logs', <AuditLogListPage />)

    expect(await screen.findByText('서버 오류가 발생했습니다')).toBeInTheDocument()
    expect(screen.queryByRole('table', { name: '감사 로그' })).not.toBeInTheDocument()

    fail = false
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }))
    expect(await screen.findByText('원문 열람')).toBeInTheDocument()
  })

  it('불러오는 동안 표 자리에 스켈레톤을 보여준다', async () => {
    let resolve: (r: Response) => void = () => {}
    backend(() => new Promise<Response>((r) => (resolve = r)) as unknown as Response)
    renderPage('/audit-logs', <AuditLogListPage />)

    const table = await screen.findByRole('table', { name: '감사 로그' })
    expect(table.querySelectorAll('tbody tr.skeleton-row').length).toBeGreaterThan(0)
    expect(table.querySelector('tbody')).toHaveAttribute('aria-busy', 'true')
    resolve(json(page([])))
    expect(await screen.findByText('아직 감사 기록이 없습니다.')).toBeInTheDocument()
  })
})
