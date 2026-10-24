import { useState } from 'react'
import { Link } from 'react-router'
import type { DashboardForm, Metrics } from '../../api/types'
import { StatusBadge } from '../../components/Badge'
import { TableWrap } from '../../components/Table'
import { formatNumber, formatPercent } from '../../format'
import { useI18n } from '../../i18n/context'
import type { TKey } from '../../i18n/translate'

type SortKey = 'campaignName' | 'title' | 'status' | 'visits' | 'visitors' | 'leads' | 'convertedVisitors' | 'conversionRate'
type Direction = 'ascending' | 'descending'

const COLUMNS: readonly { key: SortKey; label: TKey; numeric: boolean }[] = [
  { key: 'campaignName', label: 'common.campaign', numeric: false },
  { key: 'title', label: 'common.form', numeric: false },
  { key: 'status', label: 'common.status', numeric: false },
  { key: 'visits', label: 'common.visits', numeric: true },
  { key: 'visitors', label: 'common.visitors', numeric: true },
  { key: 'leads', label: 'common.leads', numeric: true },
  { key: 'convertedVisitors', label: 'common.convertedVisitors', numeric: true },
  { key: 'conversionRate', label: 'common.conversionRate', numeric: true },
]

function compare(a: DashboardForm, b: DashboardForm, key: SortKey): number {
  const x = a[key]
  const y = b[key]
  return typeof x === 'number' && typeof y === 'number' ? x - y : String(x).localeCompare(String(y))
}

/**
 * 폼별 유입 시트. 머리글 버튼으로 정렬하고(숫자는 큰 값부터, 글자는 가나다순부터, 다시 누르면 반대) aria-sort로 알린다.
 * 같은 값은 서버 순서(최신 캠페인, 폼 id)를 지킨다. 합계 행은 서버의 전체 합계다(방문자·전환은 폼 행의 합이 아니다).
 */
export function FormSheet({ forms, totals }: { forms: DashboardForm[]; totals: Metrics }) {
  const { t } = useI18n()
  const [sort, setSort] = useState<{ key: SortKey; direction: Direction } | null>(null)

  const rows = sort
    ? [...forms].sort((a, b) => (sort.direction === 'ascending' ? 1 : -1) * compare(a, b, sort.key))
    : forms

  function toggle(key: SortKey, numeric: boolean) {
    setSort((prev) => {
      if (prev?.key === key) return { key, direction: prev.direction === 'ascending' ? 'descending' : 'ascending' }
      return { key, direction: numeric ? 'descending' : 'ascending' }
    })
  }

  return (
    <TableWrap label={t('dashboard.sheetTable')} className="sheet-wrap">
      <table className="table table-compact sheet">
        <caption className="visually-hidden">{t('dashboard.sheetTable')}</caption>
        <thead>
          <tr>
            {COLUMNS.map((c) => (
              <th
                key={c.key}
                scope="col"
                className={c.numeric ? 'num shrink' : undefined}
                aria-sort={sort?.key === c.key ? sort.direction : undefined}
              >
                <button type="button" className="sort-button" onClick={() => toggle(c.key, c.numeric)}>
                  {t(c.label)}
                  <span className="sort-mark" aria-hidden="true">
                    {sort?.key === c.key ? (sort.direction === 'ascending' ? '▲' : '▼') : ''}
                  </span>
                </button>
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((f) => (
            <tr key={f.formId}>
              <td className="text-cell">{f.campaignName}</td>
              <td className="text-cell">
                <Link className="row-link" to={`/campaigns/${f.campaignId}?tab=forms&open=${f.formId}`}>
                  {f.title}
                </Link>
              </td>
              <td className="shrink">
                <StatusBadge status={f.status} />
              </td>
              <td className="num">{formatNumber(f.visits)}</td>
              <td className="num">{formatNumber(f.visitors)}</td>
              <td className="num">{formatNumber(f.leads)}</td>
              <td className="num">{formatNumber(f.convertedVisitors)}</td>
              <td className="num">{formatPercent(f.conversionRate)}</td>
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr>
            <th scope="row">{t('dashboard.total')}</th>
            <td />
            <td />
            <td className="num">{formatNumber(totals.visits)}</td>
            <td className="num">{formatNumber(totals.visitors)}</td>
            <td className="num">{formatNumber(totals.leads)}</td>
            <td className="num">{formatNumber(totals.convertedVisitors)}</td>
            <td className="num">{formatPercent(totals.conversionRate)}</td>
          </tr>
        </tfoot>
      </table>
    </TableWrap>
  )
}
