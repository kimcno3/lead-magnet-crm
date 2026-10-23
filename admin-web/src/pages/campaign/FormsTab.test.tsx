import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearSession } from '../../api/client'
import type { DistributionLink, Form } from '../../api/types'
import { json, mockBackend } from '../../test/http'
import { renderPage } from '../../test/render'
import { CampaignDetailPage } from '../CampaignDetail'
import { sea, sky } from '../creator/fixtures'
import { campaign } from './fixtures'

beforeEach(() => clearSession())
afterEach(() => vi.unstubAllGlobals())

const form: Form = {
  id: 11,
  campaignId: 7,
  templateId: 3,
  slug: 'fall-seminar',
  title: '세미나 신청',
  consentText: '동의합니다',
  fieldConfig: [{ name: 'phone', type: 'tel', required: true, label: '연락처', options: null }],
  status: 'published',
  publishedAt: '2026-10-01T00:00:00Z',
  closedAt: null,
  createdAt: '2026-09-30T00:00:00Z',
  publishedTemplateSha256: 'a'.repeat(64),
  templateSha256: 'a'.repeat(64),
}

const igLink: DistributionLink = {
  id: 1,
  channel: 'instagram',
  code: 'Ab12Cd34',
  url: 'https://go.example.com/l/Ab12Cd34',
  creator: null,
  utmSource: 'instagram',
  utmMedium: 'social',
  utmCampaign: 'fall-seminar',
  utmContent: null,
  createdAt: '2026-10-01T00:00:00Z',
}

const skyIgLink: DistributionLink = {
  ...igLink,
  id: 5,
  creator: { id: 3, name: '김하늘' },
  code: 'Sky00001',
  url: 'https://go.example.com/l/Sky00001',
}

function backend(initial: DistributionLink[] = [igLink], current: Form = form) {
  const links: DistributionLink[] = [...initial]
  const fetchMock = mockBackend((url, init) => {
    if (url.pathname === '/api/admin/campaigns/7') return json(campaign)
    if (url.pathname === '/api/admin/creators') return json([sky, sea])
    if (url.pathname === '/api/admin/forms/11') return json(current)
    if (url.pathname === '/api/admin/forms/11/links') {
      if (init?.method === 'POST') {
        const { channel, creatorId } = JSON.parse(String(init.body))
        const creator = creatorId ? { id: creatorId, name: [sky, sea].find((c) => c.id === creatorId)!.name } : null
        const created = { ...igLink, id: 2 + links.length, channel, creator, code: 'Zz99', url: 'https://go.example.com/l/Zz99', utmSource: channel }
        links.push(created)
        return json(created, 201)
      }
      return json(links)
    }
    return undefined
  })
  return fetchMock
}

describe('폼 탭', () => {
  it('게시 시점 템플릿과 현재 템플릿의 sha256이 다르면 안내를 보여준다', async () => {
    backend([igLink], { ...form, templateSha256: 'b'.repeat(64) })
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7?tab=forms&open=11')

    expect(await screen.findByText(/게시 시점 템플릿과 현재 템플릿이 다릅니다/)).toBeInTheDocument()
  })

  it('sha256이 같거나 아직 게시하지 않은 폼에는 안내가 없다', async () => {
    backend()
    const { unmount } = renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7?tab=forms&open=11')
    await screen.findByRole('table', { name: '채널별 배포 링크' })
    expect(screen.queryByText(/게시 시점 템플릿과 현재 템플릿이 다릅니다/)).toBeNull()
    unmount()

    backend([igLink], { ...form, status: 'draft', publishedTemplateSha256: null, templateSha256: 'b'.repeat(64) })
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7?tab=forms&open=11')
    await screen.findByRole('table', { name: '채널별 배포 링크' })
    expect(screen.queryByText(/게시 시점 템플릿과 현재 템플릿이 다릅니다/)).toBeNull()
  })

  it('폼 행을 펼치면 공개 URL과 채널 네 개의 배포 링크를 보여준다', async () => {
    backend()
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7?tab=forms')
    const toggle = await screen.findByRole('button', { name: /세미나 신청/ })
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    fireEvent.click(toggle)
    expect(toggle).toHaveAttribute('aria-expanded', 'true')

    expect(await screen.findByText('https://go.example.com/f/fall-seminar')).toBeInTheDocument()
    const table = await screen.findByRole('table', { name: '채널별 배포 링크' })
    const rows = within(table).getAllByRole('row').slice(1)
    expect(rows.map((r) => within(r).getAllByRole('cell')[0].textContent)).toEqual(['인스타그램', 'X', '유튜브', '스레드'])
    expect(within(rows[0]).getByText('https://go.example.com/l/Ab12Cd34')).toBeInTheDocument()
    expect(within(rows[0]).getByText('instagram / social / fall-seminar')).toBeInTheDocument()
    expect(within(rows[1]).getByRole('button', { name: '링크 만들기' })).toBeEnabled()
  })

  it('링크 만들기를 누르면 해당 채널로 만들고 URL을 보여준다', async () => {
    const fetchMock = backend()
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7?tab=forms&open=11')
    const table = await screen.findByRole('table', { name: '채널별 배포 링크' })
    const xRow = (await within(table).findByText('X')).closest('tr')!

    fireEvent.click(await within(xRow).findByRole('button', { name: '링크 만들기' }))

    expect(await within(xRow).findByText('https://go.example.com/l/Zz99')).toBeInTheDocument()
    const post = fetchMock.mock.calls.find(
      (c) => String(c[0]) === '/api/admin/forms/11/links' && (c[1] as RequestInit | undefined)?.method === 'POST',
    )!
    expect(JSON.parse(String((post[1] as RequestInit).body))).toEqual({ channel: 'x' })
  })

  it('게시된 폼의 마감은 확인 다이얼로그를 거쳐 PATCH status=closed를 보낸다', async () => {
    const fetchMock = backend()
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7?tab=forms&open=11')
    fireEvent.click(await screen.findByRole('button', { name: '마감' }))
    const dialog = await screen.findByRole('dialog', { name: '폼 마감' })
    expect(fetchMock.mock.calls.some((c) => (c[1] as RequestInit | undefined)?.method === 'PATCH')).toBe(false)

    fireEvent.click(within(dialog).getByRole('button', { name: '마감하기' }))

    await waitFor(() => {
      const patch = fetchMock.mock.calls.find((c) => (c[1] as RequestInit | undefined)?.method === 'PATCH')
      expect(patch).toBeDefined()
      expect(String(patch![0])).toBe('/api/admin/forms/11')
      expect(JSON.parse(String((patch![1] as RequestInit).body))).toEqual({ status: 'closed' })
    })
  })
})

