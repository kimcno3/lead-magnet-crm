import { screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearSession } from '../api/client'
import type { TemplateDetail } from '../api/types'
import { json, mockBackend } from '../test/http'
import { renderPage } from '../test/render'
import { TemplateDetailPage } from './TemplateDetail'

const CONTENT =
  '<!doctype html><form><input name="phone" type="tel" required><script>parent.document.title="x"</script></form>'

const template: TemplateDetail = {
  id: 3,
  name: '세미나 신청서',
  size: 2048,
  sha256: 'abc',
  createdAt: '2026-10-20T01:00:00Z',
  content: CONTENT,
  inspection: [
    { code: 'external_stylesheet', severity: 'warning', message: '외부 CSS는 차단됩니다', location: '<link href="https://cdn.example.com/a.css">' },
    { code: 'fetch_in_script', severity: 'info', message: '네트워크 호출이 제한됩니다', location: 'fetch(' },
  ],
  fields: [
    { name: 'phone', type: 'tel', required: true, label: '연락처', options: null },
    { name: 'topic', type: 'radio', required: false, label: '관심 주제', options: ['교육', '세미나'] },
  ],
}

beforeEach(() => clearSession())
afterEach(() => vi.unstubAllGlobals())

async function renderDetail() {
  mockBackend((url) => (url.pathname === '/api/admin/templates/3' ? json(template) : undefined))
  renderPage('/templates/:id', <TemplateDetailPage />, '/templates/3')
  return screen.findByTitle('세미나 신청서 미리보기')
}

describe('템플릿 상세', () => {
  it('미리보기 iframe의 sandbox는 정확히 allow-scripts allow-forms이고 allow-same-origin이 없다', async () => {
    const iframe = await renderDetail()
    expect(iframe.tagName).toBe('IFRAME')
    expect(iframe.getAttribute('sandbox')).toBe('allow-scripts allow-forms')
    expect(iframe.getAttribute('sandbox')).not.toContain('allow-same-origin')
    expect(iframe.hasAttribute('src')).toBe(false)
  })

  it('업로드 HTML은 srcdoc으로만 들어가고 관리자 DOM에는 끼워 넣지 않는다', async () => {
    const iframe = await renderDetail()
    expect(iframe.getAttribute('srcdoc')).toBe(CONTENT)
    expect(document.querySelector('form input[name="phone"]')).toBeNull()
    expect(document.querySelector('body script')).toBeNull()
  })

  it('정적 미리보기라는 안내를 보여준다', async () => {
    await renderDetail()
    expect(screen.getByText(/관리자 화면에서는 스크립트 없이 정적으로 표시됩니다/)).toBeInTheDocument()
  })

  it('추출된 필드를 표로 보여준다', async () => {
    await renderDetail()
    const table = screen.getByRole('table', { name: '추출된 필드' })
    const rows = within(table).getAllByRole('row')
    expect(rows).toHaveLength(3)
    expect(within(rows[1]).getByText('phone')).toBeInTheDocument()
    expect(within(rows[1]).getByText('필수')).toBeInTheDocument()
    expect(within(rows[2]).getByText('교육, 세미나')).toBeInTheDocument()
  })

  it('검사 결과를 경고와 참고로 나누어 텍스트 라벨과 함께 보여준다', async () => {
    await renderDetail()
    const warnings = screen.getByRole('region', { name: '경고' })
    expect(within(warnings).getByText('외부 CSS는 차단됩니다')).toBeInTheDocument()
    expect(within(warnings).getByText(/cdn\.example\.com/)).toBeInTheDocument()
    expect(within(warnings).getByText('external_stylesheet')).toBeInTheDocument()
    const info = screen.getByRole('region', { name: '참고' })
    expect(within(info).getByText('네트워크 호출이 제한됩니다')).toBeInTheDocument()
    expect(within(warnings).queryByText('네트워크 호출이 제한됩니다')).toBeNull()
  })

  it('검사 결과가 비어 있으면 문제 없음이라고 알린다', async () => {
    mockBackend((url) => (url.pathname === '/api/admin/templates/3' ? json({ ...template, inspection: [] }) : undefined))
    renderPage('/templates/:id', <TemplateDetailPage />, '/templates/3')
    expect(await screen.findByText('검사에서 발견한 문제가 없습니다.')).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: '경고' })).toBeNull()
  })
})
