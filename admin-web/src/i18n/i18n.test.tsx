import { fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router'
import { AppRoutes } from '../App'
import { clearSession } from '../api/client'
import { AuthProvider } from '../auth/AuthProvider'
import { LoginPage } from '../pages/Login'
import { CampaignListPage } from '../pages/CampaignList'
import { json, mockBackend } from '../test/http'
import { renderPage } from '../test/render'
import { readLang } from '../preferences'
import { en } from './en'
import { ko } from './ko'
import { LanguageProvider } from './LanguageProvider'
import { summarizeAuditDetail } from '../format'
import { auditActionLabel, channelLabel, templateErrorGuide } from '../labels'
import { setActiveLang, translate } from './translate'

beforeEach(() => {
  clearSession()
  window.localStorage.clear()
  document.documentElement.lang = 'ko'
})
afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

function renderApp() {
  mockBackend((url) => (url.pathname === '/api/admin/templates' ? json([]) : undefined))
  return render(
    <LanguageProvider>
      <AuthProvider>
        <MemoryRouter initialEntries={['/templates']}>
          <AppRoutes />
        </MemoryRouter>
      </AuthProvider>
    </LanguageProvider>,
  )
}

/** 사전을 끝까지 내려가며 "a.b.c" 경로 목록을 만든다. */
function paths(node: unknown, prefix = ''): string[] {
  if (typeof node === 'string') return [prefix]
  return Object.entries(node as Record<string, unknown>).flatMap(([k, v]) => paths(v, prefix ? `${prefix}.${k}` : k))
}

describe('사전', () => {
  it('en과 ko의 키가 정확히 같고 빈 문구가 없다', () => {
    expect(paths(en).sort()).toEqual(paths(ko).sort())
    for (const key of paths(en)) expect(translate(key as never, undefined, 'en'), key).not.toBe('')
  })

  it('{name} 자리표시를 값으로 바꾸고, 영어는 count가 1이면 _one 문구를 쓴다', () => {
    expect(translate('common.daysValue', { count: 30 }, 'ko')).toBe('30일')
    expect(translate('common.daysValue', { count: 30 }, 'en')).toBe('30 days')
    expect(translate('common.daysValue', { count: 1 }, 'en')).toBe('1 day')
  })
})

describe('코드별 문구', () => {
  afterEach(() => setActiveLang('ko'))

  it('채널·감사 행동·오류 안내·감사 요약이 현재 언어를 따르고, 모르는 코드는 그대로 둔다', () => {
    expect(channelLabel('instagram')).toBe('인스타그램')
    setActiveLang('en')
    expect(channelLabel('instagram')).toBe('Instagram')
    expect(channelLabel('tiktok')).toBe('tiktok')
    expect(auditActionLabel('lead.export.reveal')).toBe('CSV download (unmasked)')
    expect(auditActionLabel('unknown.action')).toBe('unknown.action')
    expect(templateErrorGuide('template_empty')).toBe('The file is empty')
    expect(templateErrorGuide('nope')).toBeUndefined()
    expect(summarizeAuditDetail({ formId: 11, channel: 'youtube', page: 0, count: 1520 })).toBe('form #11 · YouTube · page 1 · 1,520 records')
    expect(summarizeAuditDetail({ count: 1 })).toBe('1 record')
  })
})

describe('언어 선택', () => {
  it('기본은 한국어이고, English를 고르면 메뉴와 테마 문구가 바뀐다', async () => {
    renderApp()
    const picker = await screen.findByLabelText('언어 / Language')
    expect(picker).toHaveValue('ko')
    expect(screen.getByRole('link', { name: '캠페인' })).toBeInTheDocument()

    fireEvent.change(picker, { target: { value: 'en' } })

    const nav = screen.getByRole('navigation', { name: 'Main menu' })
    expect(within(nav).getByRole('link', { name: 'Campaigns' })).toBeInTheDocument()
    expect(within(nav).getByRole('link', { name: 'Audit log' })).toBeInTheDocument()
    const theme = screen.getByLabelText('Theme')
    expect(within(theme).getByRole('option', { name: 'System' })).toBeInTheDocument()
    expect(within(theme).getByRole('option', { name: 'Dark' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sign out' })).toBeInTheDocument()
  })

  it('고른 언어를 lm.lang에 기억하고 <html lang>과 문서 제목을 바꾼다', async () => {
    const view = renderApp()
    fireEvent.change(await screen.findByLabelText('언어 / Language'), { target: { value: 'en' } })
    expect(window.localStorage.getItem('lm.lang')).toBe('en')
    expect(document.documentElement.lang).toBe('en')
    expect(document.title).toBe('Lead Magnet CRM Admin')

    view.unmount()
    renderApp()
    expect(await screen.findByLabelText('언어 / Language')).toHaveValue('en')
    expect(screen.getByRole('link', { name: 'Templates' })).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('언어 / Language'), { target: { value: 'ko' } })
    expect(document.documentElement.lang).toBe('ko')
    expect(document.title).toBe('리드마그넷 CRM 관리자')
  })

  it('저장값이 없거나 모르는 값이거나 저장소가 막혀 있으면 한국어다', () => {
    vi.stubGlobal('navigator', { ...navigator, language: 'en-US' })
    expect(readLang()).toBe('ko')
    window.localStorage.setItem('lm.lang', 'fr')
    expect(readLang()).toBe('ko')
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    expect(readLang()).toBe('ko')
  })

  it('저장소가 막혀 있어도 언어를 바꿀 수 있다', async () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    renderApp()
    fireEvent.change(await screen.findByLabelText('언어 / Language'), { target: { value: 'en' } })
    expect(screen.getByRole('link', { name: 'Creators' })).toBeInTheDocument()
  })
})

describe('영어 화면', () => {
  it('로그인 화면을 영어로 보여주고, 자체 언어 선택으로 바꿀 수 있다', async () => {
    window.localStorage.setItem('lm.lang', 'en')
    mockBackend((url) => (url.pathname === '/api/admin/auth/refresh' ? json({ code: 'invalid_refresh_token' }, 401) : undefined))
    render(
      <LanguageProvider>
        <AuthProvider>
          <MemoryRouter initialEntries={['/login']}>
            <Routes>
              <Route path="/login" element={<LoginPage />} />
            </Routes>
          </MemoryRouter>
        </AuthProvider>
      </LanguageProvider>,
    )
    expect(await screen.findByRole('heading', { name: 'Operator sign-in' })).toBeInTheDocument()
    expect(screen.getByLabelText('Email')).toBeInTheDocument()
    expect(screen.getByLabelText('Password')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sign in' })).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('언어 / Language'), { target: { value: 'ko' } })
    expect(screen.getByRole('heading', { name: '운영자 로그인' })).toBeInTheDocument()
  })

  it('캠페인 목록의 표 머리글과 상태를 영어로 보여준다', async () => {
    window.localStorage.setItem('lm.lang', 'en')
    mockBackend((url) => {
      if (url.pathname === '/api/admin/campaigns') {
        return json([{ id: 7, name: '가을 세미나', status: 'active', startsAt: null, endsAt: null, formCount: 0, createdAt: '2026-09-01T00:00:00Z' }])
      }
      if (url.pathname === '/api/admin/stats/campaigns') return json({ from: null, to: null, campaigns: [] })
      return undefined
    })
    renderPage('/campaigns', <CampaignListPage />, '/campaigns', 'en')
    const row = (await screen.findByRole('link', { name: '가을 세미나' })).closest('tr')!
    expect(within(row).getByText('Active')).toBeInTheDocument()
    const headers = screen.getAllByRole('columnheader').map((h) => h.textContent)
    expect(headers).toEqual(['Name', 'Status', 'Period', 'Visits', 'Visitors', 'Leads', 'Conversion'])
    expect(screen.getByRole('heading', { level: 1, name: 'Campaigns' })).toBeInTheDocument()
  })
})
