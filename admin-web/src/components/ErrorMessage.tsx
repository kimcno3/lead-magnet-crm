import { ApiError } from '../api/client'
import { errorText } from '../errors'
import { useI18n } from '../i18n/context'
import { Button } from './Button'

export function ErrorMessage({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const { t } = useI18n()
  if (!error) return null
  return (
    <div className="error-box" role="alert">
      <p>{errorText(error)}</p>
      {error instanceof ApiError && error.code && <p className="error-code">{t('common.errorCode', { code: error.code })}</p>}
      {onRetry && (
        <Button size="sm" onClick={onRetry}>
          {t('common.retry')}
        </Button>
      )}
    </div>
  )
}
