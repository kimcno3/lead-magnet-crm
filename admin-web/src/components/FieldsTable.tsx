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
          {fields.map((f) => (
            <tr key={f.name}>
              <td className="mono">{f.name}</td>
              <td className="mono">{f.type}</td>
              <td>{f.required ? '필수' : '선택'}</td>
              <td>{f.label || <span className="muted">-</span>}</td>
              <td>{f.options?.length ? f.options.join(', ') : <span className="muted">-</span>}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </TableWrap>
  )
}
