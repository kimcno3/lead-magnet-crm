import { createContext, useContext } from 'react'
import type { Me } from '../api/types'

/** unreachable: refresh 요청이 서버에 닿지 못했거나 요청이 많아 잠시 거절됐다(429). 세션이 끝났는지 알 수 없으므로 로그아웃으로 보지 않는다. */
export type AuthStatus = 'restoring' | 'authenticated' | 'anonymous' | 'unreachable'

export interface AuthContextValue {
  status: AuthStatus
  operator: Me | null
  signIn: (email: string, password: string) => Promise<void>
  signOut: () => Promise<void>
  /** 세션 복원을 다시 시도한다(unreachable일 때). */
  retry: () => void
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext)
  if (!value) throw new Error('AuthProvider 안에서만 쓸 수 있습니다')
  return value
}
