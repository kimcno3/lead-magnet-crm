import { apiRequest } from './client'
import type { Channel, LeadContact, LeadPage } from './types'

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

/** 한 신청자의 원문 연락처. 화면에 잠깐 보여 주는 용도로만 쓰고 저장하지 않는다. */
export function getLeadContact(leadId: number): Promise<LeadContact> {
  return apiRequest(`/api/admin/leads/${leadId}/contact`)
}
