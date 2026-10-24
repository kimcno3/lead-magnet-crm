import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { FieldsTable } from './FieldsTable'

describe('FieldsTable', () => {
  it('select의 빈 안내 선택지는 선택지 목록에 넣지 않는다', () => {
    render(
      <FieldsTable
        caption="필드"
        fields={[{ name: 'company_size', type: 'select', required: true, label: '회사 규모', options: ['', 'solo', 'small'] }]}
      />,
    )
    expect(screen.getByRole('cell', { name: 'solo, small' })).toBeInTheDocument()
    expect(screen.queryByText(/^, solo/)).not.toBeInTheDocument()
  })

  it('선택지가 모두 비어 있으면 -로 보인다', () => {
    render(<FieldsTable caption="필드" fields={[{ name: 'kind', type: 'select', required: false, label: '종류', options: [''] }]} />)
    expect(screen.getAllByText('-').length).toBeGreaterThan(0)
  })
})
