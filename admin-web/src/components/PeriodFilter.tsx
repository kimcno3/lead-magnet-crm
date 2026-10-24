import type { ReactNode } from 'react'
import { useSearchParams } from 'react-router'
import { useI18n } from '../i18n/context'
import { Button } from './Button'
import { Field } from './Field'

/**
 * 기간 필터. 값은 URL 쿼리 from·to(yyyy-MM-dd, 서울 날짜)에 둔다.
 * resetParams: 기간이 바뀌면 함께 지울 쿼리(예: 페이지가 있는 목록의 page).
 * emptyLabel: 기간을 비웠을 때의 뜻(기본 "전체 기간"). 서버가 기본 기간을 채우는 화면은 그 기간을 적는다.
 * children: 날짜 입력 옆에 둘 추가 컨트롤(예: 빠른 기간 버튼).
 */
export function PeriodFilter({
  resetParams = [],
  emptyLabel,
  children,
}: { resetParams?: readonly string[]; emptyLabel?: string; children?: ReactNode } = {}) {
  const { t } = useI18n()
  const [params, setParams] = useSearchParams()
  const from = params.get('from') ?? ''
  const to = params.get('to') ?? ''

  function setPeriod(values: { from?: string; to?: string }) {
    setParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        for (const [key, value] of Object.entries(values)) {
          if (value) next.set(key, value)
          else next.delete(key)
        }
        for (const key of resetParams) next.delete(key)
        return next
      },
      { replace: true },
    )
  }

  return (
    <div className="toolbar" role="group" aria-label={t('components.periodFilter')}>
      <Field label={t('common.startDate')}>
        {(p) => <input {...p} type="date" value={from} max={to || undefined} onChange={(e) => setPeriod({ from: e.target.value })} />}
      </Field>
      <Field label={t('common.endDate')}>
        {(p) => <input {...p} type="date" value={to} min={from || undefined} onChange={(e) => setPeriod({ to: e.target.value })} />}
      </Field>
      {(from || to) && (
        <Button size="sm" variant="ghost" onClick={() => setPeriod({ from: '', to: '' })}>
          {emptyLabel ?? t('components.allPeriod')}
        </Button>
      )}
      {children}
      <p className="toolbar-note muted small">{from || to ? t('components.periodNote') : (emptyLabel ?? t('components.allPeriod'))}</p>
    </div>
  )
}
