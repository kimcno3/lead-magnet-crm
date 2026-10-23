import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearSession } from '../api/client'
import { json, mockBackend } from '../test/http'
import { renderPage } from '../test/render'
import { CampaignDetailPage } from './CampaignDetail'
import { campaign } from './campaign/fixtures'

beforeEach(() => clearSession())
afterEach(() => vi.unstubAllGlobals())

function backend(patch: (body: Record<string, unknown>) => Response) {
  let current = campaign
  const patches: Record<string, unknown>[] = []
  mockBackend((url, init) => {
    if (url.pathname === '/api/admin/campaigns/7' && init?.method === 'PATCH') {
      const body = JSON.parse(String(init.body)) as Record<string, unknown>
      patches.push(body)
      const response = patch(body)
      if (response.ok) current = { ...current, retentionDays: Number(body.retentionDays) }
      return response
    }
    if (url.pathname === '/api/admin/campaigns/7') return json(current)
    if (url.pathname.startsWith('/api/admin/campaigns/7/stats')) return json({})
    return undefined
  })
  return patches
}

describe('캠페인 보존 기간', () => {
  it('보존 기간을 머리글에 보여주고 변경 대화상자로 바꾼다', async () => {
    const patches = backend((body) => json({ ...campaign, retentionDays: body.retentionDays }))
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7?tab=forms')
    expect(await screen.findByText('보존 365일')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: '보존 기간 변경' }))
    const dialog = screen.getByRole('dialog', { name: '보존 기간 변경' })
    expect(within(dialog).getByLabelText('보존 기간(일)')).toHaveValue(365)
    fireEvent.change(within(dialog).getByLabelText('보존 기간(일)'), { target: { value: '90' } })
    fireEvent.click(within(dialog).getByRole('button', { name: '저장' }))

    // 줄이면 확인을 한 번 더 받는다. 확인 전에는 보내지 않는다.
    const confirm = await screen.findByRole('dialog', { name: '보존 기간 단축' })
    expect(confirm).toHaveTextContent('365일 → 90일')
    expect(patches).toEqual([])
    fireEvent.click(within(confirm).getByRole('button', { name: '줄이기' }))

    await waitFor(() => expect(screen.queryByRole('dialog', { name: '보존 기간 변경' })).not.toBeInTheDocument())
    expect(patches).toEqual([{ retentionDays: 90 }])
    expect(await screen.findByText('보존 90일')).toBeInTheDocument()
  })

  it('늘리면 확인 없이 바로 저장하고, 단축 확인에서 취소하면 보내지 않는다', async () => {
    const patches = backend((body) => json({ ...campaign, retentionDays: body.retentionDays }))
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7?tab=forms')
    fireEvent.click(await screen.findByRole('button', { name: '보존 기간 변경' }))
    let dialog = screen.getByRole('dialog', { name: '보존 기간 변경' })
    fireEvent.change(within(dialog).getByLabelText('보존 기간(일)'), { target: { value: '30' } })
    fireEvent.click(within(dialog).getByRole('button', { name: '저장' }))
    const confirm = await screen.findByRole('dialog', { name: '보존 기간 단축' })
    fireEvent.click(within(confirm).getByRole('button', { name: '취소' }))
    await waitFor(() => expect(screen.queryByRole('dialog', { name: '보존 기간 단축' })).not.toBeInTheDocument())
    expect(patches).toEqual([])

    dialog = screen.getByRole('dialog', { name: '보존 기간 변경' })
    fireEvent.change(within(dialog).getByLabelText('보존 기간(일)'), { target: { value: '400' } })
    fireEvent.click(within(dialog).getByRole('button', { name: '저장' }))
    await waitFor(() => expect(patches).toEqual([{ retentionDays: 400 }]))
    expect(screen.queryByRole('dialog', { name: '보존 기간 단축' })).not.toBeInTheDocument()
  })

  it('범위를 벗어나면 서버 오류를 그 칸에 보여주고 대화상자를 닫지 않는다', async () => {
    backend(() =>
      json({ code: 'validation_failed', errors: [{ field: 'retentionDays', message: 'retentionDays는 1~3650일이어야 합니다' }] }, 422),
    )
    renderPage('/campaigns/:id', <CampaignDetailPage />, '/campaigns/7?tab=forms')
    fireEvent.click(await screen.findByRole('button', { name: '보존 기간 변경' }))
    const dialog = screen.getByRole('dialog', { name: '보존 기간 변경' })
    fireEvent.change(within(dialog).getByLabelText('보존 기간(일)'), { target: { value: '0' } })
    fireEvent.click(within(dialog).getByRole('button', { name: '저장' }))

    expect(await within(dialog).findByText('retentionDays는 1~3650일이어야 합니다')).toBeInTheDocument()
    expect(screen.getByText('보존 365일')).toBeInTheDocument()
  })
})
