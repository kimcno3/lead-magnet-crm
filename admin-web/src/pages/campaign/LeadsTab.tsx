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
import { useI18n } from '../../i18n/context'
import { translate } from '../../i18n/translate'
import { anonymizedLabel, channelLabel } from '../../labels'
import { ConsentDialog } from './ConsentDialog'

/** 원문 보기 상태. 그 줄에만, 같은 명단 조회(캠페인·페이지·필터) 동안에만 유지한다. */
type Reveal = { status: 'loading'; token: number } | { status: 'shown'; contact: LeadContact } | { status: 'error'; error: unknown }

function toChannel(value: string | null): Channel | null {
  return value && (CHANNELS as readonly string[]).includes(value) ? (value as Channel) : null
}

/** CSV 확인 대화상자에 보여줄 필터 범위. */
function exportScope(campaign: Campaign, formId: number | null, channel: Channel | null): string {
  if (!formId && !channel) return translate('leads.scopeAll')
  const form = formId
    ? translate('leads.scopeForm', { title: campaign.forms.find((f) => f.id === formId)?.title ?? `#${formId}` })
    : translate('common.allForms')
  const ch = channel ? translate('leads.scopeChannel', { channel: channelLabel(channel) }) : translate('common.allChannels')
  return `${form} · ${ch}`
}

export function LeadsTab({ campaign }: { campaign: Campaign }) {
  const { t, rich } = useI18n()
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
      <div className="toolbar" role="group" aria-label={t('leads.filters')}>
        <Field label={t('common.form')}>
          {(p) => (
            <select {...p} value={formId ?? ''} onChange={(e) => update({ form: e.target.value, page: null })}>
              <option value="">{t('common.allForms')}</option>
              {campaign.forms.map((f) => (
                <option key={f.id} value={f.id}>
                  {f.title}
                </option>
              ))}
            </select>
          )}
        </Field>
        <Field label={t('common.channel')}>
          {(p) => (
            <select {...p} value={channel ?? ''} onChange={(e) => update({ channel: e.target.value, page: null })}>
              <option value="">{t('common.allChannels')}</option>
              {CHANNELS.map((c) => (
                <option key={c} value={c}>
                  {channelLabel(c)}
                </option>
              ))}
            </select>
          )}
        </Field>
        {data && (
          <p className="toolbar-note muted small" aria-live="polite">
            {rich('leads.total', { count: <span key="count" className="num">{formatNumber(data.totalElements)}</span> }, data.totalElements)}
          </p>
        )}
        <LeadExportMenu campaignId={campaign.id} formId={formId} channel={channel} scope={exportScope(campaign, formId, channel)} />
      </div>

      <ErrorMessage error={error} onRetry={reload} />
      {data && data.totalElements > 0 && data.items.length === 0 ? (
        <EmptyState
          message={t('leads.pastLastPage')}
          action={
            <Button size="sm" onClick={() => update({ page: null })}>
              {t('common.firstPage')}
            </Button>
          }
        />
      ) : data && data.totalElements === 0 ? (
        <EmptyState
          message={
            formId || channel
              ? t('leads.noMatch')
              : t('leads.empty')
          }
          action={
            formId || channel ? (
              <Button size="sm" onClick={() => update({ form: null, channel: null, page: null })}>
                {t('common.clearFilters')}
              </Button>
            ) : undefined
          }
        />
      ) : (
        <>
          <TableWrap label={t('leads.tableLabel')}>
            <table className={`table${loading && data ? ' is-stale' : ''}`}>
              <caption className="visually-hidden">{t('leads.tableLabel')}</caption>
              <thead>
                <tr>
                  <th scope="col">{t('leads.createdAt')}</th>
                  <th scope="col">{t('common.name')}</th>
                  <th scope="col">{t('leads.phone')}</th>
                  <th scope="col">{t('leads.email')}</th>
                  <th scope="col">{t('common.channel')}</th>
                  <th scope="col">{t('common.form')}</th>
                  <th scope="col">{t('leads.extra')}</th>
                  <th scope="col">{t('leads.actions')}</th>
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
            <nav className="pager" aria-label={t('leads.pager')}>
              <ul className="pager-list">
                <li>
                  <Button size="sm" disabled={page <= 0} onClick={() => update({ page: page - 1 > 0 ? String(page - 1) : null })} aria-label={t('common.prevPage')}>
                    {t('common.prev')}
                  </Button>
                </li>
                <li className="num" aria-current="page">
                  {t('common.pageOf', { page: page + 1, total: totalPages })}
                </li>
                <li>
                  <Button size="sm" disabled={page + 1 >= totalPages} onClick={() => update({ page: String(page + 1) })} aria-label={t('common.nextPage')}>
                    {t('common.next')}
                  </Button>
                </li>
              </ul>
              <span className="muted small">{t('leads.pageSize', { size: LEAD_PAGE_SIZE })}</span>
            </nav>
          )}
        </>
      )}
      {consentLeadId !== null && <ConsentDialog leadId={consentLeadId} onClose={() => setConsentLeadId(null)} />}
    </>
  )
}

