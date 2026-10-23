import { useRef, useState } from 'react'
import { useSearchParams } from 'react-router'
import { getLeadContact, LEAD_PAGE_SIZE, listLeads } from '../../api/leads'
import { CHANNELS, type Campaign, type Channel, type Lead, type LeadContact } from '../../api/types'
import { Button } from '../../components/Button'
import { EmptyState } from '../../components/EmptyState'
import { ErrorMessage } from '../../components/ErrorMessage'
import { Field } from '../../components/Field'
import { SkeletonRows, TableWrap } from '../../components/Table'
import { formatDateTime, formatNumber, formatPhone, summarizePayload } from '../../format'
import { LeadExportMenu } from './LeadExportMenu'
import { errorText } from '../../errors'
import { useAsync } from '../../hooks'
import { ANONYMIZED_LABEL, CHANNEL_LABELS } from '../../labels'
import { ConsentDialog } from './ConsentDialog'

/** 원문 보기 상태. 그 줄에만, 같은 명단 조회(캠페인·페이지·필터) 동안에만 유지한다. */
type Reveal = { status: 'loading'; token: number } | { status: 'shown'; contact: LeadContact } | { status: 'error'; error: unknown }

function toChannel(value: string | null): Channel | null {
  return value && (CHANNELS as readonly string[]).includes(value) ? (value as Channel) : null
}

/** CSV 확인 대화상자에 보여줄 필터 범위. */
function exportScope(campaign: Campaign, formId: number | null, channel: Channel | null): string {
  if (!formId && !channel) return '전체'
  const form = formId ? `폼 ${campaign.forms.find((f) => f.id === formId)?.title ?? `#${formId}`}` : '전체 폼'
  const ch = channel ? `채널 ${CHANNEL_LABELS[channel]}` : '전체 채널'
  return `${form} · ${ch}`
}

export function LeadsTab({ campaign }: { campaign: Campaign }) {
  const [params, setParams] = useSearchParams()
  const page = Math.max(0, Number(params.get('page')) || 0)
  const formId = Number(params.get('form')) || null
  const channel = toChannel(params.get('channel'))

  const { data, error, loading, reload } = useAsync(
    () => listLeads(campaign.id, { page, formId, channel }),
    [campaign.id, page, formId, channel],
  )

  // 조회 조건이 바뀌면(다른 페이지·필터) 원문을 다시 가린다. 화면을 떠나면 상태가 사라진다.
  const queryKey = JSON.stringify([campaign.id, page, formId, channel])
  const [reveals, setReveals] = useState<{ key: string; byId: Record<number, Reveal> }>({ key: queryKey, byId: {} })
  if (reveals.key !== queryKey) setReveals({ key: queryKey, byId: {} })
  const revealed = reveals.key === queryKey ? reveals.byId : {}

  // 원문 요청마다 새 토큰을 준다. 응답은 그 토큰으로 아직 기다리는 중일 때만 반영한다. 그래서 화면을 옮겼다가
  // 같은 조회 조건으로 돌아오거나 가리기를 누른 뒤 늦게 온 응답은 버려진다.
  const nextToken = useRef(0)
  const [consentLeadId, setConsentLeadId] = useState<number | null>(null)

  function setReveal(id: number, reveal: Reveal | null, onlyIfToken?: number) {
    setReveals((prev) => {
      if (prev.key !== queryKey) return prev
      const current = prev.byId[id]
      if (onlyIfToken !== undefined && !(current?.status === 'loading' && current.token === onlyIfToken)) return prev
      const byId = { ...prev.byId }
      if (reveal) byId[id] = reveal
      else delete byId[id]
      return { key: queryKey, byId }
    })
  }

  function reveal(id: number) {
    const token = ++nextToken.current
    setReveal(id, { status: 'loading', token })
    getLeadContact(id).then(
      (contact) => setReveal(id, { status: 'shown', contact }, token),
      (error: unknown) => setReveal(id, { status: 'error', error }, token),
    )
  }

  function update(changes: Record<string, string | null>) {
    setParams((prev) => {
      const next = new URLSearchParams(prev)
      for (const [k, v] of Object.entries(changes)) {
        if (v) next.set(k, v)
        else next.delete(k)
      }
      return next
    })
  }

  const totalPages = data?.totalPages ?? 0
  const columns = 8

  return (
    <>
      <div className="toolbar" role="group" aria-label="명단 필터">
        <Field label="폼">
          {(p) => (
            <select {...p} value={formId ?? ''} onChange={(e) => update({ form: e.target.value, page: null })}>
              <option value="">전체 폼</option>
              {campaign.forms.map((f) => (
                <option key={f.id} value={f.id}>
                  {f.title}
                </option>
              ))}
            </select>
          )}
        </Field>
        <Field label="채널">
          {(p) => (
            <select {...p} value={channel ?? ''} onChange={(e) => update({ channel: e.target.value, page: null })}>
              <option value="">전체 채널</option>
              {CHANNELS.map((c) => (
                <option key={c} value={c}>
                  {CHANNEL_LABELS[c]}
                </option>
              ))}
            </select>
          )}
        </Field>
        {data && (
          <p className="toolbar-note muted small" aria-live="polite">
            총 <span className="num">{formatNumber(data.totalElements)}</span>명
          </p>
        )}
        <LeadExportMenu campaignId={campaign.id} formId={formId} channel={channel} scope={exportScope(campaign, formId, channel)} />
      </div>

      <ErrorMessage error={error} onRetry={reload} />
      {data && data.totalElements > 0 && data.items.length === 0 ? (
        <EmptyState
          message="이 페이지에는 신청이 없습니다. 마지막 페이지를 넘었습니다."
          action={
            <Button size="sm" onClick={() => update({ page: null })}>
              첫 페이지로
            </Button>
          }
        />
      ) : data && data.totalElements === 0 ? (
        <EmptyState
          message={
            formId || channel
              ? '조건에 맞는 신청이 없습니다. 필터를 바꿔 보세요.'
              : '아직 신청이 없습니다. 폼 탭에서 폼을 게시하고 채널 링크를 배포하면 여기에 쌓입니다.'
          }
          action={
            formId || channel ? (
              <Button size="sm" onClick={() => update({ form: null, channel: null, page: null })}>
                필터 지우기
              </Button>
            ) : undefined
          }
        />
      ) : (
        <>
          <TableWrap label="신청자 명단">
            <table className={`table${loading && data ? ' is-stale' : ''}`}>
              <caption className="visually-hidden">신청자 명단</caption>
              <thead>
                <tr>
                  <th scope="col">신청일</th>
                  <th scope="col">이름</th>
                  <th scope="col">전화번호</th>
                  <th scope="col">이메일</th>
                  <th scope="col">채널</th>
                  <th scope="col">폼</th>
                  <th scope="col">추가 항목</th>
                  <th scope="col">보기</th>
                </tr>
              </thead>
              <tbody aria-busy={loading}>
                {!data && loading ? (
                  <SkeletonRows columns={columns} />
                ) : (
                  data?.items.map((lead) => (
                    <LeadRow
                      key={lead.id}
                      lead={lead}
                      reveal={revealed[lead.id]}
                      onReveal={() => reveal(lead.id)}
                      onHide={() => setReveal(lead.id, null)}
                      onConsent={() => setConsentLeadId(lead.id)}
                    />
                  ))
                )}
              </tbody>
            </table>
          </TableWrap>
          {totalPages > 0 && (
            <nav className="pager" aria-label="명단 페이지">
              <ul className="pager-list">
                <li>
                  <Button size="sm" disabled={page <= 0} onClick={() => update({ page: page - 1 > 0 ? String(page - 1) : null })} aria-label="이전 페이지">
                    이전
                  </Button>
                </li>
                <li className="num" aria-current="page">
                  {page + 1} / {totalPages} 페이지
                </li>
                <li>
                  <Button size="sm" disabled={page + 1 >= totalPages} onClick={() => update({ page: String(page + 1) })} aria-label="다음 페이지">
                    다음
                  </Button>
                </li>
              </ul>
              <span className="muted small">페이지당 {LEAD_PAGE_SIZE}명</span>
            </nav>
          )}
        </>
      )}
      {consentLeadId !== null && <ConsentDialog leadId={consentLeadId} onClose={() => setConsentLeadId(null)} />}
    </>
  )
}

