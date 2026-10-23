// 백엔드 DTO(record)와 같은 모양. Instant는 ISO 문자열로 온다.

export type CampaignStatus = 'active' | 'archived'
export type CreatorStatus = 'active' | 'archived'
export type FormStatus = 'draft' | 'published' | 'closed'
export type LinkChannel = 'instagram' | 'x' | 'youtube' | 'threads'
export type Channel = LinkChannel | 'direct'

export const LINK_CHANNELS: readonly LinkChannel[] = ['instagram', 'x', 'youtube', 'threads']
export const CHANNELS: readonly Channel[] = [...LINK_CHANNELS, 'direct']

export interface TokenResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
}

export interface Me {
  id: number
  email: string
}

export interface CampaignSummary {
  id: number
  name: string
  status: CampaignStatus
  startsAt: string | null
  endsAt: string | null
  formCount: number
  createdAt: string
}

export interface FormSummary {
  id: number
  slug: string
  title: string
  status: FormStatus
  publishedAt: string | null
  closedAt: string | null
}

export interface Campaign {
  id: number
  name: string
  description: string | null
  status: CampaignStatus
  startsAt: string | null
  endsAt: string | null
  /** 신청 보존 기간(일, 1~3650). 지나면 매일 03:00(KST) 익명화한다 (ADR 0020). */
  retentionDays: number
  createdAt: string
  updatedAt: string
  forms: FormSummary[]
}

export interface CreateCampaignRequest {
  name: string
  description: string | null
  startsAt: string | null
  endsAt: string | null
  /** 없으면 365 */
  retentionDays?: number
}

/** 없는 필드는 바꾸지 않는다. */
export interface UpdateCampaignRequest {
  retentionDays?: number
}

export interface FormField {
  name: string
  type: string
  required: boolean
  label: string | null
  options: string[] | null
}

export interface Form {
  id: number
  campaignId: number
  templateId: number
  slug: string
  title: string
  consentText: string
  fieldConfig: FormField[]
  status: FormStatus
  publishedAt: string | null
  closedAt: string | null
  createdAt: string
  /** 게시 시점에 복사한 템플릿의 sha256. draft는 null (ADR 0018). */
  publishedTemplateSha256: string | null
  /** 템플릿의 현재 sha256. publishedTemplateSha256과 다르면 게시한 뒤 새 버전이 올라온 것이다. */
  templateSha256: string
}

export interface CreateFormRequest {
  templateId: number | null
  title: string
  consentText: string
  slug: string | null
}

export interface FieldChange {
  name: string
  label?: string
  required?: boolean
}

export interface UpdateFormRequest {
  title?: string
  consentText?: string
  fieldConfig?: FieldChange[]
  status?: 'published' | 'closed'
}

export interface CreatorRef {
  id: number
  name: string
}

export interface DistributionLink {
  id: number
  channel: LinkChannel
  /** 크리에이터 없는(운영자 자체) 링크면 null */
  creator: CreatorRef | null
  code: string
  url: string
  utmSource: string
  utmMedium: string
  utmCampaign: string
  createdAt: string
}

export interface TemplateSummary {
  id: number
  name: string
  size: number
  fieldCount: number
  version: number
  createdAt: string
  updatedAt: string
}

export type InspectionSeverity = 'warning' | 'info'

/** 업로드 HTML 검사 경고 하나(백엔드 InspectionWarning). 업로드를 막지 않는다. */
export interface InspectionWarning {
  code: string
  severity: InspectionSeverity
  message: string
  location: string
}

export interface Template {
  id: number
  name: string
  size: number
  sha256: string
  fields: FormField[]
  inspection: InspectionWarning[]
  version: number
  createdAt: string
  updatedAt: string
}

/** PUT /api/admin/templates/{id} 응답. 새 필드로 다시 스냅샷한 draft 폼 수를 함께 준다. */
export interface TemplateVersionResult {
  id: number
  name: string
  size: number
  sha256: string
  fields: FormField[]
  inspection: InspectionWarning[]
  version: number
  updatedAt: string
  affectedDraftForms: number
}

/** POST /api/admin/templates/inspect 응답. 저장하지 않은 리포트다. */
export interface TemplateInspection {
  name: string
  size: number
  fields: FormField[]
  inspection: InspectionWarning[]
}

