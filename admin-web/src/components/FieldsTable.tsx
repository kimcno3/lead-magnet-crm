import type { FormField } from '../api/types'
import { TableWrap } from './Table'

export function FieldsTable({ fields, caption }: { fields: FormField[]; caption: string }) {
  return (
    <TableWrap label={caption}>
      <table className="table">
        <caption className="visually-hidden">{caption}</caption>
        <thead>
          <tr>
            <th scope="col">이름</th>
            <th scope="col">유형</th>
            <th scope="col">필수</th>
            <th scope="col">라벨</th>
            <th scope="col">선택지</th>
          </tr>
        </thead>
        <tbody>
          {fields.map((f) => {
            // 값이 빈 선택지는 select의 안내 문구("선택해 주세요")라 고를 수 있는 값이 아니다
            const options = (f.options ?? []).filter((o) => o !== '')
            return (
              <tr key={f.name}>
                <td className="mono" translate="no">{f.name}</td>
                <td className="mono">{f.type}</td>
                <td className="nowrap">{f.required ? '필수' : '선택'}</td>
                <td className="text-cell">{f.label || <span className="muted">-</span>}</td>
                <td className="options">{options.length ? options.join(', ') : <span className="muted">-</span>}</td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </TableWrap>
  )
}
