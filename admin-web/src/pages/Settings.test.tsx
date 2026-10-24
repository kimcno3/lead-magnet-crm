import { fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router'
import { AppRoutes } from '../App'
import { clearSession } from '../api/client'
import { AuthProvider } from '../auth/AuthProvider'
import { LanguageProvider } from '../i18n/LanguageProvider'
import { json, mockBackend } from '../test/http'

beforeEach(() => {
  clearSession()
  window.localStorage.clear()
  document.documentElement.removeAttribute('data-theme')
})
afterEach(() => vi.unstubAllGlobals())

function renderAt(path: string) {
  const fetchMock = mockBackend((url) => (url.pathname === '/api/admin/templates' ? json([]) : undefined))
  render(
    <LanguageProvider>
      <AuthProvider>
        <MemoryRouter initialEntries={[path]}>
          <AppRoutes />
        </MemoryRouter>
      </AuthProvider>
    </LanguageProvider>,
  )
  return fetchMock
}

describe('설정', () => {
  it('사이드바 하단에는 테마·언어·로그아웃 대신 설정 링크(톱니바퀴)만 있다', async () => {
    renderAt('/templates')
    const link = await screen.findByRole('link', { name: '설정' })
    expect(link).toHaveAttribute('href', '/settings')
    expect(link.querySelector('svg')).not.toBeNull()
    expect(screen.queryByLabelText('화면 테마')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '로그아웃' })).not.toBeInTheDocument()
  })

  it('사이드바를 접어도 설정 링크는 남는다', async () => {
    renderAt('/templates')
    fireEvent.click(await screen.findByRole('button', { name: '메뉴 접기' }))
    expect(screen.getByRole('link', { name: '설정' })).toBeInTheDocument()
  })

  it('설정 화면에서 테마·언어를 바꾸고 계정과 로그아웃을 본다', async () => {
    const fetchMock = renderAt('/settings')
    expect(await screen.findByRole('heading', { name: '설정', level: 1 })).toBeInTheDocument()

    const display = screen.getByRole('region', { name: '화면' })
    fireEvent.change(within(display).getByLabelText('화면 테마'), { target: { value: 'dark' } })
    expect(document.documentElement).toHaveAttribute('data-theme', 'dark')
    expect(within(display).getByLabelText('언어 / Language')).toHaveValue('ko')

    const account = screen.getByRole('region', { name: '계정' })
    expect(within(account).getByText('ops@example.com')).toBeInTheDocument()
    fireEvent.click(within(account).getByRole('button', { name: '로그아웃' }))
    expect(await screen.findByRole('heading', { name: '운영자 로그인' })).toBeInTheDocument()
    expect(fetchMock.mock.calls.some((c) => String(c[0]) === '/api/admin/auth/logout')).toBe(true)
  })

  it('영어로 바꾸면 설정 화면 문구도 바뀐다', async () => {
    renderAt('/settings')
    fireEvent.change(await screen.findByLabelText('언어 / Language'), { target: { value: 'en' } })
    expect(await screen.findByRole('heading', { name: 'Settings', level: 1 })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Settings' })).toBeInTheDocument()
  })
})
