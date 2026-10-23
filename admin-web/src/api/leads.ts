import { apiRequest } from './client'
import type { Channel, LeadPage } from './types'

export const LEAD_PAGE_SIZE = 20

export interface LeadQuery {
  page: number
  formId?: number | null
  channel?: Channel | null
}

export function listLeads(campaignId: number, q: LeadQuery): Promise<LeadPage> {
  return apiRequest(`/api/admin/campaigns/${campaignId}/leads`, {
    query: { page: q.page, size: LEAD_PAGE_SIZE, formId: q.formId, channel: q.channel },
  })
}
