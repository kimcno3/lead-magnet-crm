import { apiRequest } from './client'
import type { AuditAction, AuditLogPage } from './types'

export const AUDIT_PAGE_SIZE = 20

export interface AuditQuery {
  page: number
  action?: AuditAction | null
  campaignId?: number | null
  /** yyyy-MM-dd, 서울 날짜. 양 끝 포함. */
  from?: string | null
  to?: string | null
}

export function listAuditLogs(q: AuditQuery): Promise<AuditLogPage> {
  return apiRequest('/api/admin/audit-logs', {
    query: { page: q.page, size: AUDIT_PAGE_SIZE, action: q.action, campaignId: q.campaignId, from: q.from, to: q.to },
  })
}
