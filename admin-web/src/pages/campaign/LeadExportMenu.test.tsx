import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearSession } from '../../api/client'
import { json, mockBackend, requestsTo } from '../../test/http'
import { renderPage } from '../../test/render'
import { CampaignDetailPage } from '../CampaignDetail'
import { campaign, leadPage } from './fixtures'

beforeEach(() => {
  clearSession()
  // jsdom에는 object URL과 내려받기가 없다.
  vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: vi.fn(() => 'blob:csv'), revokeObjectURL: vi.fn() }))
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
})
afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

function csv() {
  return new Response('﻿신청일\r\n', {
    headers: { 'Content-Type': 'text/csv;charset=UTF-8', 'Content-Disposition': 'attachment; filename="leads-campaign-7-x.csv"' },
  })
}

function backend(exportResponse: () => Response = csv) {
  return mockBackend((url) => {
    if (url.pathname === '/api/admin/campaigns/7') return json(campaign)
    if (url.pathname === '/api/admin/campaigns/7/leads') return json(leadPage(0, 3))
    if (url.pathname === '/api/admin/campaigns/7/leads.csv') return exportResponse()
    return undefined
  })
}

async function openMenu() {
  await screen.findByText('신*0')
  const button = screen.getByRole('button', { name: 'CSV 다운로드' })
  expect(button).toHaveAttribute('aria-expanded', 'false')
  fireEvent.click(button)
  expect(button).toHaveAttribute('aria-expanded', 'true')
  return screen.getByRole('menu', { name: 'CSV 다운로드' })
}

describe('명단 CSV 다운로드', () => {
  it('마스킹은 화면 필터로 바로 내려받고 원문을 요청하지 않는다', async () => {
    const fetchMock = backend()
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7?tab=leads&form=11&channel=instagram')
    const menu = await openMenu()

    fireEvent.click(within(menu).getByRole('menuitem', { name: /마스킹/ }))

    await waitFor(() => expect(HTMLAnchorElement.prototype.click).toHaveBeenCalled())
    const [request] = requestsTo(fetchMock, '/api/admin/campaigns/7/leads.csv')
    expect(request.searchParams.get('formId')).toBe('11')
    expect(request.searchParams.get('channel')).toBe('instagram')
    expect(request.searchParams.has('reveal')).toBe(false)
    expect(URL.createObjectURL).toHaveBeenCalled()
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
  })

  it('원문은 감사 로그에 남는다는 확인을 거친 뒤 reveal=true로 내려받는다', async () => {
    const fetchMock = backend()
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7?tab=leads')
    const menu = await openMenu()

    fireEvent.click(within(menu).getByRole('menuitem', { name: /원문/ }))
    const dialog = await screen.findByRole('dialog', { name: '원문 CSV 다운로드' })
    expect(dialog).toHaveTextContent('감사 로그에 기록')
    expect(dialog).toHaveTextContent('범위: 전체.')
    expect(requestsTo(fetchMock, '/api/admin/campaigns/7/leads.csv')).toHaveLength(0)

    fireEvent.click(within(dialog).getByRole('button', { name: '원문 다운로드' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    const [request] = requestsTo(fetchMock, '/api/admin/campaigns/7/leads.csv')
    expect(request.searchParams.get('reveal')).toBe('true')
    expect(HTMLAnchorElement.prototype.click).toHaveBeenCalled()
  })

  it('원문 확인 대화상자는 지금 필터 범위(폼·채널)를 알려준다', async () => {
    backend()
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7?tab=leads&form=11&channel=instagram')
    const menu = await openMenu()

    fireEvent.click(within(menu).getByRole('menuitem', { name: /원문/ }))

    const dialog = await screen.findByRole('dialog', { name: '원문 CSV 다운로드' })
    expect(dialog).toHaveTextContent('범위: 폼 세미나 신청 · 채널 인스타그램.')
  })

  it('원문 확인을 취소하면 요청하지 않는다', async () => {
    const fetchMock = backend()
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7?tab=leads')
    const menu = await openMenu()

    fireEvent.click(within(menu).getByRole('menuitem', { name: /원문/ }))
    const dialog = await screen.findByRole('dialog', { name: '원문 CSV 다운로드' })
    fireEvent.click(within(dialog).getByRole('button', { name: '취소' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(requestsTo(fetchMock, '/api/admin/campaigns/7/leads.csv')).toHaveLength(0)
  })

  it('원문 다운로드가 실패하면 대화상자에 오류를 보여주고 닫지 않는다', async () => {
    backend(() => json({ code: 'internal_error', detail: '서버 오류가 발생했습니다' }, 500))
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7?tab=leads')
    const menu = await openMenu()

    fireEvent.click(within(menu).getByRole('menuitem', { name: /원문/ }))
    const dialog = await screen.findByRole('dialog', { name: '원문 CSV 다운로드' })
    fireEvent.click(within(dialog).getByRole('button', { name: '원문 다운로드' }))

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('서버 오류가 발생했습니다')
    expect(HTMLAnchorElement.prototype.click).not.toHaveBeenCalled()
  })

  it('마스킹 다운로드가 실패하면 오류를 보여준다', async () => {
    backend(() => json({ code: 'internal_error', detail: '서버 오류가 발생했습니다' }, 500))
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7?tab=leads')
    const menu = await openMenu()

    fireEvent.click(within(menu).getByRole('menuitem', { name: /마스킹/ }))

    expect(await screen.findByText('서버 오류가 발생했습니다')).toBeInTheDocument()
  })

  it('Esc로 메뉴를 닫고 버튼으로 포커스를 돌린다', async () => {
    backend()
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7?tab=leads')
    const menu = await openMenu()
    expect(within(menu).getAllByRole('menuitem')[0]).toHaveFocus()

    fireEvent.keyDown(menu, { key: 'ArrowDown' })
    expect(within(menu).getAllByRole('menuitem')[1]).toHaveFocus()
    fireEvent.keyDown(menu, { key: 'Escape' })

    expect(screen.queryByRole('menu')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'CSV 다운로드' })).toHaveFocus()
  })
})
