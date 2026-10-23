import { apiRequest } from './client'
import type { DistributionLink, LinkChannel } from './types'

export function listLinks(formId: number): Promise<DistributionLink[]> {
  return apiRequest(`/api/admin/forms/${formId}/links`)
}

export function createLink(formId: number, channel: LinkChannel): Promise<DistributionLink> {
  return apiRequest(`/api/admin/forms/${formId}/links`, { method: 'POST', json: { channel } })
}
