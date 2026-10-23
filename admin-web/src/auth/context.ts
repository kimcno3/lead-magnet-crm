import { createContext, useContext } from 'react'
import type { Me } from '../api/types'

export type AuthStatus = 'restoring' | 'authenticated' | 'anonymous'

export interface AuthContextValue {
  status: AuthStatus
  operator: Me | null
  signIn: (email: string, password: string) => Promise<void>
  signOut: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext)
  if (!value) throw new Error('AuthProvider 안에서만 쓸 수 있습니다')
  return value
}
