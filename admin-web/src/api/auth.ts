import { apiRequest } from './client'
import type { Me } from './types'

export { login, logout, restoreSession } from './client'

export function fetchMe(): Promise<Me> {
  return apiRequest<Me>('/api/admin/auth/me')
}
