import { apiRequest } from './client'
import type { Campaign, CampaignSummary, CreateCampaignRequest } from './types'

export function listCampaigns(): Promise<CampaignSummary[]> {
  return apiRequest('/api/admin/campaigns')
}

export function getCampaign(id: number): Promise<Campaign> {
  return apiRequest(`/api/admin/campaigns/${id}`)
}

export function createCampaign(body: CreateCampaignRequest): Promise<Campaign> {
  return apiRequest('/api/admin/campaigns', { method: 'POST', json: body })
}

export function archiveCampaign(id: number): Promise<Campaign> {
  return apiRequest(`/api/admin/campaigns/${id}`, { method: 'PATCH', json: { status: 'archived' } })
}
