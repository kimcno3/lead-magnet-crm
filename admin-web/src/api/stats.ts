import { apiRequest } from './client'
import type { CampaignListStats, CampaignStats, ChannelStats, CreatorStats } from './types'

export interface Period {
  from?: string | null
  to?: string | null
}

export function campaignListStats(period: Period = {}): Promise<CampaignListStats> {
  return apiRequest('/api/admin/stats/campaigns', { query: { ...period } })
}

export function campaignStats(campaignId: number, period: Period): Promise<CampaignStats> {
  return apiRequest(`/api/admin/campaigns/${campaignId}/stats`, { query: { ...period } })
}

export function channelStats(campaignId: number, period: Period): Promise<ChannelStats> {
  return apiRequest(`/api/admin/campaigns/${campaignId}/stats/channels`, { query: { ...period } })
}

export function creatorStats(campaignId: number, period: Period): Promise<CreatorStats> {
  return apiRequest(`/api/admin/campaigns/${campaignId}/stats/creators`, { query: { ...period } })
}