export interface TemplateDetail extends Template {
  content: string
}

export interface Lead {
  id: number
  formId: number
  formTitle: string
  channel: Channel
  name: string | null
  phone: string | null
  email: string | null
  /** 연락처를 복호화하지 못한 줄. name·phone·email이 모두 null이다 (ADR 0015). */
  contactUnavailable: boolean
  payload: Record<string, unknown> | null
  createdAt: string
  /** 보존 기간이 지나 파기된 시각. 있으면 name·phone·email이 null이고 payload는 키만 남는다 (ADR 0020). */
  anonymizedAt: string | null
}

/** 동의 증빙(GET /api/admin/leads/{id}/consent). 문구·sha256은 V10 이전 신청에서 백필하지 못했으면 null (ADR 0020). */
export interface LeadConsent {
  consentText: string | null
  consentSha256: string | null
  consentAt: string
  anonymizedAt: string | null
}

/** 원문 연락처(GET /api/admin/leads/{id}/contact). 명단의 name·phone·email은 가린 값이다 (ADR 0015). */
export interface LeadContact {
  name: string | null
  phone: string | null
  email: string | null
  /** phone·email 이외 이름의 전화·이메일 입력 원문(필드 이름 → 값). 명단 payload에서는 가려져 있다 (ADR 0015). */
  extraContacts?: Record<string, string>
}

export interface LeadPage {
  items: Lead[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface Metrics {
  visits: number
  visitors: number
  leads: number
  convertedVisitors: number
  conversionRate: number
}

export interface CampaignListStats {
  from: string | null
  to: string | null
  campaigns: (Metrics & { campaignId: number; name: string; status: CampaignStatus })[]
}

export interface CampaignStats extends Metrics {
  campaignId: number
  from: string | null
  to: string | null
}

export interface ChannelStats {
  campaignId: number
  from: string | null
  to: string | null
  channels: (Metrics & { channel: Channel })[]
}

export interface Creator {
  id: number
  name: string
  handle: string | null
  memo: string | null
  status: CreatorStatus
  createdAt: string
  updatedAt: string
}

export interface CreateCreatorRequest {
  name: string
  handle: string | null
  memo: string | null
}

/** 없는 필드는 바꾸지 않는다. handle·memo에 빈 문자열을 주면 지운다. */
export interface UpdateCreatorRequest {
  name?: string
  handle?: string
  memo?: string
  status?: 'archived'
}

export interface CreatorStats {
  campaignId: number
  from: string | null
  to: string | null
  /** creator가 null인 행은 "직접·자체 링크"(크리에이터 없는 링크 + direct) */
  creators: (Metrics & {
    creator: (CreatorRef & { status: CreatorStatus }) | null
    channels: (Metrics & { channel: Channel })[]
  })[]
}

export interface CreatorCampaignStats {
  creatorId: number
  name: string
  status: CreatorStatus
  from: string | null
  to: string | null
  campaigns: (Metrics & { campaignId: number; name: string; status: CampaignStatus })[]
}

export interface FieldError {
  field: string
  message: string
  code?: string
}

export interface ProblemDetail {
  status?: number
  title?: string
  detail?: string
  code?: string
  errors?: FieldError[]
}

/** 감사 로그 action (ADR 0016). */
export const AUDIT_ACTIONS = [
  'lead.list',
  'lead.contact.view',
  'lead.export',
  'lead.export.reveal',
  'auth.login.success',
  'auth.login.failure',
  'auth.login.locked',
  'retention.anonymize',
  'campaign.retention.update',
  'auth.refresh.reused',
  'auth.logout',
  'form.publish',
  'form.close',
  'template.version.upload',
  'creator.archive',
] as const
export type AuditAction = (typeof AUDIT_ACTIONS)[number]

/** 감사 기록 한 줄. detail에는 필터·페이지·건수·가린 이메일만 있다. */
export interface AuditLog {
  id: number
  operatorId: number | null
  operatorEmail: string | null
  action: AuditAction
  targetType: string
  targetId: number | null
  campaignId: number | null
  detail: Record<string, unknown>
  ip: string | null
  userAgent: string | null
  createdAt: string
}

export interface AuditLogPage {
  items: AuditLog[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}
