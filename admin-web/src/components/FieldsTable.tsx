import type { FormField } from '../api/types'
import { formatOptions } from '../format'
import { useI18n } from '../i18n/context'
import { TableWrap } from './Table'

export function FieldsTable({ fields, caption }: { fields: FormField[]; caption: string }) {
  const { t } = useI18n()
  return (
    <TableWrap label={caption}>
      <table className="table">
        <caption className="visually-hidden">{caption}</caption>
        <thead>
          <tr>
            <th scope="col" className="shrink">{t('common.name')}</th>
            <th scope="col" className="shrink">{t('components.fieldType')}</th>
            <th scope="col">{t('components.fieldLabel')}</th>
            <th scope="col" className="shrink">{t('common.required')}</th>
            <th scope="col">{t('components.fieldOptions')}</th>
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
                <td className="shrink">{f.required ? t('common.required') : t('common.notRequired')}</td>
                <td className="options">{options || <span className="muted">-</span>}</td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </TableWrap>
  )
}
