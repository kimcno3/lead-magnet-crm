import { fireEvent, screen, waitFor, within } from '@testing-library/react'
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
  version: 2,
  createdAt: '2026-10-20T01:00:00Z',
  updatedAt: '2026-10-22T01:00:00Z',
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

  it('버전 번호와 수정 시각을 보여준다', async () => {
    await renderDetail()
    expect(screen.getByText('v2')).toBeInTheDocument()
    expect(screen.getByText(/수정 /)).toBeInTheDocument()
  })

  it('새 버전 업로드는 PUT으로 보내고 검사 결과와 바뀐 초안 폼 수를 보여준 뒤 닫으면 다시 읽는다', async () => {
    let detailReads = 0
    const fetchMock = mockBackend((url, init) => {
      if (url.pathname !== '/api/admin/templates/3') return undefined
      if (init?.method === 'PUT') {
        return json({
          id: 3, name: '세미나 신청서', size: 100, sha256: 'new', version: 3, updatedAt: '2026-10-24T01:00:00Z', affectedDraftForms: 2,
          fields: template.fields,
          inspection: [{ code: 'external_image', severity: 'warning', message: '외부 이미지는 차단됩니다', location: '<img>' }],
        })
      }
      detailReads += 1
      return json(template)
    })
    renderPage('/templates/:id', <TemplateDetailPage />, '/templates/3')
    fireEvent.click(await screen.findByRole('button', { name: '새 버전 업로드' }))
    const dialog = await screen.findByRole('dialog', { name: '새 버전 업로드' })
    fireEvent.change(within(dialog).getByLabelText(/HTML 파일/), {
      target: { files: [new File(['<form><input name="phone"></form>'], 'v3.html', { type: 'text/html' })] },
    })
    fireEvent.click(within(dialog).getByRole('button', { name: '새 버전 올리기' }))

    expect(await within(dialog).findByText('v3으로 바꿨습니다')).toBeInTheDocument()
    expect(within(dialog).getByText('2개')).toBeInTheDocument()
    expect(within(dialog).getByText('외부 이미지는 차단됩니다')).toBeInTheDocument()
    const put = fetchMock.mock.calls.find((c) => (c[1] as RequestInit | undefined)?.method === 'PUT')!
    expect(String(put[0])).toBe('/api/admin/templates/3')
    expect((put[1] as RequestInit).body).toBeInstanceOf(FormData)

    fireEvent.click(within(dialog).getAllByRole('button', { name: '닫기' }).at(-1)!)
    await waitFor(() => expect(detailReads).toBe(2))
  })

  it('계약을 어긴 새 버전은 안내와 코드를 보여주고 다시 읽지 않는다', async () => {
    let detailReads = 0
    mockBackend((url, init) => {
      if (url.pathname !== '/api/admin/templates/3') return undefined
      if (init?.method === 'PUT') {
        return json({ status: 422, code: 'template_contact_field_missing', detail: 'phone 또는 email 필드가 필요합니다' }, 422)
      }
      detailReads += 1
      return json(template)
    })
    renderPage('/templates/:id', <TemplateDetailPage />, '/templates/3')
    fireEvent.click(await screen.findByRole('button', { name: '새 버전 업로드' }))
    const dialog = await screen.findByRole('dialog', { name: '새 버전 업로드' })
    fireEvent.click(within(dialog).getByRole('button', { name: '새 버전 올리기' }))
    expect(await within(dialog).findByText('올릴 HTML 파일을 골라 주세요')).toBeInTheDocument()
    fireEvent.change(within(dialog).getByLabelText(/HTML 파일/), { target: { files: [new File(['x'], 'bad.html', { type: 'text/html' })] } })
    fireEvent.click(within(dialog).getByRole('button', { name: '새 버전 올리기' }))

    const alert = await within(dialog).findByRole('alert')
    expect(alert).toHaveTextContent('phone 또는 email 이름의 입력이 있어야 합니다')
    expect(alert).toHaveTextContent('template_contact_field_missing')
    expect(detailReads).toBe(1)
  })

  it('올리는 중에 다이얼로그를 닫아도 요청이 끝나면 상세를 다시 읽는다', async () => {
    let detailReads = 0
    let finish: (r: Response) => void = () => {}
    mockBackend((url, init) => {
      if (url.pathname !== '/api/admin/templates/3') return undefined
      if (init?.method === 'PUT') return new Promise<Response>((resolve) => (finish = resolve)) as unknown as Response
      detailReads += 1
      return json(template)
    })
    renderPage('/templates/:id', <TemplateDetailPage />, '/templates/3')
    fireEvent.click(await screen.findByRole('button', { name: '새 버전 업로드' }))
    const dialog = await screen.findByRole('dialog', { name: '새 버전 업로드' })
    fireEvent.change(within(dialog).getByLabelText(/HTML 파일/), {
      target: { files: [new File(['<form><input name="phone"></form>'], 'v3.html', { type: 'text/html' })] },
    })
    fireEvent.click(within(dialog).getByRole('button', { name: '새 버전 올리기' }))
    fireEvent.click(within(dialog).getAllByRole('button', { name: '취소' }).at(-1)!)
    finish(json({ id: 3, name: 'x', size: 1, sha256: 'n', version: 3, updatedAt: '2026-10-24T01:00:00Z', affectedDraftForms: 0, fields: [], inspection: [] }))

    await waitFor(() => expect(detailReads).toBe(2))
  })
})
