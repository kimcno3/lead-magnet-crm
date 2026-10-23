import { apiRequest } from './client'
import type { Template, TemplateDetail, TemplateInspection, TemplateSummary, TemplateVersionResult } from './types'

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

/** 저장하지 않고 검사 리포트만 받는다. */
export function inspectTemplate(file: File, name: string): Promise<TemplateInspection> {
  const formData = new FormData()
  formData.append('file', file)
  if (name.trim()) formData.append('name', name.trim())
  return apiRequest('/api/admin/templates/inspect', { method: 'POST', formData })
}

/** 같은 템플릿에 새 버전을 올린다. draft 폼은 새 필드로 다시 만들어지고 게시된 폼은 영향이 없다. */
export function uploadTemplateVersion(id: number, file: File): Promise<TemplateVersionResult> {
  const formData = new FormData()
  formData.append('file', file)
  return apiRequest(`/api/admin/templates/${id}`, { method: 'PUT', formData })
}
