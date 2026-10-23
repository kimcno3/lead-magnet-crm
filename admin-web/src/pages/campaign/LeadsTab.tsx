import { useState } from 'react'
import { useSearchParams } from 'react-router'
import { getLeadContact, LEAD_PAGE_SIZE, listLeads } from '../../api/leads'
import { CHANNELS, type Campaign, type Channel, type Lead, type LeadContact } from '../../api/types'
import { Button } from '../../components/Button'
import { EmptyState } from '../../components/EmptyState'
import { ErrorMessage } from '../../components/ErrorMessage'
import { Field } from '../../components/Field'
import { SkeletonRows, TableWrap } from '../../components/Table'
import { formatDateTime, formatNumber, formatPhone, summarizePayload } from '../../format'
import { errorText } from '../../errors'
import { useAsync } from '../../hooks'
import { CHANNEL_LABELS } from '../../labels'

/** 원문 보기 상태. 그 줄에만, 같은 명단 조회(캠페인·페이지·필터) 동안에만 유지한다. */
type Reveal = { status: 'loading' } | { status: 'shown'; contact: LeadContact } | { status: 'error'; error: unknown }

function toChannel(value: string | null): Channel | null {
  return value && (CHANNELS as readonly string[]).includes(value) ? (value as Channel) : null
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

  function setReveal(id: number, reveal: Reveal | null) {
    // 응답이 늦게 와도 다른 조회 조건의 화면에는 원문을 남기지 않는다.
    setReveals((prev) => {
      if (prev.key !== queryKey) return prev
      const byId = { ...prev.byId }
      if (reveal) byId[id] = reveal
      else delete byId[id]
      return { key: queryKey, byId }
    })
  }

  function reveal(id: number) {
    setReveal(id, { status: 'loading' })
    getLeadContact(id).then(
      (contact) => setReveal(id, { status: 'shown', contact }),
      (error: unknown) => setReveal(id, { status: 'error', error }),
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
                  <th scope="col">원문</th>
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
    </>
  )
}

function LeadRow({ lead, reveal, onReveal, onHide }: { lead: Lead; reveal: Reveal | undefined; onReveal: () => void; onHide: () => void }) {
  const extra = summarizePayload(lead.payload)
  const shown = reveal?.status === 'shown' ? reveal.contact : null
  const contact = shown ?? lead
  return (
    <tr>
      <td className="num nowrap">{formatDateTime(lead.createdAt)}</td>
      <td>{contact.name || <span className="muted">-</span>}</td>
      <td className="num nowrap">{formatPhone(contact.phone)}</td>
      <td className="break">{contact.email || <span className="muted">-</span>}</td>
      <td className="nowrap">{CHANNEL_LABELS[lead.channel] ?? lead.channel}</td>
      <td>{lead.formTitle}</td>
      <td className="truncate" title={extra}>
        {extra}
      </td>
      <td className="nowrap">
        {shown ? (
          <Button size="sm" variant="ghost" onClick={onHide}>
            가리기
          </Button>
        ) : (
          <Button size="sm" variant="ghost" busy={reveal?.status === 'loading'} onClick={onReveal}>
            원문 보기
          </Button>
        )}
        {reveal?.status === 'error' && (
          <p className="small" role="alert">
            {errorText(reveal.error)}
          </p>
        )}
      </td>
    </tr>
  )
}
