import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearSession } from '../api/client'
import type { Dashboard } from '../api/types'
import { json, mockBackend, requestsTo } from '../test/http'
import { renderPage } from '../test/render'
import { DashboardPage } from './Dashboard'

const metrics = (visits: number, visitors: number, leads: number, convertedVisitors: number, conversionRate: number) => ({
  visits,
  visitors,
  leads,
  convertedVisitors,
  conversionRate,
})

const dashboard: Dashboard = {
  from: '2026-03-10',
  to: '2026-03-12',
  totals: metrics(1520, 4, 3, 2, 0.5),
  daily: [
    { date: '2026-03-10', visits: 3, visitors: 3, leads: 1, convertedVisitors: 1 },
    { date: '2026-03-11', visits: 1, visitors: 1, leads: 1, convertedVisitors: 0 },
    { date: '2026-03-12', visits: 3, visitors: 3, leads: 1, convertedVisitors: 1 },
  ],
  forms: [
    { campaignId: 2, campaignName: 'B 캠페인', formId: 5, title: '세미나 신청', status: 'published', ...metrics(1, 1, 1, 1, 1) },
    { campaignId: 1, campaignName: 'A 캠페인', formId: 3, title: '전자책', status: 'published', ...metrics(3, 2, 1, 1, 0.5) },
    { campaignId: 1, campaignName: 'A 캠페인', formId: 4, title: '체크리스트', status: 'closed', ...metrics(3, 2, 2, 0, 0) },
  ],
  channels: [
    { channel: 'instagram', ...metrics(2, 1, 1, 1, 1) },
    { channel: 'x', ...metrics(2, 1, 1, 1, 1) },
    { channel: 'youtube', ...metrics(0, 0, 0, 0, 0) },
    { channel: 'threads', ...metrics(0, 0, 0, 0, 0) },
    { channel: 'direct', ...metrics(3, 2, 1, 0, 0) },
  ],
  creators: [
    { creator: { id: 9, name: '하늘', status: 'active' }, ...metrics(2, 1, 1, 1, 1) },
    { creator: null, ...metrics(5, 3, 2, 1, 0.3333) },
  ],
  recentLeads: [
    { id: 12, createdAt: '2026-03-12T03:30:00Z', campaignId: 2, campaignName: 'B 캠페인', formTitle: '세미나 신청', channel: 'x', creatorName: null, name: '김*수' },
    { id: 11, createdAt: '2026-03-10T01:30:00Z', campaignId: 1, campaignName: 'A 캠페인', formTitle: '전자책', channel: 'instagram', creatorName: '하늘', name: null },
  ],
}

const empty: Dashboard = {
  ...dashboard,
  totals: metrics(0, 0, 0, 0, 0),
  daily: dashboard.daily.map((d) => ({ ...d, visits: 0, visitors: 0, leads: 0, convertedVisitors: 0 })),
  forms: [],
  creators: [{ creator: null, ...metrics(0, 0, 0, 0, 0) }],
  recentLeads: [],
}

beforeEach(() => {
  clearSession()
  vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:csv'), revokeObjectURL: vi.fn() }))
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
})
afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
  vi.useRealTimers()
})

function backend(body: Dashboard | (() => Response) = dashboard) {
  return mockBackend((url) => {
    if (url.pathname === '/api/admin/stats/dashboard') return typeof body === 'function' ? body() : json(body)
    if (url.pathname === '/api/admin/stats/dashboard.csv') {
      return new Response('﻿캠페인\r\n', {
        headers: { 'Content-Type': 'text/csv;charset=UTF-8', 'Content-Disposition': 'attachment; filename="dashboard-forms-x.csv"' },
      })
    }
    return undefined
  })
}

function sheetRows() {
  const table = screen.getByRole('table', { name: '폼별 유입 시트' })
  return within(table)
    .getAllByRole('row')
    .slice(1, -1)
    .map((r) => within(r).getAllByRole('cell')[1].textContent)
}

