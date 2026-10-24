import type { FormField } from '../api/types'
import { formatOptions } from '../format'
import { TableWrap } from './Table'

export function FieldsTable({ fields, caption }: { fields: FormField[]; caption: string }) {
  return (
    <TableWrap label={caption}>
      <table className="table">
        <caption className="visually-hidden">{caption}</caption>
        <thead>
          <tr>
            <th scope="col" className="shrink">이름</th>
            <th scope="col" className="shrink">유형</th>
            <th scope="col">라벨</th>
            <th scope="col" className="shrink">필수</th>
            <th scope="col">선택지</th>
          </tr>
        </thead>
        <tbody>
          {fields.map((f) => {
            const options = formatOptions(f.options)
            return (
              <tr key={f.name}>
                <td className="mono shrink" translate="no">{f.name}</td>
                <td className="mono shrink">{f.type}</td>
                <td className="text-cell">{f.label || <span className="muted">-</span>}</td>
                <td className="shrink">{f.required ? '필수' : '선택'}</td>
                <td className="options">{options || <span className="muted">-</span>}</td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </TableWrap>
  )
}
