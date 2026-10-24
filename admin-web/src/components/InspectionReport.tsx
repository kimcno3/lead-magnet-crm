import { useId } from 'react'
import type { InspectionSeverity, InspectionWarning } from '../api/types'
import { useI18n } from '../i18n/context'
import type { TKey } from '../i18n/translate'

const GROUPS: { severity: InspectionSeverity; label: TKey; hint: TKey }[] = [
  { severity: 'warning', label: 'components.inspectionWarning', hint: 'components.inspectionWarningHint' },
  { severity: 'info', label: 'components.inspectionInfo', hint: 'components.inspectionInfoHint' },
]

/** 검사 결과를 경고·참고로 나누어 보여준다. 흑백 체계라 색이나 아이콘 없이 텍스트 라벨로 구분한다. */
export function InspectionReport({ warnings }: { warnings: InspectionWarning[] }) {
  const { t } = useI18n()
  if (warnings.length === 0) {
    return <p className="note">{t('components.inspectionNone')}</p>
  }
  return (
    <div className="inspection">
      {GROUPS.map((group) => {
        const items = warnings.filter((w) => w.severity === group.severity)
        return items.length > 0 ? <InspectionGroup key={group.severity} label={t(group.label)} hint={t(group.hint)} items={items} /> : null
      })}
    </div>
  )
}

function InspectionGroup({ label, hint, items }: { label: string; hint: string; items: InspectionWarning[] }) {
  const headingId = useId()
  const { t } = useI18n()
  return (
    <section aria-labelledby={headingId} className="inspection-group">
      <h3 id={headingId} className="inspection-title">
        {label}
      </h3>
      <p className="inspection-count">
        {t('components.inspectionCount', { count: items.length, hint })}
      </p>
      <ul className="inspection-list">
        {items.map((w) => (
          <li key={`${w.code}|${w.location}`} className="inspection-item">
            <p className="inspection-message">{w.message}</p>
            <p className="inspection-meta">
              <span className="mono" translate="no">
                {w.code}
              </span>
              {w.location && (
                <span className="mono inspection-location" translate="no">
                  {w.location}
                </span>
              )}
            </p>
          </li>
        ))}
      </ul>
    </section>
  )
}
