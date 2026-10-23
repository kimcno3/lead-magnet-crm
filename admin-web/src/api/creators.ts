import { apiRequest } from './client'
import type { Period } from './stats'
import type { CreateCreatorRequest, Creator, CreatorCampaignStats, UpdateCreatorRequest } from './types'

export function listCreators(): Promise<Creator[]> {
  return apiRequest('/api/admin/creators')
}

export function getCreator(id: number): Promise<Creator> {
  return apiRequest(`/api/admin/creators/${id}`)
}

export function createCreator(body: CreateCreatorRequest): Promise<Creator> {
  return apiRequest('/api/admin/creators', { method: 'POST', json: body })
}

export function updateCreator(id: number, body: UpdateCreatorRequest): Promise<Creator> {
  return apiRequest(`/api/admin/creators/${id}`, { method: 'PATCH', json: body })
}

export function archiveCreator(id: number): Promise<Creator> {
  return updateCreator(id, { status: 'archived' })
}

export function creatorCampaignStats(id: number, period: Period): Promise<CreatorCampaignStats> {
  return apiRequest(`/api/admin/creators/${id}/stats`, { query: { ...period } })
}
