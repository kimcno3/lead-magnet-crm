import { useId } from 'react'
import type { InspectionSeverity, InspectionWarning } from '../api/types'

const GROUPS: { severity: InspectionSeverity; label: string; hint: string }[] = [
  { severity: 'warning', label: '경고', hint: '공개 폼에서 문제가 될 수 있습니다. 업로드는 할 수 있습니다.' },
  { severity: 'info', label: '참고', hint: '알아 두면 되는 내용입니다.' },
]

/** 검사 결과를 경고·참고로 나누어 보여준다. 흑백 체계라 색이나 아이콘 없이 텍스트 라벨로 구분한다. */
export function InspectionReport({ warnings }: { warnings: InspectionWarning[] }) {
  if (warnings.length === 0) {
    return <p className="note">검사에서 발견한 문제가 없습니다.</p>
  }
  return (
    <div className="inspection">
      {GROUPS.map((group) => {
        const items = warnings.filter((w) => w.severity === group.severity)
        return items.length > 0 ? <InspectionGroup key={group.severity} {...group} items={items} /> : null
      })}
    </div>
  )
}

function InspectionGroup({ label, hint, items }: { label: string; hint: string; items: InspectionWarning[] }) {
  const headingId = useId()
  return (
    <section aria-labelledby={headingId} className="inspection-group">
      <h3 id={headingId} className="inspection-title">
        {label}
      </h3>
      <p className="inspection-count">
        {items.length}건 · {hint}
      </p>
      <ul className="inspection-list">
        {items.map((w) => (
          <li key={`${w.code}|${w.location}`} className="inspection-item">
            <p className="inspection-message">{w.message}</p>
            <p className="inspection-meta">
              <span className="mono" translate="no">
                {w.code}
              </span>
              <span className="mono inspection-location" translate="no">
                {w.location}
              </span>
            </p>
          </li>
        ))}
      </ul>
    </section>
  )
}