function postBodies(fetchMock: ReturnType<typeof mockBackend>) {
  return fetchMock.mock.calls
    .filter((c) => String(c[0]) === '/api/admin/forms/11/links' && (c[1] as RequestInit | undefined)?.method === 'POST')
    .map((c) => JSON.parse(String((c[1] as RequestInit).body)))
}

describe('크리에이터 링크', () => {
  it('자체 링크 표에는 크리에이터 없는 링크만 나오고 크리에이터 링크는 크리에이터 표에 나온다', async () => {
    backend([skyIgLink])
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7?tab=forms&open=11')
    const own = await screen.findByRole('table', { name: '채널별 배포 링크' })
    const igRow = (await within(own).findByText('인스타그램')).closest('tr')!
    expect(await within(igRow).findByRole('button', { name: '링크 만들기' })).toBeEnabled()

    const table = await screen.findByRole('table', { name: '크리에이터별 배포 링크' })
    const header = within(table).getAllByRole('columnheader').map((h) => h.textContent)
    expect(header).toEqual(['크리에이터', '인스타그램', 'X', '유튜브', '스레드'])
    const row = (await within(table).findByText('김하늘')).closest('tr')!
    expect(within(row).getByText('Sky00001')).toBeInTheDocument()
    expect(within(row).getByRole('button', { name: '김하늘 X 링크 만들기' })).toBeEnabled()
  })

  it('크리에이터를 골라 추가하고 칸의 만들기를 누르면 channel과 creatorId로 만든다', async () => {
    const fetchMock = backend([])
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7?tab=forms&open=11')
    const select = await screen.findByRole('combobox', { name: '크리에이터' })
    // 보관된 크리에이터는 고를 수 없다
    expect(within(select).queryByRole('option', { name: '박바다' })).not.toBeInTheDocument()
    fireEvent.change(select, { target: { value: '3' } })
    fireEvent.click(screen.getByRole('button', { name: '표에 추가' }))

    const table = await screen.findByRole('table', { name: '크리에이터별 배포 링크' })
    fireEvent.click(within(table).getByRole('button', { name: '김하늘 유튜브 링크 만들기' }))

    await waitFor(() => expect(postBodies(fetchMock)).toEqual([{ channel: 'youtube', creatorId: 3 }]))
    expect(await within(table).findByText('Zz99')).toBeInTheDocument()
  })

  it('보관된 크리에이터의 행은 이미 만든 링크만 보여주고 새로 만들 수 없다', async () => {
    backend([{ ...skyIgLink, creator: { id: 4, name: '박바다' } }])
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7?tab=forms&open=11')
    const table = await screen.findByRole('table', { name: '크리에이터별 배포 링크' })
    const row = (await within(table).findByText('박바다')).closest('tr')!
    expect(within(row).getByText('보관됨')).toBeInTheDocument()
    expect(within(row).getByRole('button', { name: '박바다 X 링크 만들기' })).toBeDisabled()
  })

  it('크리에이터 목록을 불러오는 동안 로딩 자리를 보여준다', async () => {
    mockBackend((url) => {
      if (url.pathname === '/api/admin/campaigns/7') return json(campaign)
      if (url.pathname === '/api/admin/forms/11') return json(form)
      if (url.pathname === '/api/admin/forms/11/links') return json([igLink])
      if (url.pathname === '/api/admin/creators') return new Promise<Response>(() => {})
      return undefined
    })
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7?tab=forms&open=11')
    const loading = await screen.findByText('크리에이터 목록을 불러오는 중')
    expect(loading.closest('[aria-busy="true"]')).not.toBeNull()
    expect(loading.closest('[aria-busy="true"]')!.querySelector('.skeleton')).not.toBeNull()
    expect(screen.queryByRole('combobox', { name: '크리에이터' })).not.toBeInTheDocument()
  })

  it('크리에이터 링크 영역의 이름은 한 번만 붙는다', async () => {
    backend([skyIgLink])
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7?tab=forms&open=11')
    await screen.findByRole('table', { name: '크리에이터별 배포 링크' })
    expect(screen.getAllByRole('region', { name: '크리에이터별 배포 링크' })).toHaveLength(1)
  })

  it('등록한 크리에이터가 없으면 크리에이터 메뉴로 안내한다', async () => {
    mockBackend((url) => {
      if (url.pathname === '/api/admin/campaigns/7') return json(campaign)
      if (url.pathname === '/api/admin/forms/11') return json(form)
      if (url.pathname === '/api/admin/forms/11/links') return json([igLink])
      if (url.pathname === '/api/admin/creators') return json([])
      return undefined
    })
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7?tab=forms&open=11')
    expect(await screen.findByRole('link', { name: '크리에이터 메뉴' })).toHaveAttribute('href', '/creators')
    expect(screen.queryByRole('table', { name: '크리에이터별 배포 링크' })).not.toBeInTheDocument()
  })
})

