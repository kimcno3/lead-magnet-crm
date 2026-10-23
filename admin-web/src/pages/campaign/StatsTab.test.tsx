import { fireEvent, screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearSession } from '../../api/client'
import { json, mockBackend, requestsTo } from '../../test/http'
import { renderPage } from '../../test/render'
import { CampaignDetailPage } from '../CampaignDetail'
import { campaign, channels, stats } from './fixtures'

beforeEach(() => clearSession())
afterEach(() => vi.unstubAllGlobals())

function backend() {
  return mockBackend((url) => {
    if (url.pathname === '/api/admin/campaigns/7') return json(campaign)
    if (url.pathname === '/api/admin/campaigns/7/stats') return json(stats)
    if (url.pathname === '/api/admin/campaigns/7/stats/channels') return json(channels)
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

    await vi.waitFor(() => {
      const last = requestsTo(fetchMock, '/api/admin/campaigns/7/stats').at(-1)!
      expect(last.searchParams.get('from')).toBe('2026-10-01')
      expect(last.searchParams.get('to')).toBe('2026-10-15')
    })
  })
})
