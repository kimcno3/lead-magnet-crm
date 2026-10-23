import { apiDownload, apiRequest, saveDownload } from './client'
import type { Channel, LeadConsent, LeadContact, LeadPage } from './types'

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

/** 한 신청의 동의 문구 증빙. 개인정보가 아니어서 감사 기록 없이 열린다 (ADR 0020). */
export function getLeadConsent(leadId: number): Promise<LeadConsent> {
  return apiRequest(`/api/admin/leads/${leadId}/consent`)
}

/**
 * 명단 CSV를 내려받는다(필터는 화면과 같다). reveal이면 원문이고 서버가 감사 로그에 남긴다 (ADR 0016).
 * 토큰이 메모리에만 있어 링크 대신 fetch로 받아 Blob으로 저장한다.
 */
export async function downloadLeadsCsv(
  campaignId: number,
  q: Omit<LeadQuery, 'page'> & { reveal: boolean },
): Promise<void> {
  const file = await apiDownload(`/api/admin/campaigns/${campaignId}/leads.csv`, {
    query: { formId: q.formId, channel: q.channel, reveal: q.reveal ? 'true' : null },
  })
  saveDownload(file, `leads-campaign-${campaignId}.csv`)
}
