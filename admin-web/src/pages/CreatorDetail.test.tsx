import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearSession } from '../api/client'
import { json, mockBackend, requestsTo } from '../test/http'
import { renderPage } from '../test/render'
import { CreatorDetailPage } from './CreatorDetail'
import { sea, sky, skyStats } from './creator/fixtures'

beforeEach(() => clearSession())
afterEach(() => vi.unstubAllGlobals())

function patches(fetchMock: ReturnType<typeof mockBackend>) {
  return fetchMock.mock.calls.filter((c) => (c[1] as RequestInit | undefined)?.method === 'PATCH')
}

function backend(creator = sky) {
  let current = creator
  return mockBackend((url, init) => {
    if (url.pathname === `/api/admin/creators/${creator.id}`) {
      if (init?.method === 'PATCH') current = { ...current, ...JSON.parse(String(init.body)) }
      return json(current)
    }
    if (url.pathname === `/api/admin/creators/${creator.id}/stats`) return json({ ...skyStats, creatorId: creator.id })
    return undefined
  })
}

describe('크리에이터 상세', () => {
  it('정보와 캠페인별 성과를 보여주고 캠페인 이름은 캠페인 상세로 간다', async () => {
    backend()
    renderPage('/creators/:id', <CreatorDetailPage />, '/creators/3')
    expect(await screen.findByRole('heading', { name: '김하늘', level: 1 })).toBeInTheDocument()
    expect(screen.getByText('@sky_kim')).toBeInTheDocument()
    expect(screen.getByText('R/S 20%, 매월 말 정산')).toBeInTheDocument()
    const table = await screen.findByRole('table', { name: '캠페인별 성과' })
    const rows = within(table).getAllByRole('row').slice(1)
    expect(rows).toHaveLength(2)
    expect(within(rows[0]).getByRole('link', { name: '가을 세미나' })).toHaveAttribute('href', '/campaigns/7')
    expect(within(rows[0]).getByText('1,200')).toBeInTheDocument()
    expect(within(rows[0]).getByText('66.7%')).toBeInTheDocument()
  })

  it('기간을 고르면 from·to를 붙여 다시 조회한다', async () => {
    const fetchMock = backend()
    renderPage('/creators/:id', <CreatorDetailPage />, '/creators/3')
    await screen.findByRole('table', { name: '캠페인별 성과' })
    fireEvent.change(screen.getByLabelText('시작일'), { target: { value: '2026-10-01' } })
    await waitFor(() => {
      const last = requestsTo(fetchMock, '/api/admin/creators/3/stats').at(-1)!
      expect(last.searchParams.get('from')).toBe('2026-10-01')
    })
  })

  it('수정은 바꾼 값을 PATCH로 보낸다(핸들을 비우면 빈 문자열)', async () => {
    const fetchMock = backend()
    renderPage('/creators/:id', <CreatorDetailPage />, '/creators/3')
    fireEvent.click(await screen.findByRole('button', { name: '수정' }))
    const dialog = await screen.findByRole('dialog', { name: '크리에이터 수정' })
    fireEvent.change(within(dialog).getByLabelText('이름'), { target: { value: '김하늘2' } })
    fireEvent.change(within(dialog).getByLabelText(/핸들/), { target: { value: '' } })
    fireEvent.click(within(dialog).getByRole('button', { name: '저장' }))

    await waitFor(() => expect(patches(fetchMock)).toHaveLength(1))
    expect(JSON.parse(String((patches(fetchMock)[0][1] as RequestInit).body))).toEqual({
      name: '김하늘2',
      handle: '',
      memo: 'R/S 20%, 매월 말 정산',
    })
    expect(await screen.findByRole('heading', { name: '김하늘2', level: 1 })).toBeInTheDocument()
  })

  it('보관은 확인을 거쳐 status=archived를 보낸다', async () => {
    const fetchMock = backend()
    renderPage('/creators/:id', <CreatorDetailPage />, '/creators/3')
    fireEvent.click(await screen.findByRole('button', { name: '보관' }))
    const dialog = await screen.findByRole('dialog', { name: '크리에이터 보관' })
    fireEvent.click(within(dialog).getByRole('button', { name: '보관하기' }))
    await waitFor(() => expect(patches(fetchMock)).toHaveLength(1))
    expect(JSON.parse(String((patches(fetchMock)[0][1] as RequestInit).body))).toEqual({ status: 'archived' })
  })

  it('보관된 크리에이터는 보관 버튼이 없고 안내를 보여준다', async () => {
    backend(sea)
    renderPage('/creators/:id', <CreatorDetailPage />, '/creators/4')
    expect(await screen.findByRole('heading', { name: '박바다', level: 1 })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '보관' })).not.toBeInTheDocument()
    expect(screen.getByText(/보관된 크리에이터입니다. 새 링크를 만들 수 없고/)).toBeInTheDocument()
  })

  it('없는 크리에이터면 오류를 보여준다', async () => {
    mockBackend(() => undefined)
    renderPage('/creators/:id', <CreatorDetailPage />, '/creators/99')
    expect((await screen.findAllByRole('alert'))[0]).toHaveTextContent('없음')
  })
})
