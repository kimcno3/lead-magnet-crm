import { fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router'
import { clearSession } from '../api/client'
import { AuthProvider } from '../auth/AuthProvider'
import { json, mockBackend, requestsTo } from '../test/http'
import { LoginPage } from './Login'

function renderLogin() {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={[{ pathname: '/login', state: { from: '/campaigns/7?tab=leads' } }]}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/campaigns/:id" element={<p>캠페인 7 화면</p>} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  )
}

beforeEach(() => clearSession())
afterEach(() => vi.unstubAllGlobals())

describe('로그인 화면', () => {
  it('성공하면 원래 가려던 경로로 이동한다', async () => {
    const fetchMock = mockBackend((url, init) => {
      if (url.pathname === '/api/admin/auth/refresh') return json({ code: 'invalid_refresh_token' }, 401)
      if (url.pathname === '/api/admin/auth/login') {
        expect(JSON.parse(String(init?.body))).toEqual({ email: 'ops@example.com', password: 'correct-horse-1' })
        return json({ accessToken: 'A1', tokenType: 'Bearer', expiresIn: 900 })
      }
      return undefined
    })
    renderLogin()

    fireEvent.change(await screen.findByLabelText('이메일'), { target: { value: 'ops@example.com' } })
    fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'correct-horse-1' } })
    fireEvent.click(screen.getByRole('button', { name: '로그인' }))

    expect(await screen.findByText('캠페인 7 화면')).toBeInTheDocument()
    expect(requestsTo(fetchMock, '/api/admin/auth/me')).toHaveLength(1)
  })

  it('401이면 자격 증명 오류 문구를 보여주고 화면에 남는다', async () => {
    mockBackend((url) => {
      if (url.pathname === '/api/admin/auth/refresh') return json({ code: 'invalid_refresh_token' }, 401)
      if (url.pathname === '/api/admin/auth/login') {
        return json({ status: 401, code: 'invalid_credentials', detail: '이메일 또는 비밀번호가 올바르지 않습니다' }, 401)
      }
      return undefined
    })
    renderLogin()

    fireEvent.change(await screen.findByLabelText('이메일'), { target: { value: 'ops@example.com' } })
    fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'wrong-password' } })
    fireEvent.click(screen.getByRole('button', { name: '로그인' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('이메일 또는 비밀번호가 올바르지 않습니다')
    expect(screen.queryByText('캠페인 7 화면')).not.toBeInTheDocument()
    expect(screen.getByLabelText('비밀번호')).toHaveValue('')
  })

  it.each([
    ['900', '약 15분'],
    ['61', '약 2분'],
    ['30', '약 1분'],
  ])('429면 Retry-After %s초를 분으로 올려 잠시 후 다시 시도하라고 안내한다', async (retryAfter, minutes) => {
    mockBackend((url) => {
      if (url.pathname === '/api/admin/auth/refresh') return json({ code: 'invalid_refresh_token' }, 401)
      if (url.pathname === '/api/admin/auth/login') {
        return new Response(
          JSON.stringify({ status: 429, code: 'too_many_login_attempts', detail: '로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요' }),
          { status: 429, headers: { 'Content-Type': 'application/problem+json', 'Retry-After': retryAfter } },
        )
      }
      return undefined
    })
    renderLogin()

    fireEvent.change(await screen.findByLabelText('이메일'), { target: { value: 'ops@example.com' } })
    fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'wrong-password' } })
    fireEvent.click(screen.getByRole('button', { name: '로그인' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(`잠시 후 다시 시도해 주세요(${minutes})`)
    expect(screen.getByLabelText('비밀번호')).toHaveValue('')
  })

  it('429에 Retry-After가 없으면 분 없이 안내한다', async () => {
    mockBackend((url) => {
      if (url.pathname === '/api/admin/auth/refresh') return json({ code: 'invalid_refresh_token' }, 401)
      if (url.pathname === '/api/admin/auth/login') return json({ status: 429, code: 'too_many_login_attempts' }, 429)
      return undefined
    })
    renderLogin()

    fireEvent.change(await screen.findByLabelText('이메일'), { target: { value: 'ops@example.com' } })
    fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'wrong-password' } })
    fireEvent.click(screen.getByRole('button', { name: '로그인' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/^잠시 후 다시 시도해 주세요$/)
  })

  it('이미 세션이 복원되면 바로 원래 경로로 보낸다', async () => {
    mockBackend(() => undefined)
    renderLogin()
    expect(await screen.findByText('캠페인 7 화면')).toBeInTheDocument()
  })
})
