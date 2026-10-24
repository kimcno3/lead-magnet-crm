import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { fetchMe, login, logout, restoreSession } from '../api/auth'
import { ApiError, clearSession, onSessionExpired } from '../api/client'
import type { Me } from '../api/types'
import { AuthContext, type AuthContextValue, type AuthStatus } from './context'

/**
 * 잠시 뒤 다시 시도하면 될 수 있는 실패: fetch 자체가 실패했거나(서버에 닿지 못함) refresh가 IP별 한도로 429다(ADR 0021).
 * 세션이 끝났다는 뜻이 아니므로 로그아웃으로 보지 않는다.
 */
function isTransientError(error: unknown): boolean {
  return error instanceof TypeError || (error instanceof ApiError && error.status === 429)
}

/** 시작할 때 refresh 쿠키로 세션을 복원한다. 토큰은 api/client 모듈 메모리에만 있다. */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>('restoring')
  const [operator, setOperator] = useState<Me | null>(null)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let active = true
    restoreSession()
      .then(async (ok) => (ok ? fetchMe() : null))
      .then(
        (me) => {
          if (!active) return
          setOperator(me)
          setStatus(me ? 'authenticated' : 'anonymous')
        },
        (error: unknown) => {
          if (!active) return
          if (isTransientError(error)) {
            setStatus('unreachable')
            return
          }
          // refresh는 됐지만 /me가 실패했다. 쓰지 못하는 토큰을 메모리에 남기지 않는다.
          clearSession()
          setOperator(null)
          setStatus('anonymous')
        },
      )
    return () => {
      active = false
    }
  }, [attempt])

  useEffect(
    () =>
      onSessionExpired(() => {
        setOperator(null)
        setStatus('anonymous')
      }),
    [],
  )

  const retry = useCallback(() => {
    setStatus('restoring')
    setAttempt((n) => n + 1)
  }, [])

  const signIn = useCallback(async (email: string, password: string) => {
    await login(email, password)
    try {
      setOperator(await fetchMe())
    } catch (e) {
      clearSession()
      throw e
    }
    setStatus('authenticated')
  }, [])

  const signOut = useCallback(async () => {
    try {
      await logout()
    } finally {
      clearSession()
      setOperator(null)
      setStatus('anonymous')
    }
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({ status, operator, signIn, signOut, retry }),
    [status, operator, signIn, signOut, retry],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
