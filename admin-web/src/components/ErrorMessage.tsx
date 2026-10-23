import { ApiError } from '../api/client'
import { errorText } from '../errors'
import { Button } from './Button'

export function ErrorMessage({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  if (!error) return null
  return (
    <div className="error-box" role="alert">
      <p>{errorText(error)}</p>
      {error instanceof ApiError && error.code && <p className="error-code">코드: {error.code}</p>}
      {onRetry && (
        <Button size="sm" onClick={onRetry}>
          다시 시도
        </Button>
      )}
    </div>
  )
}
