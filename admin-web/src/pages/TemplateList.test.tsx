import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest'
import { clearSession } from '../api/client'
import { AI_FORM_PROMPT } from '../aiFormPrompt'
import { json, mockBackend, requestsTo } from '../test/http'
import { renderPage } from '../test/render'
import { TemplateListPage } from './TemplateList'

const summary = { id: 3, name: '세미나 신청서', size: 2048, fieldCount: 4, createdAt: '2026-10-20T01:00:00Z' }

beforeEach(() => clearSession())
afterEach(() => vi.unstubAllGlobals())

function uploadCalls(fetchMock: Mock) {
  return fetchMock.mock.calls.filter(
    (c) => String(c[0]) === '/api/admin/templates' && (c[1] as RequestInit | undefined)?.method === 'POST',
  )
}

function htmlFile(name = 'form.html') {
  return new File(['<form><input name="age"></form>'], name, { type: 'text/html' })
}

async function openUpload() {
  fireEvent.click(await screen.findByRole('button', { name: 'HTML 업로드' }))
  return screen.findByRole('dialog', { name: 'HTML 템플릿 업로드' })
}

describe('템플릿 목록', () => {
  it('목록을 표로 보여준다', async () => {
    mockBackend((url) => (url.pathname === '/api/admin/templates' ? json([summary]) : undefined))
    renderPage('/templates', <TemplateListPage />)
    const row = (await screen.findByRole('link', { name: '세미나 신청서' })).closest('tr')!
    expect(within(row).getByText('2.0 KB')).toBeInTheDocument()
    expect(within(row).getByText('4')).toBeInTheDocument()
  })

  it('비어 있으면 다음 행동을 알려준다', async () => {
    mockBackend((url) => (url.pathname === '/api/admin/templates' ? json([]) : undefined))
    renderPage('/templates', <TemplateListPage />)
    expect(await screen.findByText(/아직 등록한 템플릿이 없습니다/)).toBeInTheDocument()
  })

  it.each([
    ['template_contact_field_missing', 'phone 또는 email 이름의 입력이 있어야 합니다'],
    ['template_form_count_invalid', '문서에 <form>이 정확히 하나 있어야 합니다'],
    ['template_not_utf8', 'UTF-8로 저장한 파일이어야 합니다'],
  ])('422 %s면 코드별 안내 문구와 백엔드 detail을 보여준다', async (code, guide) => {
    const fetchMock = mockBackend((url, init) => {
      if (url.pathname !== '/api/admin/templates') return undefined
      if (init?.method === 'POST') return json({ status: 422, code, detail: `백엔드 설명 ${code}` }, 422)
      return json([summary])
    })
    renderPage('/templates', <TemplateListPage />)
    const dialog = await openUpload()

    fireEvent.change(within(dialog).getByLabelText(/HTML 파일/), { target: { files: [htmlFile()] } })
    fireEvent.click(within(dialog).getByRole('button', { name: '업로드' }))

    const alert = await within(dialog).findByRole('alert')
    expect(alert).toHaveTextContent(guide)
    expect(alert).toHaveTextContent(`백엔드 설명 ${code}`)
    const post = uploadCalls(fetchMock)[0]
    expect((post[1] as RequestInit).body).toBeInstanceOf(FormData)
    expect(((post[1] as RequestInit).body as FormData).get('file')).toBeInstanceOf(File)
  })

  it('드래그 앤 드롭으로 파일을 고를 수 있다', async () => {
    mockBackend((url) => (url.pathname === '/api/admin/templates' ? json([]) : undefined))
    renderPage('/templates', <TemplateListPage />)
    fireEvent.click(await screen.findByRole('button', { name: '템플릿 올리기' }))
    const dialog = await screen.findByRole('dialog', { name: 'HTML 템플릿 업로드' })

    fireEvent.drop(within(dialog).getByTestId('dropzone'), { dataTransfer: { files: [htmlFile('seminar.html')] } })

    expect(within(dialog).getByText(/seminar\.html/)).toBeInTheDocument()
  })

  it('파일 없이 업로드하면 입력 옆에 오류를 보여준다', async () => {
    const fetchMock = mockBackend((url) => (url.pathname === '/api/admin/templates' ? json([summary]) : undefined))
    renderPage('/templates', <TemplateListPage />)
    const dialog = await openUpload()
    fireEvent.click(within(dialog).getByRole('button', { name: '업로드' }))
    expect(await within(dialog).findByText('업로드할 HTML 파일을 골라 주세요')).toBeInTheDocument()
    expect(uploadCalls(fetchMock)).toHaveLength(0)
  })
  it('이름 필드 오류는 이름 입력 아래에 붙고 그 입력으로 포커스가 간다', async () => {
    mockBackend((url, init) => {
      if (url.pathname !== '/api/admin/templates') return undefined
      if (init?.method === 'POST') {
        return json(
          { status: 422, code: 'validation_failed', detail: '입력값이 올바르지 않습니다', errors: [{ field: 'name', message: 'name은 1~100자여야 합니다' }] },
          422,
        )
      }
      return json([summary])
    })
    renderPage('/templates', <TemplateListPage />)
    const dialog = await openUpload()
    fireEvent.change(within(dialog).getByLabelText(/HTML 파일/), { target: { files: [htmlFile()] } })
    fireEvent.click(within(dialog).getByRole('button', { name: '업로드' }))

    const nameInput = within(dialog).getByLabelText(/이름/)
    await waitFor(() => expect(nameInput).toHaveAttribute('aria-invalid', 'true'))
    expect(nameInput).toHaveAccessibleDescription(/name은 1~100자여야 합니다/)
    await waitFor(() => expect(nameInput).toHaveFocus())
  })

  it('413이 ProblemDetail이 아닌 HTML(nginx 기본 페이지)이어도 크기 안내를 보여준다', async () => {
    mockBackend((url, init) => {
      if (url.pathname !== '/api/admin/templates') return undefined
      if (init?.method === 'POST') {
        return new Response('<html><body><h1>413 Request Entity Too Large</h1></body></html>', {
          status: 413,
          headers: { 'Content-Type': 'text/html' },
        })
      }
      return json([summary])
    })
    renderPage('/templates', <TemplateListPage />)
    const dialog = await openUpload()
    fireEvent.change(within(dialog).getByLabelText(/HTML 파일/), { target: { files: [htmlFile()] } })
    fireEvent.click(within(dialog).getByRole('button', { name: '업로드' }))

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('512KB 이하 파일만 올릴 수 있습니다')
  })

  it('AI 프롬프트 복사 버튼은 프롬프트를 클립보드에 복사한다', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    vi.stubGlobal('navigator', { ...navigator, clipboard: { writeText } })
    mockBackend((url) => (url.pathname === '/api/admin/templates' ? json([summary]) : undefined))
    renderPage('/templates', <TemplateListPage />)

    fireEvent.click(await screen.findByRole('button', { name: 'AI 프롬프트 복사' }))

    await waitFor(() => expect(writeText).toHaveBeenCalledWith(AI_FORM_PROMPT))
    expect(await screen.findByText('복사했습니다')).toBeInTheDocument()
  })

  it('클립보드를 쓸 수 없으면 실패를 알린다', async () => {
    vi.stubGlobal('navigator', { ...navigator, clipboard: { writeText: vi.fn().mockRejectedValue(new Error('denied')) } })
    mockBackend((url) => (url.pathname === '/api/admin/templates' ? json([summary]) : undefined))
    renderPage('/templates', <TemplateListPage />)

    fireEvent.click(await screen.findByRole('button', { name: 'AI 프롬프트 복사' }))

    expect(await screen.findByText('복사하지 못했습니다. 아래 글을 선택해 직접 복사해 주세요')).toBeInTheDocument()
    expect(screen.getByLabelText(/AI 프롬프트/)).toHaveValue(AI_FORM_PROMPT)
  })

  it('검사만 하기는 /inspect만 부르고 저장하지 않으며 결과를 다이얼로그에 보여준다', async () => {
    const fetchMock = mockBackend((url, init) => {
      if (url.pathname === '/api/admin/templates/inspect' && init?.method === 'POST') {
        return json({
          name: 'form',
          size: 31,
          fields: [{ name: 'age', type: 'text', required: false, label: 'age', options: [] }],
          inspection: [
            { code: 'external_image', severity: 'warning', message: '외부 이미지는 표시되지 않습니다', location: '<img src="https://i.example/a.png">' },
          ],
        })
      }
      return url.pathname === '/api/admin/templates' ? json([summary]) : undefined
    })
    renderPage('/templates', <TemplateListPage />)
    const dialog = await openUpload()
    fireEvent.change(within(dialog).getByLabelText(/HTML 파일/), { target: { files: [htmlFile()] } })

    fireEvent.click(within(dialog).getByRole('button', { name: '검사만 하기' }))

    expect(await within(dialog).findByText('외부 이미지는 표시되지 않습니다')).toBeInTheDocument()
    expect(within(dialog).getByText(/저장하지 않았습니다/)).toBeInTheDocument()
    expect(uploadCalls(fetchMock)).toHaveLength(0)
    expect(requestsTo(fetchMock, '/api/admin/templates/inspect')).toHaveLength(1)
    expect(screen.getByRole('dialog', { name: 'HTML 템플릿 업로드' })).toBeInTheDocument()
  })

  it('검사만 하기에서 계약 오류가 나면 업로드와 같은 안내를 보여준다', async () => {
    mockBackend((url, init) => {
      if (url.pathname === '/api/admin/templates/inspect' && init?.method === 'POST') {
        return json({ status: 422, code: 'template_form_count_invalid', detail: 'form이 정확히 하나 있어야 합니다(현재 0개)' }, 422)
      }
      return url.pathname === '/api/admin/templates' ? json([summary]) : undefined
    })
    renderPage('/templates', <TemplateListPage />)
    const dialog = await openUpload()
    fireEvent.change(within(dialog).getByLabelText(/HTML 파일/), { target: { files: [htmlFile()] } })
    fireEvent.click(within(dialog).getByRole('button', { name: '검사만 하기' }))

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('문서에 <form>이 정확히 하나 있어야 합니다')
  })

  it('파일 없이 검사만 하기를 누르면 파일을 고르라고 알리고 요청하지 않는다', async () => {
    const fetchMock = mockBackend((url) => (url.pathname === '/api/admin/templates' ? json([summary]) : undefined))
    renderPage('/templates', <TemplateListPage />)
    const dialog = await openUpload()
    fireEvent.click(within(dialog).getByRole('button', { name: '검사만 하기' }))
    expect(await within(dialog).findByText('업로드할 HTML 파일을 골라 주세요')).toBeInTheDocument()
    expect(requestsTo(fetchMock, '/api/admin/templates/inspect')).toHaveLength(0)
  })

  it('검사 결과가 나오면 결과 제목으로 포커스가 가고 상태 영역으로 알린다', async () => {
    mockBackend((url, init) => {
      if (url.pathname === '/api/admin/templates/inspect' && init?.method === 'POST') {
        return json({ name: 'form', size: 31, fields: [], inspection: [{ code: 'external_image', severity: 'warning', message: '외 3건', location: '' }] })
      }
      return url.pathname === '/api/admin/templates' ? json([summary]) : undefined
    })
    renderPage('/templates', <TemplateListPage />)
    const dialog = await openUpload()
    fireEvent.change(within(dialog).getByLabelText(/HTML 파일/), { target: { files: [htmlFile()] } })
    fireEvent.click(within(dialog).getByRole('button', { name: '검사만 하기' }))

    const status = await within(dialog).findByRole('status')
    expect(within(status).getByText('외 3건')).toBeInTheDocument()
    await waitFor(() => expect(within(status).getByRole('heading', { name: '검사 결과' })).toHaveFocus())
  })

  it('검사만 하기가 413이면 크기 안내를 보여주고 검사 중 문구는 끝나면 돌아온다', async () => {
    mockBackend((url, init) => {
      if (url.pathname === '/api/admin/templates/inspect' && init?.method === 'POST') {
        return json({ status: 413, code: 'template_too_large', detail: '크기 초과' }, 413)
      }
      return url.pathname === '/api/admin/templates' ? json([summary]) : undefined
    })
    renderPage('/templates', <TemplateListPage />)
    const dialog = await openUpload()
    fireEvent.change(within(dialog).getByLabelText(/HTML 파일/), { target: { files: [htmlFile()] } })
    fireEvent.click(within(dialog).getByRole('button', { name: '검사만 하기' }))

    expect(await within(dialog).findByRole('alert')).toHaveTextContent('512KB 이하 파일만 올릴 수 있습니다')
    expect(within(dialog).getByRole('button', { name: '검사만 하기' })).toBeEnabled()
  })
})