describe('대시보드', () => {
  it('KPI 네 개와 폼 시트를 보여주고 폼은 캠페인 폼 탭으로, 최근 신청은 명단 탭으로 간다', async () => {
    backend()
    renderPage('/dashboard', <DashboardPage />)
    const kpis = await screen.findByRole('list', { name: '핵심 지표' })
    await within(kpis).findByText('1,520')
    expect(within(kpis).getAllByRole('listitem').map((li) => li.textContent)).toEqual([
      '방문1,520',
      '방문자4',
      '신청3',
      '전환율50.0%',
    ])
    expect(screen.getByRole('heading', { name: '대시보드', level: 1 })).toBeInTheDocument()
    expect(screen.getByText('조회 기간 2026-03-10 ~ 2026-03-12')).toBeInTheDocument()

    const table = screen.getByRole('table', { name: '폼별 유입 시트' })
    const header = within(table).getAllByRole('columnheader').map((th) => th.textContent)
    expect(header).toEqual(['캠페인', '폼', '상태', '방문', '방문자', '신청', '전환 방문자', '전환율'])
    expect(within(table).getByRole('link', { name: '세미나 신청' })).toHaveAttribute('href', '/campaigns/2?tab=forms&open=5')
    expect(within(table).getByText('마감')).toBeInTheDocument()
    const footer = within(table).getAllByRole('row').at(-1)!
    expect(within(footer).getAllByRole('cell').map((c) => c.textContent)).toEqual(['', '', '1,520', '4', '3', '2', '50.0%'])
    expect(within(footer).getByRole('rowheader')).toHaveTextContent('합계')

    const recent = screen.getByRole('list', { name: '최근 신청' })
    expect(within(recent).getByRole('link', { name: /김\*수/ })).toHaveAttribute('href', '/campaigns/2?tab=leads')
    expect(within(recent).getByRole('link', { name: /\(이름 없음\)/ })).toHaveAttribute('href', '/campaigns/1?tab=leads')
    expect(screen.queryByText(/@/)).not.toBeInTheDocument()

    const channels = screen.getByRole('table', { name: '채널별 성과' })
    expect(within(channels).getAllByRole('row')).toHaveLength(6)
    const creators = screen.getByRole('table', { name: '크리에이터별 성과' })
    expect(within(creators).getByRole('link', { name: '하늘' })).toHaveAttribute('href', '/creators/9')
    expect(within(creators).getByText('직접·자체 링크')).toBeInTheDocument()
  })

  it('열 머리글을 누르면 정렬하고 aria-sort를 바꾼다', async () => {
    backend()
    renderPage('/dashboard', <DashboardPage />)
    const table = await screen.findByRole('table', { name: '폼별 유입 시트' })
    await within(table).findByText('세미나 신청')
    const leadsHeader = within(table).getByRole('columnheader', { name: '신청' })
    expect(leadsHeader).not.toHaveAttribute('aria-sort')

    fireEvent.click(within(leadsHeader).getByRole('button', { name: '신청' }))
    expect(leadsHeader).toHaveAttribute('aria-sort', 'descending')
    expect(sheetRows()).toEqual(['체크리스트', '세미나 신청', '전자책'])

    fireEvent.click(within(leadsHeader).getByRole('button', { name: '신청' }))
    expect(leadsHeader).toHaveAttribute('aria-sort', 'ascending')
    expect(sheetRows()).toEqual(['세미나 신청', '전자책', '체크리스트'])

    const formHeader = within(table).getByRole('columnheader', { name: '폼' })
    fireEvent.click(within(formHeader).getByRole('button', { name: '폼' }))
    expect(formHeader).toHaveAttribute('aria-sort', 'ascending')
    expect(leadsHeader).not.toHaveAttribute('aria-sort')
  })

  it('빠른 기간 버튼은 서울 오늘까지의 기간을 URL과 요청에 넣는다', async () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-03-12T15:30:00Z')) // 서울 03-13 00:30
    const fetchMock = backend()
    renderPage('/dashboard', <DashboardPage />)
    await screen.findByRole('table', { name: '폼별 유입 시트' })
    expect(requestsTo(fetchMock, '/api/admin/stats/dashboard')[0].searchParams.has('from')).toBe(false)

    const quick = screen.getByRole('group', { name: '빠른 기간' })
    fireEvent.click(within(quick).getByRole('button', { name: '최근 7일' }))

    await waitFor(() => {
      const last = requestsTo(fetchMock, '/api/admin/stats/dashboard').at(-1)!
      expect(last.searchParams.get('from')).toBe('2026-03-07')
      expect(last.searchParams.get('to')).toBe('2026-03-13')
    })
    expect(screen.getByLabelText('시작일')).toHaveValue('2026-03-07')
    expect(screen.getByLabelText('종료일')).toHaveValue('2026-03-13')
    expect(within(quick).getByRole('button', { name: '최근 7일' })).toHaveAttribute('aria-pressed', 'true')

    fireEvent.click(within(quick).getByRole('button', { name: '최근 90일' }))
    await waitFor(() => expect(screen.getByLabelText('시작일')).toHaveValue('2025-12-14'))
  })

  it('CSV 버튼은 같은 기간으로 폼 시트 CSV를 받는다', async () => {
    const fetchMock = backend()
    renderPage('/dashboard', <DashboardPage />, '/dashboard?from=2026-03-10&to=2026-03-12')
    await screen.findByRole('table', { name: '폼별 유입 시트' })

    fireEvent.click(screen.getByRole('button', { name: 'CSV 내려받기' }))

    await waitFor(() => expect(requestsTo(fetchMock, '/api/admin/stats/dashboard.csv')).toHaveLength(1))
    const req = requestsTo(fetchMock, '/api/admin/stats/dashboard.csv')[0]
    expect(req.searchParams.get('from')).toBe('2026-03-10')
    expect(req.searchParams.get('to')).toBe('2026-03-12')
    await waitFor(() => expect(URL.createObjectURL).toHaveBeenCalled())
  })

  it('일별 추이 그래프에는 같은 데이터를 담은 숨긴 표가 있다', async () => {
    backend()
    renderPage('/dashboard', <DashboardPage />)
    expect(await screen.findByRole('img', { name: '일별 방문과 신청 추이 그래프' })).toBeInTheDocument()
    const table = screen.getByRole('table', { name: '일별 추이 데이터' })
    expect(table.closest('.visually-hidden')).not.toBeNull()
    const rows = within(table).getAllByRole('row').slice(1)
    expect(rows).toHaveLength(3)
    expect(within(rows[0]).getByRole('rowheader')).toHaveTextContent('2026-03-10')
    expect(within(rows[0]).getAllByRole('cell').map((c) => c.textContent)).toEqual(['3', '3', '1', '1'])
  })

  it('데이터가 없으면 빈 상태를 보여준다', async () => {
    backend(empty)
    renderPage('/dashboard', <DashboardPage />)
    expect(await screen.findByText('이 기간에 방문이나 신청이 있는 폼이 없습니다.')).toBeInTheDocument()
    expect(screen.getByText('이 기간에 신청이 없습니다.')).toBeInTheDocument()
    expect(screen.queryByRole('table', { name: '폼별 유입 시트' })).not.toBeInTheDocument()
  })

  it('불러오지 못하면 오류와 다시 시도를 보여준다', async () => {
    let calls = 0
    backend(() => {
      calls += 1
      return calls === 1 ? json({ code: 'validation_failed', detail: '날짜 형식' }, 422) : json(dashboard)
    })
    renderPage('/dashboard', <DashboardPage />)
    const retry = await screen.findByRole('button', { name: '다시 시도' })
    expect(screen.queryByRole('table', { name: '폼별 유입 시트' })).not.toBeInTheDocument()
    fireEvent.click(retry)
    expect(await screen.findByRole('table', { name: '폼별 유입 시트' })).toBeInTheDocument()
  })

  it('영어 화면에서는 영어 문구를 쓴다', async () => {
    backend()
    renderPage('/dashboard', <DashboardPage />, '/dashboard', 'en')
    expect(await screen.findByRole('heading', { name: 'Dashboard', level: 1 })).toBeInTheDocument()
    await screen.findByRole('table', { name: 'Inflow by form sheet' })
    expect(screen.getByRole('button', { name: 'Download CSV' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Last 30 days' })).toBeInTheDocument()
    expect(screen.getByRole('list', { name: 'Recent sign-ups' })).toBeInTheDocument()
    expect(screen.getByRole('table', { name: 'Daily trend data' })).toBeInTheDocument()
  })
})
