import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearSession } from '../../api/client'
import { json, mockBackend, requestsTo } from '../../test/http'
import { renderPage } from '../../test/render'
import { CampaignDetailPage } from '../CampaignDetail'
import { campaign, channels, creatorStats, stats } from './fixtures'

beforeEach(() => clearSession())
afterEach(() => vi.unstubAllGlobals())

function backend() {
  return mockBackend((url) => {
    if (url.pathname === '/api/admin/campaigns/7') return json(campaign)
    if (url.pathname === '/api/admin/campaigns/7/stats') return json(stats)
    if (url.pathname === '/api/admin/campaigns/7/stats/channels') return json(channels)
    if (url.pathname === '/api/admin/campaigns/7/stats/creators') return json(creatorStats)
    return undefined
  })
}

describe('캠페인 성과 탭', () => {
  it('KPI 다섯 개를 포맷해서 보여준다(전환율 0.6667 → 66.7%)', async () => {
    backend()
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7')
    const kpis = await screen.findByRole('list', { name: '핵심 지표' })
    await within(kpis).findByText('66.7%')
    const items = within(kpis).getAllByRole('listitem')
    expect(items.map((li) => li.textContent)).toEqual([
      '방문1,520',
      '방문자3',
      '신청2',
      '전환 방문자2',
      '전환율66.7%',
    ])
    expect(screen.getByText(/전환율 = 신청한 방문자 \/ 방문자/)).toBeInTheDocument()
  })

  it('채널별 표에 다섯 채널 행이 있다', async () => {
    backend()
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7')
    const table = await screen.findByRole('table', { name: '채널별 성과' })
    await within(table).findByText('인스타그램')
    const bodyRows = within(table).getAllByRole('row').slice(1)
    expect(bodyRows).toHaveLength(5)
    expect(bodyRows.map((r) => within(r).getAllByRole('cell')[0].textContent)).toEqual([
      '인스타그램',
      'X',
      '유튜브',
      '스레드',
      '직접 유입',
    ])
    expect(within(bodyRows[0]).getByText('100.0%')).toBeInTheDocument()
  })

  it('기간을 고르면 from·to를 붙여 다시 조회한다', async () => {
    const fetchMock = backend()
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7')
    await screen.findByRole('table', { name: '채널별 성과' })

    fireEvent.change(screen.getByLabelText('시작일'), { target: { value: '2026-10-01' } })
    fireEvent.change(screen.getByLabelText('종료일'), { target: { value: '2026-10-15' } })

    await waitFor(() => {
      const last = requestsTo(fetchMock, '/api/admin/campaigns/7/stats').at(-1)!
      expect(last.searchParams.get('from')).toBe('2026-10-01')
      expect(last.searchParams.get('to')).toBe('2026-10-15')
    })
  })
})

describe('크리에이터별 성과', () => {
  it('크리에이터 행과 마지막 직접·자체 링크 행을 보여주고 이름은 크리에이터 상세로 간다', async () => {
    backend()
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7')
    const table = await screen.findByRole('table', { name: '크리에이터별 성과' })
    await within(table).findByText('직접·자체 링크')
    const rows = within(table).getAllByRole('row').slice(1)
    expect(rows).toHaveLength(3)
    expect(within(rows[0]).getByRole('link', { name: '김하늘' })).toHaveAttribute('href', '/creators/3')
    expect(within(rows[0]).getByText('66.7%')).toBeInTheDocument()
    expect(within(rows[1]).getByText('보관됨')).toBeInTheDocument()
    expect(within(rows[2]).getByText('직접·자체 링크')).toBeInTheDocument()
    expect(screen.getByText(/같은 크리에이터의 링크로 방문하고 신청한/)).toBeInTheDocument()
  })

  it('채널별 보기를 펼치면 그 크리에이터의 채널 행이 나온다', async () => {
    backend()
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7')
    const table = await screen.findByRole('table', { name: '크리에이터별 성과' })
    const toggle = await within(table).findByRole('button', { name: '직접·자체 링크 채널별 보기' })
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    expect(within(table).getAllByRole('row')).toHaveLength(4)

    fireEvent.click(toggle)

    expect(toggle).toHaveAttribute('aria-expanded', 'true')
    const rows = within(table).getAllByRole('row')
    expect(rows).toHaveLength(9)
    // 펼친 채널 행의 첫 칸은 행 머리글이다
    expect(rows.slice(4).map((r) => within(r).getByRole('rowheader').textContent)).toEqual([
      '인스타그램',
      'X',
      '유튜브',
      '스레드',
      '직접 유입',
    ])
    expect(within(rows[8]).getByText('600')).toBeInTheDocument()
  })

  it('크리에이터별 지표가 실패해도 나머지 성과는 보인다', async () => {
    mockBackend((url) => {
      if (url.pathname === '/api/admin/campaigns/7') return json(campaign)
      if (url.pathname === '/api/admin/campaigns/7/stats') return json(stats)
      if (url.pathname === '/api/admin/campaigns/7/stats/channels') return json(channels)
      if (url.pathname === '/api/admin/campaigns/7/stats/creators') return json({ code: 'internal_error', detail: '서버 오류가 발생했습니다' }, 500)
      return undefined
    })
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7')
    expect(await screen.findByRole('alert')).toHaveTextContent('서버 오류가 발생했습니다')
    expect(screen.queryByRole('table', { name: '크리에이터별 성과' })).not.toBeInTheDocument()
    expect(await screen.findByRole('table', { name: '채널별 성과' })).toBeInTheDocument()
  })
})

describe('성과 탭 오류', () => {
  it('채널별 지표가 실패하면 표를 숨기고 오류와 다시 시도를 보여준다', async () => {
    mockBackend((url) => {
      if (url.pathname === '/api/admin/campaigns/7') return json(campaign)
      if (url.pathname === '/api/admin/campaigns/7/stats') return json(stats)
      if (url.pathname === '/api/admin/campaigns/7/stats/channels') return json({ code: 'internal_error', detail: '서버 오류가 발생했습니다' }, 500)
      if (url.pathname === '/api/admin/campaigns/7/stats/creators') return json(creatorStats)
      return undefined
    })
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7')
    expect(await screen.findByRole('alert')).toHaveTextContent('서버 오류가 발생했습니다')
    expect(screen.queryByRole('table', { name: '채널별 성과' })).not.toBeInTheDocument()
    expect(screen.getByRole('list', { name: '핵심 지표' })).toBeInTheDocument()
  })
})
