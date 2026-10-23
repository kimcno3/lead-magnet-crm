import { useSearchParams } from 'react-router'
import { Button } from './Button'
import { Field } from './Field'

/** 지표 기간 필터. 값은 URL 쿼리 from·to(yyyy-MM-dd, 서울 날짜)에 둔다. */
export function PeriodFilter() {
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
        return next
      },
      { replace: true },
    )
  }

  return (
    <div className="toolbar" role="group" aria-label="기간 필터">
      <Field label="시작일">
        {(p) => <input {...p} type="date" value={from} max={to || undefined} onChange={(e) => setPeriod({ from: e.target.value })} />}
      </Field>
      <Field label="종료일">
        {(p) => <input {...p} type="date" value={to} min={from || undefined} onChange={(e) => setPeriod({ to: e.target.value })} />}
      </Field>
      {(from || to) && (
        <Button size="sm" variant="ghost" onClick={() => setPeriod({ from: '', to: '' })}>
          전체 기간
        </Button>
      )}
      <p className="toolbar-note muted small">{from || to ? '날짜는 서울 시각 기준이며 양 끝 날짜를 포함합니다' : '전체 기간'}</p>
    </div>
  )
}