/** 보존 기간이 지나 파기된 줄. 연락처·추가 항목이 없고 원문·동의 문구 보기를 두지 않는다 (ADR 0020). */
function AnonymizedRow({ lead }: { lead: Lead }) {
  return (
    <tr>
      <td className="num nowrap">{formatDateTime(lead.createdAt)}</td>
      <td>
        <span className="muted" title={`보존 기간이 지나 ${formatDateTime(lead.anonymizedAt)}에 이름·연락처를 파기했습니다`}>
          {ANONYMIZED_LABEL}
        </span>
      </td>
      <td className="muted">-</td>
      <td className="muted">-</td>
      <td className="nowrap">{CHANNEL_LABELS[lead.channel] ?? lead.channel}</td>
      <td>{lead.formTitle}</td>
      <td className="muted">-</td>
      <td />
    </tr>
  )
}

function LeadRow({
  lead,
  reveal,
  onReveal,
  onHide,
  onConsent,
}: {
  lead: Lead
  reveal: Reveal | undefined
  onReveal: () => void
  onHide: () => void
  onConsent: () => void
}) {
  if (lead.anonymizedAt) return <AnonymizedRow lead={lead} />
  const extra = summarizePayload(lead.payload)
  const shown = reveal?.status === 'shown' ? reveal.contact : null
  const contact = shown ?? lead
  return (
    <tr>
      <td className="num nowrap">{formatDateTime(lead.createdAt)}</td>
      <td>
        {lead.contactUnavailable ? (
          <span className="muted" title="연락처를 복호화하지 못했습니다. 키 설정을 확인하세요">
            복호화 불가
          </span>
        ) : (
          contact.name || <span className="muted">-</span>
        )}
      </td>
      <td className="num nowrap">{formatPhone(contact.phone)}</td>
      <td className="break">{contact.email || <span className="muted">-</span>}</td>
      <td className="nowrap">{CHANNEL_LABELS[lead.channel] ?? lead.channel}</td>
      <td>{lead.formTitle}</td>
      <td className="truncate" title={extra}>
        {extra}
      </td>
      <td className="nowrap">
        {lead.contactUnavailable ? null : shown ? (
          <Button size="sm" variant="ghost" onClick={onHide}>
            가리기
          </Button>
        ) : (
          <Button size="sm" variant="ghost" busy={reveal?.status === 'loading'} onClick={onReveal}>
            원문 보기
          </Button>
        )}
        <Button size="sm" variant="ghost" onClick={onConsent}>
          동의 문구 보기
        </Button>
        {reveal?.status === 'error' && (
          <p className="small" role="alert">
            {errorText(reveal.error)}
          </p>
        )}
      </td>
    </tr>
  )
}