/**
 * 보존 기간이 지나 파기된 줄. 연락처·추가 항목이 없고 원문 보기도 없다. 동의 문구는 증빙이라 파기 뒤에도 남아 있어
 * 동의 문구 보기만 둔다(개인정보가 아니다, ADR 0020).
 */
function AnonymizedRow({ lead, onConsent }: { lead: Lead; onConsent: () => void }) {
  const { t } = useI18n()
  return (
    <tr>
      <td className="num nowrap">{formatDateTime(lead.createdAt)}</td>
      <td>
        <span className="muted" title={t('leads.anonymizedTitle', { when: formatDateTime(lead.anonymizedAt) })}>
          {anonymizedLabel()}
        </span>
      </td>
      <td className="muted">-</td>
      <td className="muted">-</td>
      <td className="nowrap">{channelLabel(lead.channel)}</td>
      <td className="text-cell">{lead.formTitle}</td>
      <td className="muted">-</td>
      <td className="nowrap">
        <ConsentButton lead={lead} onClick={onConsent} />
      </td>
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
  const { t } = useI18n()
  if (lead.anonymizedAt) return <AnonymizedRow lead={lead} onConsent={onConsent} />
  const when = formatDateTime(lead.createdAt)
  const shown = reveal?.status === 'shown' ? reveal.contact : null
  // 원문 보기를 누른 줄은 payload에서 가려진 연락처 모양 항목도 원문으로 바꿔 보여 준다.
  const extra = summarizePayload(shown?.extraContacts ? { ...lead.payload, ...shown.extraContacts } : lead.payload)
  const contact = shown ?? lead
  return (
    <tr>
      <td className="num nowrap">{formatDateTime(lead.createdAt)}</td>
      <td className="nowrap">
        {lead.contactUnavailable ? (
          <span className="muted" title={t('leads.undecryptableTitle')}>
            {t('leads.undecryptable')}
          </span>
        ) : (
          contact.name || <span className="muted">-</span>
        )}
      </td>
      <td className="num nowrap">{formatPhone(contact.phone)}</td>
      <td className="email">{contact.email || <span className="muted">-</span>}</td>
      <td className="nowrap">{channelLabel(lead.channel)}</td>
      <td className="text-cell">{lead.formTitle}</td>
      <td className="truncate" title={extra}>
        {extra}
      </td>
      <td className="nowrap">
        {lead.contactUnavailable ? null : shown ? (
          <Button size="sm" variant="ghost" onClick={onHide} aria-label={t('leads.hideOf', { when })}>
            {t('leads.hide')}
          </Button>
        ) : (
          <Button
            size="sm"
            variant="ghost"
            busy={reveal?.status === 'loading'}
            onClick={onReveal}
            aria-label={t('leads.revealOf', { when })}
          >
            {t('leads.reveal')}
          </Button>
        )}
        <ConsentButton lead={lead} onClick={onConsent} />
        {reveal?.status === 'error' && (
          <p className="small" role="alert">
            {errorText(reveal.error)}
          </p>
        )}
      </td>
    </tr>
  )
}

/** 줄마다 같은 글자가 반복되므로 스크린리더에는 신청일을 붙여 어느 줄인지 알린다. */
function ConsentButton({ lead, onClick }: { lead: Lead; onClick: () => void }) {
  const { t } = useI18n()
  return (
    <Button size="sm" variant="ghost" onClick={onClick} aria-label={t('leads.consentOf', { when: formatDateTime(lead.createdAt) })}>
      {t('leads.consent')}
    </Button>
  )
}
