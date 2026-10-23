import { screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearSession } from '../api/client'
import type { CampaignSummary } from '../api/types'
import { json, mockBackend } from '../test/http'
import { renderPage } from '../test/render'
import { CampaignListPage } from './CampaignList'

const summary: CampaignSummary = {
  id: 7,
  name: '가을 세미나',
  status: 'active',
  startsAt: '2026-09-30T15:00:00Z',
  endsAt: '2026-10-31T14:59:59Z',
  formCount: 2,
  createdAt: '2026-09-01T00:00:00Z',
}

beforeEach(() => clearSession())
afterEach(() => vi.unstubAllGlobals())

describe('캠페인 목록', () => {
  it('목록과 지표를 캠페인 id로 합쳐 보여준다', async () => {
    mockBackend((url) => {
      if (url.pathname === '/api/admin/campaigns') return json([summary])
      if (url.pathname === '/api/admin/stats/campaigns') {
        return json({ from: null, to: null, campaigns: [{ campaignId: 7, name: '가을 세미나', status: 'active', visits: 1520, visitors: 3, leads: 2, convertedVisitors: 2, conversionRate: 0.6667 }] })
      }
      return undefined
    })
    renderPage('/campaigns', <CampaignListPage />)
    const row = (await screen.findByRole('link', { name: '가을 세미나' })).closest('tr')!
    expect(within(row).getByText('1,520')).toBeInTheDocument()
    expect(within(row).getByText('66.7%')).toBeInTheDocument()
    expect(within(row).getByText('2026-10-01 ~ 2026-10-31')).toBeInTheDocument()
  })

  it('지표 조회가 실패해도 목록은 지표 없이 보여주고 안내를 붙인다', async () => {
    mockBackend((url) => {
      if (url.pathname === '/api/admin/campaigns') return json([summary])
      if (url.pathname === '/api/admin/stats/campaigns') return json({ code: 'internal_error', detail: '서버 오류가 발생했습니다' }, 500)
      return undefined
    })
    renderPage('/campaigns', <CampaignListPage />)
    const row = (await screen.findByRole('link', { name: '가을 세미나' })).closest('tr')!
    expect(within(row).getAllByText('-').length).toBeGreaterThanOrEqual(4)
    expect(screen.getByRole('alert')).toHaveTextContent('지표를 불러오지 못했습니다')
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument()
  })
})
