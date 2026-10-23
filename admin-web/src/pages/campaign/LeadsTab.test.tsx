import { fireEvent, screen, waitFor, within } from '@testing-library/react'
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
    await waitFor(() => expect(requestsTo(fetchMock, '/api/admin/campaigns/7/leads').at(-1)!.searchParams.get('formId')).toBe('11'))
    fireEvent.change(screen.getByLabelText('채널'), { target: { value: 'direct' } })

    await waitFor(() => {
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

describe('명단 페이지 경계', () => {
  it('현재 페이지 표시는 페이지 목록의 항목에 aria-current="page"로 붙는다', async () => {
    backend()
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7?tab=leads&page=1')
    await screen.findByText('신청자1')
    const pager = screen.getByRole('navigation', { name: '명단 페이지' })
    const current = within(pager).getAllByRole('listitem').filter((li) => li.getAttribute('aria-current') === 'page')
    expect(current).toHaveLength(1)
    expect(current[0]).toHaveTextContent('2 / 3 페이지')
    expect(pager.querySelectorAll('[aria-current]')).toHaveLength(1)
  })

  it('마지막 페이지를 넘으면 빈 상태와 첫 페이지로 버튼을 보여준다', async () => {
    const fetchMock = mockBackend((url) => {
      if (url.pathname === '/api/admin/campaigns/7') return json(campaign)
      if (url.pathname === '/api/admin/campaigns/7/leads') {
        const page = Number(url.searchParams.get('page') ?? 0)
        return page >= 3 ? json({ items: [], page, size: 20, totalElements: 45, totalPages: 3 }) : json(leadPage(page))
      }
      return undefined
    })
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7?tab=leads&page=9')

    expect(await screen.findByText(/이 페이지에는 신청이 없습니다/)).toBeInTheDocument()
    expect(screen.queryByRole('table', { name: '신청자 명단' })).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '첫 페이지로' }))
    expect(await screen.findByText('신청자0')).toBeInTheDocument()
    expect(requestsTo(fetchMock, '/api/admin/campaigns/7/leads').at(-1)!.searchParams.get('page')).toBe('0')
  })
})
