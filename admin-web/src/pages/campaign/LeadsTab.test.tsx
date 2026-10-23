import { fireEvent, screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearSession } from '../../api/client'
import { json, mockBackend, requestsTo } from '../../test/http'
import { renderPage } from '../../test/render'
import { CampaignDetailPage } from '../CampaignDetail'
import { campaign, leadPage } from './fixtures'

beforeEach(() => clearSession())
afterEach(() => vi.unstubAllGlobals())

function backend(total = 45) {
  return mockBackend((url) => {
    if (url.pathname === '/api/admin/campaigns/7') return json(campaign)
    if (url.pathname === '/api/admin/campaigns/7/leads') {
      return json(leadPage(Number(url.searchParams.get('page') ?? 0), total))
    }
    return undefined
  })
}

describe('명단 탭', () => {
  it('전화번호를 하이픈 형식으로, 채널은 이름으로 보여준다', async () => {
    backend()
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7?tab=leads')
    const table = await screen.findByRole('table', { name: '신청자 명단' })
    const row = (await within(table).findByText('신청자0')).closest('tr')!
    expect(within(row).getByText('010-1234-5678')).toBeInTheDocument()
    expect(within(row).getByText('인스타그램')).toBeInTheDocument()
    expect(within(row).getByText('company: 하늘상사')).toBeInTheDocument()
    expect(within(row).getByText('2026-10-23 00:05')).toBeInTheDocument()
  })

  it('다음·이전 페이지가 page 파라미터를 바꾸고 size는 20이다', async () => {
    const fetchMock = backend()
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7?tab=leads')
    await screen.findByText('신청자0')
    expect(screen.getByText('1 / 3 페이지')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '이전 페이지' })).toBeDisabled()

    fireEvent.click(screen.getByRole('button', { name: '다음 페이지' }))
    await screen.findByText('신청자1')
    fireEvent.click(screen.getByRole('button', { name: '다음 페이지' }))
    await screen.findByText('신청자2')
    expect(screen.getByRole('button', { name: '다음 페이지' })).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: '이전 페이지' }))
    await screen.findByText('신청자1')

    const pages = requestsTo(fetchMock, '/api/admin/campaigns/7/leads').map((u) => u.searchParams.get('page'))
    expect(pages).toEqual(['0', '1', '2', '1'])
    expect(requestsTo(fetchMock, '/api/admin/campaigns/7/leads')[0].searchParams.get('size')).toBe('20')
  })

  it('폼·채널 필터는 파라미터로 보내고 첫 페이지로 돌아간다', async () => {
    const fetchMock = backend()
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7?tab=leads&page=2')
    await screen.findByText('신청자2')

    fireEvent.change(screen.getByLabelText('폼'), { target: { value: '11' } })
    await vi.waitFor(() => expect(requestsTo(fetchMock, '/api/admin/campaigns/7/leads').at(-1)!.searchParams.get('formId')).toBe('11'))
    fireEvent.change(screen.getByLabelText('채널'), { target: { value: 'direct' } })

    await vi.waitFor(() => {
      const last = requestsTo(fetchMock, '/api/admin/campaigns/7/leads').at(-1)!
      expect(last.searchParams.get('channel')).toBe('direct')
      expect(last.searchParams.get('formId')).toBe('11')
      expect(last.searchParams.get('page')).toBe('0')
    })
  })

  it('신청이 없으면 빈 상태를 보여준다', async () => {
    mockBackend((url) => {
      if (url.pathname === '/api/admin/campaigns/7') return json(campaign)
      if (url.pathname === '/api/admin/campaigns/7/leads') return json({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
      return undefined
    })
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7?tab=leads')
    expect(await screen.findByText(/아직 신청이 없습니다/)).toBeInTheDocument()
  })
})
