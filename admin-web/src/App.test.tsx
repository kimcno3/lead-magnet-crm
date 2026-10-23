import { render, screen } from '@testing-library/react'
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
  it('세션 복원에 실패하면 로그인 화면으로 보낸다', async () => {
    mockBackend((url) => (url.pathname === '/api/admin/auth/refresh' ? json({ code: 'invalid_refresh_token' }, 401) : undefined))
    renderAt('/templates')
    expect(await screen.findByRole('heading', { name: '운영자 로그인' })).toBeInTheDocument()
  })

  it('세션이 복원되면 사이드바에 운영자 이메일과 메뉴를 보여준다', async () => {
    mockBackend((url) => (url.pathname === '/api/admin/templates' ? json([]) : undefined))
    renderAt('/templates')
    expect(await screen.findByText('ops@example.com')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '캠페인' })).toHaveAttribute('href', '/campaigns')
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
