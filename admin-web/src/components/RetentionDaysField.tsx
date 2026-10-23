import { RETENTION_DAYS } from '../labels'
import { Field } from './Field'

/** 캠페인 생성·보존 기간 변경이 함께 쓰는 입력 (ADR 0020). 범위 검사는 서버가 하고 오류는 이 칸에 보인다. */
export function RetentionDaysField({ value, onChange, error }: { value: string; onChange: (value: string) => void; error?: string }) {
  return (
    <Field
      label="보존 기간(일)"
      error={error}
      hint={`${RETENTION_DAYS.min}~${RETENTION_DAYS.max}일. 신청 후 이 기간이 지나면 매일 03:00에 이름·연락처를 파기합니다(지표는 남습니다).`}
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
