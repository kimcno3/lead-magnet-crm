import type { Campaign, ChannelStats, CampaignStats, CreatorStats, LeadPage } from '../../api/types'

export const campaign: Campaign = {
  id: 7,
  name: '가을 세미나',
  description: null,
  status: 'active',
  startsAt: '2026-09-30T15:00:00Z',
  endsAt: '2026-10-31T14:59:59Z',
  createdAt: '2026-09-01T00:00:00Z',
  updatedAt: '2026-09-01T00:00:00Z',
  forms: [
    { id: 11, slug: 'fall-seminar', title: '세미나 신청', status: 'published', publishedAt: '2026-10-01T00:00:00Z', closedAt: null },
    { id: 12, slug: 'fall-ebook', title: '전자책 받기', status: 'draft', publishedAt: null, closedAt: null },
  ],
}

export const stats: CampaignStats = {
  campaignId: 7,
  from: null,
  to: null,
  visits: 1520,
  visitors: 3,
  leads: 2,
  convertedVisitors: 2,
  conversionRate: 0.6667,
}

const zero = { visits: 0, visitors: 0, leads: 0, convertedVisitors: 0, conversionRate: 0 }
export const channels: ChannelStats = {
  campaignId: 7,
  from: null,
  to: null,
  channels: [
    { channel: 'instagram', visits: 900, visitors: 2, leads: 2, convertedVisitors: 2, conversionRate: 1 },
    { channel: 'x', ...zero },
    { channel: 'youtube', visits: 20, visitors: 1, leads: 0, convertedVisitors: 0, conversionRate: 0 },
    { channel: 'threads', ...zero },
    { channel: 'direct', visits: 600, visitors: 0, leads: 0, convertedVisitors: 0, conversionRate: 0 },
  ],
}

/** 명단 API는 가린 값만 준다(ADR 0015). 원문은 leadContact로 따로 받는다. */
export function leadPage(page: number, totalElements = 45): LeadPage {
  return {
    page,
    size: 20,
    totalElements,
    totalPages: Math.ceil(totalElements / 20),
    items: [
      {
        id: 100 + page,
        formId: 11,
        formTitle: '세미나 신청',
        channel: 'instagram',
        name: `신*${page}`,
        phone: '010-****-5678',
        email: 'l***@example.com',
        payload: { company: '하늘상사' },
        createdAt: '2026-10-22T15:05:00Z',
      },
    ],
  }
}

const linkChannels = (instagram: typeof zero) => [
  { channel: 'instagram' as const, ...instagram },
  { channel: 'x' as const, ...zero },
  { channel: 'youtube' as const, ...zero },
  { channel: 'threads' as const, ...zero },
]

export const creatorStats: CreatorStats = {
  campaignId: 7,
  from: null,
  to: null,
  creators: [
    {
      creator: { id: 3, name: '김하늘', status: 'active' },
      visits: 800, visitors: 3, leads: 2, convertedVisitors: 2, conversionRate: 0.6667,
      channels: linkChannels({ visits: 800, visitors: 3, leads: 2, convertedVisitors: 2, conversionRate: 0.6667 }),
    },
    {
      creator: { id: 4, name: '박바다', status: 'archived' },
      visits: 100, visitors: 2, leads: 1, convertedVisitors: 1, conversionRate: 0.5,
      channels: linkChannels({ visits: 100, visitors: 2, leads: 1, convertedVisitors: 1, conversionRate: 0.5 }),
    },
    {
      creator: null,
      visits: 620, visitors: 1, leads: 0, convertedVisitors: 0, conversionRate: 0,
      channels: [...linkChannels(zero), { channel: 'direct', visits: 600, visitors: 1, leads: 0, convertedVisitors: 0, conversionRate: 0 }],
    },
  ],
}

export function leadContact(page: number) {
  return { name: `신청자${page}`, phone: '01012345678', email: 'lead@example.com' }
}
