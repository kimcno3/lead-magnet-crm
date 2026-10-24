import { render, screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router'
import { AppRoutes } from './App'
import { clearSession } from './api/client'
import { AuthProvider } from './auth/AuthProvider'
import { json, mockBackend } from './test/http'

beforeEach(() => clearSession())
afterEach(() => vi.unstubAllGlobals())

function renderAt(path: string) {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={[path]}>
        <AppRoutes />
      </MemoryRouter>
    </AuthProvider>,
  )
}

describe('App 라우팅', () => {
  it('크리에이터 메뉴는 목록과 상세 화면으로 간다', async () => {
    mockBackend((url) => (url.pathname === '/api/admin/creators' ? json([]) : url.pathname === '/api/admin/creators/3' ? json({ id: 3, name: '김하늘', handle: null, memo: null, status: 'active', createdAt: '2026-10-01T00:00:00Z', updatedAt: '2026-10-01T00:00:00Z' }) : url.pathname === '/api/admin/creators/3/stats' ? json({ creatorId: 3, name: '김하늘', status: 'active', from: null, to: null, campaigns: [] }) : undefined))
    renderAt('/creators')
    expect(await screen.findByRole('heading', { name: '크리에이터', level: 1 })).toBeInTheDocument()
    renderAt('/creators/3')
    expect(await screen.findByRole('heading', { name: '김하늘', level: 1 })).toBeInTheDocument()
  })

  it('첫 화면은 대시보드이고 주 메뉴의 첫 항목이다', async () => {
    mockBackend((url) =>
      url.pathname === '/api/admin/stats/dashboard'
        ? json({ from: '2026-03-10', to: '2026-03-12', totals: { visits: 0, visitors: 0, leads: 0, convertedVisitors: 0, conversionRate: 0 }, daily: [], forms: [], channels: [], creators: [], recentLeads: [] })
        : undefined,
    )
    renderAt('/')
    expect(await screen.findByRole('heading', { name: '대시보드', level: 1 })).toBeInTheDocument()
    const nav = screen.getByRole('navigation', { name: '주 메뉴' })
    expect(within(nav).getAllByRole('link')[0]).toHaveAttribute('href', '/dashboard')
    expect(within(nav).getAllByRole('link')[0]).toHaveTextContent('대시보드')
  })

  it('세션 복원에 실패하면 로그인 화면으로 보낸다', async () => {
    mockBackend((url) => (url.pathname === '/api/admin/auth/refresh' ? json({ code: 'invalid_refresh_token' }, 401) : undefined))
    renderAt('/templates')
    expect(await screen.findByRole('heading', { name: '운영자 로그인' })).toBeInTheDocument()
  })

  it('세션이 복원되면 사이드바에 메뉴와 설정 링크를, 설정 화면에 운영자 이메일을 보여준다', async () => {
    mockBackend((url) => (url.pathname === '/api/admin/templates' ? json([]) : undefined))
    renderAt('/templates')
    expect(await screen.findByRole('link', { name: '설정' })).toHaveAttribute('href', '/settings')
    expect(screen.getByRole('link', { name: '캠페인' })).toHaveAttribute('href', '/campaigns')
    expect(screen.getByRole('link', { name: '크리에이터' })).toHaveAttribute('href', '/creators')
    expect(screen.getByRole('heading', { name: '템플릿', level: 1 })).toBeInTheDocument()
  })

  it('API가 401을 내고 refresh도 실패하면 로그인 화면으로 돌아간다', async () => {
    let refreshes = 0
    mockBackend((url) => {
      if (url.pathname === '/api/admin/auth/refresh') {
        refreshes += 1
        return refreshes === 1
          ? json({ accessToken: 'A1', tokenType: 'Bearer', expiresIn: 900 })
          : json({ code: 'refresh_token_reused' }, 401)
      }
      if (url.pathname === '/api/admin/templates') return json({ code: 'unauthorized' }, 401)
      return undefined
    })
    renderAt('/templates')
    expect(await screen.findByRole('heading', { name: '운영자 로그인' })).toBeInTheDocument()
  })
})
