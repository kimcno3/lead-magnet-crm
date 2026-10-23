import { fireEvent, screen, waitFor, within } from '@testing-library/react'
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

  it('새 캠페인은 보존 기간(일)을 함께 보내고 기본값은 365일이다', async () => {
    const bodies: unknown[] = []
    mockBackend((url, init) => {
      if (url.pathname === '/api/admin/campaigns' && init?.method === 'POST') {
        bodies.push(JSON.parse(String(init.body)))
        return json({ id: 9 }, 201)
      }
      if (url.pathname === '/api/admin/campaigns') return json([])
      if (url.pathname === '/api/admin/stats/campaigns') return json({ from: null, to: null, campaigns: [] })
      return undefined
    })
    renderPage('/campaigns', <CampaignListPage />)
    fireEvent.click(await screen.findByRole('button', { name: '새 캠페인' }))
    const dialog = screen.getByRole('dialog', { name: '새 캠페인' })
    expect(within(dialog).getByLabelText('보존 기간(일)')).toHaveValue(365)
    fireEvent.change(within(dialog).getByLabelText('이름'), { target: { value: '봄' } })
    fireEvent.change(within(dialog).getByLabelText('보존 기간(일)'), { target: { value: '30' } })
    fireEvent.click(within(dialog).getByRole('button', { name: '만들기' }))
    await waitFor(() => expect(bodies).toHaveLength(1))
    expect(bodies[0]).toMatchObject({ name: '봄', retentionDays: 30 })
  })

  it('보존 기간이 범위를 벗어나면 서버 오류를 그 칸에 보여준다', async () => {
    mockBackend((url, init) => {
      if (url.pathname === '/api/admin/campaigns' && init?.method === 'POST') {
        return json({ code: 'validation_failed', errors: [{ field: 'retentionDays', message: 'retentionDays는 1~3650일이어야 합니다' }] }, 422)
      }
      if (url.pathname === '/api/admin/campaigns') return json([])
      if (url.pathname === '/api/admin/stats/campaigns') return json({ from: null, to: null, campaigns: [] })
      return undefined
    })
    renderPage('/campaigns', <CampaignListPage />)
    fireEvent.click(await screen.findByRole('button', { name: '새 캠페인' }))
    const dialog = screen.getByRole('dialog', { name: '새 캠페인' })
    fireEvent.change(within(dialog).getByLabelText('이름'), { target: { value: '봄' } })
    fireEvent.change(within(dialog).getByLabelText('보존 기간(일)'), { target: { value: '0' } })
    fireEvent.click(within(dialog).getByRole('button', { name: '만들기' }))
    expect(await within(dialog).findByText('retentionDays는 1~3650일이어야 합니다')).toBeInTheDocument()
  })
})
