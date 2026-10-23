import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearSession } from '../../api/client'
import { json, mockBackend, requestsTo } from '../../test/http'
import { renderPage } from '../../test/render'
import { CampaignDetailPage } from '../CampaignDetail'
import { campaign, leadContact, leadPage } from './fixtures'

beforeEach(() => clearSession())
afterEach(() => vi.unstubAllGlobals())

function backend(total = 45) {
  return mockBackend((url) => {
    if (url.pathname === '/api/admin/campaigns/7') return json(campaign)
    if (url.pathname === '/api/admin/campaigns/7/leads') {
      return json(leadPage(Number(url.searchParams.get('page') ?? 0), total))
    }
    const contact = url.pathname.match(/^\/api\/admin\/leads\/(\d+)\/contact$/)
    if (contact) return json(leadContact(Number(contact[1]) - 100))
    return undefined
  })
}

/** 두 줄짜리 첫 페이지. 원문 보기가 그 줄에만 적용되는지 본다. */
function twoRowBackend(contactResponse: () => Response | Promise<Response> = () => json(leadContact(0))) {
  return mockBackend((url) => {
    if (url.pathname === '/api/admin/campaigns/7') return json(campaign)
    if (url.pathname === '/api/admin/campaigns/7/leads') {
      const page = leadPage(0, 2)
      return json({ ...page, items: [page.items[0], { ...page.items[0], id: 200, name: '김*희', email: null }] })
    }
    if (url.pathname === '/api/admin/leads/100/contact') return contactResponse()
    return undefined
  })
}

async function rowOf(text: string) {
  const table = await screen.findByRole('table', { name: '신청자 명단' })
  return (await within(table).findByText(text)).closest('tr')!
}

