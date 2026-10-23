import { apiRequest } from './client'
import type { DistributionLink, LinkChannel } from './types'

export function listLinks(formId: number): Promise<DistributionLink[]> {
  return apiRequest(`/api/admin/forms/${formId}/links`)
}

/** creatorId가 없으면 크리에이터 없는(운영자 자체) 링크를 만든다. */
export function createLink(formId: number, channel: LinkChannel, creatorId?: number): Promise<DistributionLink> {
  const json = creatorId === undefined ? { channel } : { channel, creatorId }
  return apiRequest(`/api/admin/forms/${formId}/links`, { method: 'POST', json })
}
