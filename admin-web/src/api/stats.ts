import { apiDownload, apiRequest, saveDownload } from './client'
import type { CampaignListStats, CampaignStats, ChannelStats, CreatorStats, Dashboard } from './types'

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

/** 유입 현황 대시보드. 기간을 비우면 서버가 서울 오늘까지 30일로 채운다. */
export function dashboardStats(period: Period = {}): Promise<Dashboard> {
  return apiRequest('/api/admin/stats/dashboard', { query: { ...period } })
}

/** 대시보드 폼 시트 CSV(개인정보 없음). */
export async function downloadDashboardCsv(period: Period = {}): Promise<void> {
  const file = await apiDownload('/api/admin/stats/dashboard.csv', { query: { ...period } })
  saveDownload(file, 'dashboard-forms.csv')
}
