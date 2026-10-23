import { apiRequest } from './client'
import type { Template, TemplateDetail, TemplateSummary } from './types'

export function listTemplates(): Promise<TemplateSummary[]> {
  return apiRequest('/api/admin/templates')
}

export function getTemplate(id: number): Promise<TemplateDetail> {
  return apiRequest(`/api/admin/templates/${id}`)
}

export function uploadTemplate(file: File, name: string): Promise<Template> {
  const formData = new FormData()
  formData.append('file', file)
  if (name.trim()) formData.append('name', name.trim())
  return apiRequest('/api/admin/templates', { method: 'POST', formData })
}
