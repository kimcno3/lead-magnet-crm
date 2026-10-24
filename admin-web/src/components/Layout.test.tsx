import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router'
import { AppRoutes } from '../App'
import { clearSession } from '../api/client'
import { AuthProvider } from '../auth/AuthProvider'
import { json, mockBackend } from '../test/http'

beforeEach(() => {
  clearSession()
  window.localStorage.clear()
  document.documentElement.removeAttribute('data-theme')
})
afterEach(() => vi.unstubAllGlobals())

function renderApp(path = '/templates') {
  mockBackend((url) => (url.pathname === '/api/admin/templates' ? json([]) : undefined))
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={[path]}>
        <AppRoutes />
      </MemoryRouter>
    </AuthProvider>,
  )
}

describe('사이드바', () => {
  it('접기 버튼으로 메뉴를 접고 펼치며, 접은 상태를 기억한다', async () => {
    const view = renderApp()
    const toggle = await screen.findByRole('button', { name: '메뉴 접기' })
    expect(toggle).toHaveAttribute('aria-expanded', 'true')

    fireEvent.click(toggle)
    expect(screen.getByRole('button', { name: '메뉴 펼치기' })).toHaveAttribute('aria-expanded', 'false')
    expect(screen.queryByRole('link', { name: '캠페인' })).not.toBeInTheDocument()
    expect(window.localStorage.getItem('lm.sidebar-collapsed')).toBe('1')

    view.unmount()
    renderApp()
    expect(await screen.findByRole('button', { name: '메뉴 펼치기' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '메뉴 펼치기' }))
    expect(screen.getByRole('link', { name: '캠페인' })).toBeInTheDocument()
  })

  it('화면 테마를 고르면 문서에 적용하고 기억하며, 시스템 설정은 data-theme을 지운다', async () => {
    renderApp('/settings')
    const picker = await screen.findByLabelText('화면 테마')
    expect(picker).toHaveValue('system')

    fireEvent.change(picker, { target: { value: 'dark' } })
    expect(document.documentElement).toHaveAttribute('data-theme', 'dark')
    expect(window.localStorage.getItem('lm.theme')).toBe('dark')

    fireEvent.change(picker, { target: { value: 'system' } })
    expect(document.documentElement).not.toHaveAttribute('data-theme')
  })

  it('저장소를 쓸 수 없어도 화면은 기본값으로 동작한다', async () => {
    const getItem = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    const setItem = vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    renderApp()
    fireEvent.click(await screen.findByRole('button', { name: '메뉴 접기' }))
    expect(screen.getByRole('button', { name: '메뉴 펼치기' })).toBeInTheDocument()
    getItem.mockRestore()
    setItem.mockRestore()
  })
})
