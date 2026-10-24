import { getLeadConsent } from '../../api/leads'
import { Dialog } from '../../components/Dialog'
import { ErrorMessage } from '../../components/ErrorMessage'
import { formatDateTime } from '../../format'
import { useAsync } from '../../hooks'
import { useI18n } from '../../i18n/context'

/**
 * 신청자가 동의한 문구 원문과 sha256, 동의 시각 (ADR 0020). 신청 시점 폼 문구의 복사본이라 폼이 바뀌어도 그대로다.
 * 개인정보가 아니어서 원문 연락처와 달리 감사 기록 없이 연다.
 */
export function ConsentDialog({ leadId, onClose }: { leadId: number; onClose: () => void }) {
  const { t } = useI18n()
  const { data, error, loading, reload } = useAsync(() => getLeadConsent(leadId), [leadId])
  return (
    <Dialog open title={t('consent.title')} onClose={onClose}>
      <ErrorMessage error={error} onRetry={reload} />
      <p className="muted" role="status" aria-live="polite">
        {loading && !data ? t('common.loading') : ''}
      </p>
      {data && (
        <dl className="detail-grid">
          <dt>{t('consent.consentAt')}</dt>
          <dd className="num">{formatDateTime(data.consentAt)}</dd>
          <dt>{t('consent.text')}</dt>
          <dd className="break pre-wrap">
            {data.consentText ?? <span className="muted">{t('consent.missing')}</span>}
          </dd>
          <dt>SHA-256</dt>
          <dd className="num break">{data.consentSha256 ?? <span className="muted">-</span>}</dd>
        </dl>
      )}
    </Dialog>
  )
}
