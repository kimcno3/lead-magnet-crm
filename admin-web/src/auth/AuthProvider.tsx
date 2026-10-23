import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { fetchMe, login, logout, restoreSession } from '../api/auth'
import { clearSession, onSessionExpired } from '../api/client'
import type { Me } from '../api/types'
import { AuthContext, type AuthContextValue, type AuthStatus } from './context'

/** 시작할 때 refresh 쿠키로 세션을 복원한다. 토큰은 api/client 모듈 메모리에만 있다. */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>('restoring')
  const [operator, setOperator] = useState<Me | null>(null)

  useEffect(() => {
    let active = true
    restoreSession()
      .then(async (ok) => {
        if (!ok) return null
        return fetchMe()
      })
      .then(
        (me) => {
          if (!active) return
          setOperator(me)
          setStatus(me ? 'authenticated' : 'anonymous')
        },
        () => active && setStatus('anonymous'),
      )
    const off = onSessionExpired(() => {
      setOperator(null)
      setStatus('anonymous')
    })
    return () => {
      active = false
      off()
    }
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
      setOperator(null)
      setStatus('anonymous')
    }
  }, [])

  const value = useMemo<AuthContextValue>(
    () => ({ status, operator, signIn, signOut }),
    [status, operator, signIn, signOut],
  )
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
