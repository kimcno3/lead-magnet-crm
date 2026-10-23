import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearSession } from '../api/client'
import { json, mockBackend } from '../test/http'
import { renderPage } from '../test/render'
import { CreatorListPage } from './CreatorList'
import { sea, sky } from './creator/fixtures'

beforeEach(() => clearSession())
afterEach(() => vi.unstubAllGlobals())

function posts(fetchMock: ReturnType<typeof mockBackend>) {
  return fetchMock.mock.calls.filter((c) => (c[1] as RequestInit | undefined)?.method === 'POST' && String(c[0]) === '/api/admin/creators')
}

describe('크리에이터 목록', () => {
  it('이름·핸들·상태를 표로 보여주고 이름은 상세로 가는 링크다', async () => {
    mockBackend((url) => (url.pathname === '/api/admin/creators' ? json([sky, sea]) : undefined))
    renderPage('/creators', <CreatorListPage />)
    const table = await screen.findByRole('table', { name: '크리에이터 목록' })
    const link = await within(table).findByRole('link', { name: '김하늘' })
    expect(link).toHaveAttribute('href', '/creators/3')
    const rows = within(table).getAllByRole('row').slice(1)
    expect(within(rows[0]).getByText('@sky_kim')).toBeInTheDocument()
    expect(within(rows[0]).getByText('협업 중')).toBeInTheDocument()
    expect(within(rows[1]).getByText('보관됨')).toBeInTheDocument()
  })

  it('없으면 등록을 안내한다', async () => {
    mockBackend((url) => (url.pathname === '/api/admin/creators' ? json([]) : undefined))
    renderPage('/creators', <CreatorListPage />)
    expect(await screen.findByText(/아직 등록한 크리에이터가 없습니다/)).toBeInTheDocument()
  })

  it('새 크리에이터를 등록하면 POST하고 상세로 이동한다', async () => {
    const fetchMock = mockBackend((url, init) => {
      if (url.pathname === '/api/admin/creators') return init?.method === 'POST' ? json(sky, 201) : json([])
      return undefined
    })
    renderPage('/creators', <CreatorListPage />)
    fireEvent.click(await screen.findByRole('button', { name: '새 크리에이터' }))
    const dialog = await screen.findByRole('dialog', { name: '새 크리에이터' })
    fireEvent.change(within(dialog).getByLabelText('이름'), { target: { value: ' 김하늘 ' } })
    fireEvent.change(within(dialog).getByLabelText(/핸들/), { target: { value: '@sky_kim' } })
    fireEvent.click(within(dialog).getByRole('button', { name: '등록' }))

    await waitFor(() => expect(posts(fetchMock)).toHaveLength(1))
    expect(JSON.parse(String((posts(fetchMock)[0][1] as RequestInit).body))).toEqual({ name: '김하늘', handle: '@sky_kim', memo: null })
    expect(await screen.findByText('다른 화면')).toBeInTheDocument()
  })

  it('서버 검증 오류는 해당 입력 옆에 보여준다', async () => {
    mockBackend((url, init) => {
      if (url.pathname === '/api/admin/creators') {
        return init?.method === 'POST'
          ? json({ code: 'validation_failed', detail: 'name은 1~100자여야 합니다', errors: [{ field: 'name', message: 'name은 1~100자여야 합니다' }] }, 422)
          : json([])
      }
      return undefined
    })
    renderPage('/creators', <CreatorListPage />)
    fireEvent.click(await screen.findByRole('button', { name: '새 크리에이터' }))
    const dialog = await screen.findByRole('dialog', { name: '새 크리에이터' })
    fireEvent.click(within(dialog).getByRole('button', { name: '등록' }))
    const name = within(dialog).getByLabelText('이름')
    await waitFor(() => expect(name).toHaveAttribute('aria-invalid', 'true'))
    expect(name).toHaveAccessibleDescription('name은 1~100자여야 합니다')
  })
})