describe('필드 설정 편집(draft)', () => {
  const draft: Form = {
    ...form,
    id: 12,
    slug: 'fall-ebook',
    title: '전자책 받기',
    status: 'draft',
    publishedAt: null,
    fieldConfig: [
      { name: 'phone', type: 'tel', required: true, label: '연락처', options: null },
      { name: 'memo', type: 'text', required: false, label: null, options: null },
    ],
  }

  function draftBackend() {
    return mockBackend((url, init) => {
      if (url.pathname === '/api/admin/campaigns/7') return json(campaign)
      if (url.pathname === '/api/admin/forms/12') return init?.method === 'PATCH' ? json(draft) : json(draft)
      if (url.pathname === '/api/admin/forms/12/links') return json([])
      if (url.pathname === '/api/admin/creators') return json([])
      return undefined
    })
  }

  function patches(fetchMock: ReturnType<typeof mockBackend>) {
    return fetchMock.mock.calls.filter((c) => (c[1] as RequestInit | undefined)?.method === 'PATCH')
  }

  it('필수 체크박스는 필드 이름이 들어간 접근 가능한 이름을 가진다', async () => {
    draftBackend()
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7?tab=forms&open=12')
    expect(await screen.findByRole('checkbox', { name: 'phone 필수' })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: 'memo 필수' })).not.toBeChecked()
  })

  it('원래 있던 라벨을 비우면 저장을 막고 그 입력 옆에 오류를 보여준다', async () => {
    const fetchMock = draftBackend()
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7?tab=forms&open=12')
    const label = await screen.findByRole('textbox', { name: 'phone 라벨' })

    fireEvent.change(label, { target: { value: '  ' } })
    fireEvent.click(screen.getByRole('button', { name: '필드 설정 저장' }))

    await waitFor(() => expect(label).toHaveAttribute('aria-invalid', 'true'))
    expect(label).toHaveAccessibleDescription('라벨을 입력해 주세요')
    expect(screen.getByRole('status')).toHaveTextContent('라벨을 비울 수 없습니다')
    expect(patches(fetchMock)).toHaveLength(0)
  })

  it('라벨과 필수 여부를 바꾸면 fieldConfig로 저장한다(라벨 없던 필드는 비워 둬도 된다)', async () => {
    const fetchMock = draftBackend()
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7?tab=forms&open=12')
    fireEvent.change(await screen.findByRole('textbox', { name: 'phone 라벨' }), { target: { value: '휴대전화' } })
    fireEvent.click(screen.getByRole('checkbox', { name: 'memo 필수' }))
    fireEvent.click(screen.getByRole('button', { name: '필드 설정 저장' }))

    await waitFor(() => expect(patches(fetchMock)).toHaveLength(1))
    expect(JSON.parse(String((patches(fetchMock)[0][1] as RequestInit).body))).toEqual({
      fieldConfig: [
        { name: 'phone', label: '휴대전화', required: true },
        { name: 'memo', required: true },
      ],
    })
  })
})