describe('명단 탭', () => {
  it('가린 연락처를 그대로 보여주고 채널은 이름으로 보여준다', async () => {
    backend()
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7?tab=leads')
    const table = await screen.findByRole('table', { name: '신청자 명단' })
    const row = (await within(table).findByText('신*0')).closest('tr')!
    expect(within(row).getByText('010-****-5678')).toBeInTheDocument()
    expect(within(row).getByText('l***@example.com')).toBeInTheDocument()
    expect(within(row).getByText('인스타그램')).toBeInTheDocument()
    expect(within(row).getByText('company: 하늘상사')).toBeInTheDocument()
    expect(within(row).getByText('2026-10-23 00:05')).toBeInTheDocument()
  })

  it('다음·이전 페이지가 page 파라미터를 바꾸고 size는 20이다', async () => {
    const fetchMock = backend()
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7?tab=leads')
    await screen.findByText('신*0')
    expect(screen.getByText('1 / 3 페이지')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '이전 페이지' })).toBeDisabled()

    fireEvent.click(screen.getByRole('button', { name: '다음 페이지' }))
    await screen.findByText('신*1')
    fireEvent.click(screen.getByRole('button', { name: '다음 페이지' }))
    await screen.findByText('신*2')
    expect(screen.getByRole('button', { name: '다음 페이지' })).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: '이전 페이지' }))
    await screen.findByText('신*1')

    const pages = requestsTo(fetchMock, '/api/admin/campaigns/7/leads').map((u) => u.searchParams.get('page'))
    expect(pages).toEqual(['0', '1', '2', '1'])
    expect(requestsTo(fetchMock, '/api/admin/campaigns/7/leads')[0].searchParams.get('size')).toBe('20')
  })

  it('폼·채널 필터는 파라미터로 보내고 첫 페이지로 돌아간다', async () => {
    const fetchMock = backend()
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7?tab=leads&page=2')
    await screen.findByText('신*2')

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

describe('원문 보기', () => {
  it('원문 보기는 그 줄만 원문으로 바꾸고 가리기로 되돌린다', async () => {
    const fetchMock = twoRowBackend()
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7?tab=leads')
    const row = await rowOf('신*0')
    const other = await rowOf('김*희')

    fireEvent.click(within(row).getByRole('button', { name: '원문 보기' }))

    expect(await within(row).findByText('신청자0')).toBeInTheDocument()
    expect(within(row).getByText('010-1234-5678')).toBeInTheDocument()
    expect(within(row).getByText('lead@example.com')).toBeInTheDocument()
    expect(within(row).queryByText('010-****-5678')).not.toBeInTheDocument()
    expect(within(other).getByText('010-****-5678')).toBeInTheDocument()
    expect(within(other).getByRole('button', { name: '원문 보기' })).toBeInTheDocument()
    expect(requestsTo(fetchMock, '/api/admin/leads/100/contact')).toHaveLength(1)
    expect(requestsTo(fetchMock, '/api/admin/leads/200/contact')).toHaveLength(0)

    fireEvent.click(within(row).getByRole('button', { name: '가리기' }))

    expect(within(row).getByText('신*0')).toBeInTheDocument()
    expect(within(row).getByText('010-****-5678')).toBeInTheDocument()
    expect(within(row).queryByText('신청자0')).not.toBeInTheDocument()
  })

  it('다른 페이지로 가면 원문이 다시 가려진다', async () => {
    backend()
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7?tab=leads')
    fireEvent.click(within(await rowOf('신*0')).getByRole('button', { name: '원문 보기' }))
    await screen.findByText('신청자0')

    fireEvent.click(screen.getByRole('button', { name: '다음 페이지' }))
    await screen.findByText('신*1')
    fireEvent.click(screen.getByRole('button', { name: '이전 페이지' }))

    const row = await rowOf('신*0')
    expect(within(row).getByRole('button', { name: '원문 보기' })).toBeInTheDocument()
    expect(screen.queryByText('신청자0')).not.toBeInTheDocument()
  })

  it('원문을 불러오지 못하면 그 줄에 오류를 보여주고 가린 값을 유지한다', async () => {
    twoRowBackend(() => json({ code: 'not_found', detail: '리소스를 찾을 수 없습니다' }, 404))
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7?tab=leads')
    const row = await rowOf('신*0')

    fireEvent.click(within(row).getByRole('button', { name: '원문 보기' }))

    expect(await within(row).findByRole('alert')).toHaveTextContent('리소스를 찾을 수 없습니다')
    expect(within(row).getByText('010-****-5678')).toBeInTheDocument()
    expect(within(row).getByRole('button', { name: '원문 보기' })).toBeEnabled()
  })
})

describe('원문 보기 경합과 복호화 실패', () => {
  it('원문 응답이 화면을 옮긴 뒤 오면 같은 페이지로 돌아와도 원문을 보이지 않는다', async () => {
    let release: (r: Response) => void = () => {}
    mockBackend((url) => {
      if (url.pathname === '/api/admin/campaigns/7') return json(campaign)
      if (url.pathname === '/api/admin/campaigns/7/leads') return json(leadPage(Number(url.searchParams.get('page') ?? 0)))
      if (url.pathname === '/api/admin/leads/100/contact') return new Promise<Response>((resolve) => (release = resolve))
      return undefined
    })
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7?tab=leads')
    fireEvent.click(within(await rowOf('신*0')).getByRole('button', { name: '원문 보기' }))

    fireEvent.click(screen.getByRole('button', { name: '다음 페이지' }))
    await screen.findByText('신*1')
    fireEvent.click(screen.getByRole('button', { name: '이전 페이지' }))
    const row = await rowOf('신*0')
    release(json(leadContact(0)))

    await waitFor(() => expect(within(row).getByRole('button', { name: '원문 보기' })).toBeEnabled())
    expect(screen.queryByText('신청자0')).not.toBeInTheDocument()
    expect(within(row).getByText('010-****-5678')).toBeInTheDocument()
  })

  it('필터를 바꿨다 되돌린 뒤 늦게 온 응답은 보이지 않는다', async () => {
    let release: (r: Response) => void = () => {}
    twoRowBackend(() => new Promise<Response>((resolve) => (release = resolve)))
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7?tab=leads')
    const row = await rowOf('신*0')
    fireEvent.click(within(row).getByRole('button', { name: '원문 보기' }))
    fireEvent.change(screen.getByLabelText('채널'), { target: { value: 'direct' } })
    fireEvent.change(screen.getByLabelText('채널'), { target: { value: '' } })
    const again = await rowOf('신*0')
    release(json(leadContact(0)))

    await waitFor(() => expect(within(again).getByRole('button', { name: '원문 보기' })).toBeEnabled())
    expect(screen.queryByText('신청자0')).not.toBeInTheDocument()
  })

  it('복호화할 수 없는 줄은 복호화 불가로 표시하고 원문 보기를 두지 않는다', async () => {
    mockBackend((url) => {
      if (url.pathname === '/api/admin/campaigns/7') return json(campaign)
      if (url.pathname === '/api/admin/campaigns/7/leads') {
        const page = leadPage(0, 2)
        const broken = { ...page.items[0], id: 300, name: null, phone: null, email: null, contactUnavailable: true }
        return json({ ...page, items: [page.items[0], broken] })
      }
      return undefined
    })
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7?tab=leads')
    const row = await rowOf('복호화 불가')

    expect(within(row).queryByRole('button', { name: '원문 보기' })).not.toBeInTheDocument()
    expect(within(await rowOf('신*0')).getByRole('button', { name: '원문 보기' })).toBeInTheDocument()
  })
})

describe('명단 페이지 경계', () => {
  it('현재 페이지 표시는 페이지 목록의 항목에 aria-current="page"로 붙는다', async () => {
    backend()
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7?tab=leads&page=1')
    await screen.findByText('신*1')
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
    expect(await screen.findByText('신*0')).toBeInTheDocument()
    expect(requestsTo(fetchMock, '/api/admin/campaigns/7/leads').at(-1)!.searchParams.get('page')).toBe('0')
  })
})

describe('파기와 동의 문구', () => {
  function anonymizedBackend(consent = () => json({ consentText: '개인정보 수집에 동의합니다', consentSha256: 'ab'.repeat(32), consentAt: '2026-10-22T15:05:00Z', anonymizedAt: null })) {
    return mockBackend((url) => {
      if (url.pathname === '/api/admin/campaigns/7') return json(campaign)
      if (url.pathname === '/api/admin/campaigns/7/leads') {
        const page = leadPage(0, 2)
        const gone = { ...page.items[0], id: 400, name: null, phone: null, email: null, payload: { company: null }, anonymizedAt: '2026-10-23T18:00:00Z' }
        return json({ ...page, items: [page.items[0], gone] })
      }
      if (url.pathname === '/api/admin/leads/100/consent') return consent()
      return undefined
    })
  }

  it('파기된 줄은 파기됨으로 표시하고 원문·동의 문구 보기를 두지 않는다', async () => {
    anonymizedBackend()
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7?tab=leads')
    const row = await rowOf('파기됨')

    expect(within(row).queryByRole('button', { name: '원문 보기' })).not.toBeInTheDocument()
    expect(within(row).queryByRole('button', { name: '동의 문구 보기' })).not.toBeInTheDocument()
    const kept = await rowOf('신*0')
    expect(within(kept).getByRole('button', { name: '원문 보기' })).toBeInTheDocument()
    expect(within(kept).getByRole('button', { name: '동의 문구 보기' })).toBeInTheDocument()
  })

  it('동의 문구 보기는 문구 원문과 sha256, 동의 시각을 대화상자로 보여준다', async () => {
    const fetchMock = anonymizedBackend()
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7?tab=leads')
    fireEvent.click(within(await rowOf('신*0')).getByRole('button', { name: '동의 문구 보기' }))

    const dialog = await screen.findByRole('dialog', { name: '동의 문구' })
    expect(await within(dialog).findByText('개인정보 수집에 동의합니다')).toBeInTheDocument()
    expect(within(dialog).getByText('ab'.repeat(32))).toBeInTheDocument()
    expect(within(dialog).getByText('2026-10-23 00:05')).toBeInTheDocument()
    expect(requestsTo(fetchMock, '/api/admin/leads/100/consent')).toHaveLength(1)
    expect(requestsTo(fetchMock, '/api/admin/leads/100/contact')).toHaveLength(0)

    fireEvent.click(within(dialog).getByRole('button', { name: '닫기' }))
    await waitFor(() => expect(screen.queryByRole('dialog', { name: '동의 문구' })).not.toBeInTheDocument())
  })

  it('백필하지 못한 신청은 문구가 없다고 알리고 조회 실패는 오류를 보여준다', async () => {
    anonymizedBackend(() => json({ consentText: null, consentSha256: null, consentAt: '2026-10-22T15:05:00Z', anonymizedAt: null }))
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7?tab=leads')
    fireEvent.click(within(await rowOf('신*0')).getByRole('button', { name: '동의 문구 보기' }))
    const dialog = await screen.findByRole('dialog', { name: '동의 문구' })
    expect(await within(dialog).findByText(/저장된 동의 문구가 없습니다/)).toBeInTheDocument()
  })

  it('동의 문구를 불러오지 못하면 대화상자에 오류를 보여준다', async () => {
    anonymizedBackend(() => json({ code: 'internal_error', detail: '서버 오류가 발생했습니다' }, 500))
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7?tab=leads')
    fireEvent.click(within(await rowOf('신*0')).getByRole('button', { name: '동의 문구 보기' }))
    const dialog = await screen.findByRole('dialog', { name: '동의 문구' })
    expect(await within(dialog).findByRole('alert')).toBeInTheDocument()
  })
})
