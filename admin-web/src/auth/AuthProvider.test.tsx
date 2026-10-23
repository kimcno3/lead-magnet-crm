import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router'
import { AppRoutes } from '../App'
import { clearSession, hasSession } from '../api/client'
import { json, mockBackend } from '../test/http'
import { AuthProvider } from './AuthProvider'

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

describe('세션 복원', () => {
  it('refresh는 성공했지만 /me가 실패하면 메모리 토큰을 지우고 로그인 화면으로 간다', async () => {
    mockBackend((url) => (url.pathname === '/api/admin/auth/me' ? json({ code: 'internal_error' }, 500) : undefined))
    renderAt('/templates')
    expect(await screen.findByRole('heading', { name: '운영자 로그인' })).toBeInTheDocument()
    expect(hasSession()).toBe(false)
  })

  it('refresh가 네트워크 오류면 로그아웃시키지 않고 연결 오류와 다시 시도를 보여준다', async () => {
    let attempts = 0
    mockBackend((url) => {
      if (url.pathname === '/api/admin/auth/refresh') {
        attempts += 1
        if (attempts === 1) throw new TypeError('Failed to fetch')
        return json({ accessToken: 'A2', tokenType: 'Bearer', expiresIn: 900 })
      }
      if (url.pathname === '/api/admin/templates') return json([])
      return undefined
    })
    renderAt('/templates')

    expect(await screen.findByRole('alert')).toHaveTextContent('서버에 연결하지 못했습니다')
    expect(screen.queryByRole('heading', { name: '운영자 로그인' })).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }))
    expect(await screen.findByRole('heading', { name: '템플릿', level: 1 })).toBeInTheDocument()
  })
})

describe('세션 복원 중 refresh 429', () => {
  it('로그아웃시키지 않고 다시 시도 상태를 보여 주며 다시 시도하면 복원된다', async () => {
    let attempts = 0
    mockBackend((url) => {
      if (url.pathname === '/api/admin/auth/refresh') {
        attempts += 1
        if (attempts === 1) return json({ code: 'too_many_requests' }, 429)
        return json({ accessToken: 'A2', tokenType: 'Bearer', expiresIn: 900 })
      }
      if (url.pathname === '/api/admin/templates') return json([])
      return undefined
    })
    renderAt('/templates')

    expect(await screen.findByRole('alert')).toHaveTextContent('잠시 후 다시 시도해 주세요')
    expect(screen.queryByRole('heading', { name: '운영자 로그인' })).not.toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }))
    expect(await screen.findByRole('heading', { name: '템플릿', level: 1 })).toBeInTheDocument()
  })
})

describe('로그아웃', () => {
  it('logout 요청이 실패해도 처리되지 않은 거절 없이 로그인 화면으로 간다', async () => {
    const unhandled = vi.fn()
    window.addEventListener('unhandledrejection', unhandled)
    mockBackend((url) => {
      if (url.pathname === '/api/admin/auth/logout') throw new TypeError('Failed to fetch')
      if (url.pathname === '/api/admin/templates') return json([])
      return undefined
    })
    renderAt('/templates')
    fireEvent.click(await screen.findByRole('button', { name: '로그아웃' }))

    expect(await screen.findByRole('heading', { name: '운영자 로그인' })).toBeInTheDocument()
    expect(hasSession()).toBe(false)
    await new Promise((r) => setTimeout(r, 0))
    expect(unhandled).not.toHaveBeenCalled()
    window.removeEventListener('unhandledrejection', unhandled)
  })
})
