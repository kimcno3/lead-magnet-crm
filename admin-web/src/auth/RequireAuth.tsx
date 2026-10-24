import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { Button } from '../components/Button'
import { useI18n } from '../i18n/context'
import { useAuth } from './context'

export function RequireAuth({ children }: { children: ReactNode }) {
  const { status, retry } = useAuth()
  const location = useLocation()
  const { t } = useI18n()
  if (status === 'restoring') {
    return (
      <p className="boot" role="status">
        {t('auth.restoring')}
      </p>
    )
  }
  if (status === 'unreachable') {
    return (
      <div className="boot">
        <div className="error-box" role="alert">
          <p>{t('auth.unreachable')}</p>
          <Button size="sm" onClick={retry}>
            {t('common.retry')}
          </Button>
        </div>
      </div>
    )
  }
  if (status === 'anonymous') {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  }
  return <>{children}</>
}
