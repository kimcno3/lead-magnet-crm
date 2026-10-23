import { fireEvent, screen, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest'
import { clearSession } from '../api/client'
import { json, mockBackend } from '../test/http'
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
})
