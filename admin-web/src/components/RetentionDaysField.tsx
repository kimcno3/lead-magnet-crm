import { useI18n } from '../i18n/context'
import { RETENTION_DAYS } from '../labels'
import { Field } from './Field'

/** 캠페인 생성·보존 기간 변경이 함께 쓰는 입력 (ADR 0020). 범위 검사는 서버가 하고 오류는 이 칸에 보인다. */
export function RetentionDaysField({ value, onChange, error }: { value: string; onChange: (value: string) => void; error?: string }) {
  const { t } = useI18n()
  return (
    <Field
      label={t('components.retentionDays')}
      error={error}
      hint={t('components.retentionHint', { min: String(RETENTION_DAYS.min), max: String(RETENTION_DAYS.max) })}
    >
      {(p) => (
        <input
          {...p}
          type="number"
          inputMode="numeric"
          min={RETENTION_DAYS.min}
          max={RETENTION_DAYS.max}
          step={1}
          value={value}
          onChange={(e) => onChange(e.target.value)}
          required
        />
      )}
    </Field>
  )
}
