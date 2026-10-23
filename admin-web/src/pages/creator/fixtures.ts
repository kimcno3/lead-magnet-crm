import type { Creator, CreatorCampaignStats } from '../../api/types'

export const sky: Creator = {
  id: 3,
  name: '김하늘',
  handle: '@sky_kim',
  memo: 'R/S 20%, 매월 말 정산',
  status: 'active',
  createdAt: '2026-10-01T00:00:00Z',
  updatedAt: '2026-10-01T00:00:00Z',
}

export const sea: Creator = {
  id: 4,
  name: '박바다',
  handle: null,
  memo: null,
  status: 'archived',
  createdAt: '2026-09-01T00:00:00Z',
  updatedAt: '2026-10-02T00:00:00Z',
}

export const skyStats: CreatorCampaignStats = {
  creatorId: 3,
  name: '김하늘',
  status: 'active',
  from: null,
  to: null,
  campaigns: [
    { campaignId: 7, name: '가을 세미나', status: 'active', visits: 1200, visitors: 3, leads: 2, convertedVisitors: 2, conversionRate: 0.6667 },
    { campaignId: 5, name: '봄 특강', status: 'archived', visits: 0, visitors: 0, leads: 0, convertedVisitors: 0, conversionRate: 0 },
  ],
}
