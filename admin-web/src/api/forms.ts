import { apiRequest } from './client'
import type { CreateFormRequest, Form, UpdateFormRequest } from './types'

export function createForm(campaignId: number, body: CreateFormRequest): Promise<Form> {
  return apiRequest(`/api/admin/campaigns/${campaignId}/forms`, { method: 'POST', json: body })
}

export function getForm(id: number): Promise<Form> {
  return apiRequest(`/api/admin/forms/${id}`)
}

export function updateForm(id: number, body: UpdateFormRequest): Promise<Form> {
  return apiRequest(`/api/admin/forms/${id}`, { method: 'PATCH', json: body })
}
