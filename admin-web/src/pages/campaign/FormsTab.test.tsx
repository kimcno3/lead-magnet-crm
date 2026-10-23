import { fireEvent, screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearSession } from '../../api/client'
import type { DistributionLink, Form } from '../../api/types'
import { json, mockBackend } from '../../test/http'
import { renderPage } from '../../test/render'
import { CampaignDetailPage } from '../CampaignDetail'
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
}

const igLink: DistributionLink = {
  id: 1,
  channel: 'instagram',
  code: 'Ab12Cd34',
  url: 'https://go.example.com/l/Ab12Cd34',
  utmSource: 'instagram',
  utmMedium: 'social',
  utmCampaign: 'fall-seminar',
  createdAt: '2026-10-01T00:00:00Z',
}

function backend() {
  const links: DistributionLink[] = [igLink]
  const fetchMock = mockBackend((url, init) => {
    if (url.pathname === '/api/admin/campaigns/7') return json(campaign)
    if (url.pathname === '/api/admin/forms/11') return json(form)
    if (url.pathname === '/api/admin/forms/11/links') {
      if (init?.method === 'POST') {
        const channel = JSON.parse(String(init.body)).channel
        const created = { ...igLink, id: 2, channel, code: 'Zz99', url: 'https://go.example.com/l/Zz99', utmSource: channel }
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

    await vi.waitFor(() => {
      const patch = fetchMock.mock.calls.find((c) => (c[1] as RequestInit | undefined)?.method === 'PATCH')
      expect(patch).toBeDefined()
      expect(String(patch![0])).toBe('/api/admin/forms/11')
      expect(JSON.parse(String((patch![1] as RequestInit).body))).toEqual({ status: 'closed' })
    })
  })
})
