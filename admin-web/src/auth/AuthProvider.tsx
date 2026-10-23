import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { fetchMe, login, logout, restoreSession } from '../api/auth'
import { clearSession, onSessionExpired } from '../api/client'
import type { Me } from '../api/types'
import { AuthContext, type AuthContextValue, type AuthStatus } from './context'

/** fetch 자체가 실패한 경우(서버에 닿지 못함). HTTP 오류 응답과 구분한다. */
function isNetworkError(error: unknown): boolean {
  return error instanceof TypeError
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
          if (isNetworkError(error)) {
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
