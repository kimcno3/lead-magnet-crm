import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { Button } from '../components/Button'
import { useAuth } from './context'

export function RequireAuth({ children }: { children: ReactNode }) {
  const { status, retry } = useAuth()
  const location = useLocation()
  if (status === 'restoring') {
    return (
      <p className="boot" role="status">
        세션을 확인하는 중…
      </p>
    )
  }
  if (status === 'unreachable') {
    return (
      <div className="boot">
        <div className="error-box" role="alert">
          <p>서버에 연결하지 못했습니다. 네트워크나 서버 상태를 확인한 뒤 다시 시도해 주세요.</p>
          <Button size="sm" onClick={retry}>
            다시 시도
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
