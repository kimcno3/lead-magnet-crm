import { Fragment, useState } from 'react'
import { useSearchParams } from 'react-router'
import { getForm, updateForm } from '../../api/forms'
import { createLink, listLinks } from '../../api/links'
import { LINK_CHANNELS, type Campaign, type FormSummary, type LinkChannel } from '../../api/types'
import { StatusBadge } from '../../components/Badge'
import { Button } from '../../components/Button'
import { ConfirmDialog } from '../../components/ConfirmDialog'
import { CopyButton } from '../../components/CopyButton'
import { EmptyState } from '../../components/EmptyState'
import { ErrorMessage } from '../../components/ErrorMessage'
import { errorText } from '../../errors'
import { FieldsTable } from '../../components/FieldsTable'
import { TableWrap } from '../../components/Table'
import { formatDateTime } from '../../format'
import { useAsync } from '../../hooks'
import { CHANNEL_LABELS, publicBaseFrom } from '../../labels'
import { FieldConfigEditor } from './FieldConfigEditor'
import { CreatorLinks } from './CreatorLinks'
import { NewFormDialog } from './NewFormDialog'

export function FormsTab({ campaign, onChanged }: { campaign: Campaign; onChanged: () => void }) {
  // 펼친 폼은 URL(?open=)에 남겨 새로고침·공유해도 같은 패널이 열린다.
  const [params, setParams] = useSearchParams()
  const openId = Number(params.get('open')) || null
  function setOpenId(id: number | null) {
    setParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        if (id) next.set('open', String(id))
        else next.delete('open')
        return next
      },
      { replace: true },
    )
  }
  const [creating, setCreating] = useState(false)
  const archived = campaign.status === 'archived'

  const newButton = (
    <Button variant="primary" onClick={() => setCreating(true)} disabled={archived} title={archived ? '보관된 캠페인에는 폼을 만들 수 없습니다' : undefined}>
      새 폼
    </Button>
  )

  return (
    <>
      <div className="section-head">
        <h2 className="section-title">폼 {campaign.forms.length}개</h2>
        {newButton}
      </div>
      {archived && <p className="note">보관된 캠페인입니다. 새 폼을 만들 수 없습니다.</p>}
      {campaign.forms.length === 0 ? (
        <EmptyState
          message={archived ? '이 캠페인에는 폼이 없습니다.' : '아직 폼이 없습니다. 등록한 템플릿으로 첫 폼을 만들어 보세요.'}
          action={archived ? undefined : newButton}
        />
      ) : (
        <TableWrap label="폼 목록">
          <table className="table table-expandable">
            <caption className="visually-hidden">폼 목록</caption>
            <thead>
              <tr>
                <th scope="col">제목</th>
                <th scope="col">slug</th>
                <th scope="col">상태</th>
                <th scope="col">게시일</th>
              </tr>
            </thead>
            <tbody>
              {campaign.forms.map((f) => {
                const open = openId === f.id
                return (
                  <Fragment key={f.id}>
                    <tr className={open ? 'is-open' : undefined}>
                      <td>
                        <button
                          type="button"
                          className="disclosure"
                          aria-expanded={open}
                          aria-controls={`form-panel-${f.id}`}
                          onClick={() => setOpenId(open ? null : f.id)}
                        >
                          <span className="disclosure-mark" aria-hidden="true">
                            {open ? '−' : '+'}
                          </span>
                          {f.title}
                        </button>
                      </td>
                      <td className="mono" translate="no">{f.slug}</td>
                      <td>
                        <StatusBadge status={f.status} />
                      </td>
                      <td className="num">{formatDateTime(f.publishedAt)}</td>
                    </tr>
                    {open && (
                      <tr className="panel-row">
                        <td colSpan={4} id={`form-panel-${f.id}`}>
                          <FormPanel summary={f} onChanged={onChanged} />
                        </td>
                      </tr>
                    )}
                  </Fragment>
                )
              })}
            </tbody>
          </table>
        </TableWrap>
      )}
      <NewFormDialog open={creating} campaignId={campaign.id} onClose={() => setCreating(false)} onCreated={(form) => {
        setCreating(false)
        setOpenId(form.id)
        onChanged()
      }} />
    </>
  )
}

function FormPanel({ summary, onChanged }: { summary: FormSummary; onChanged: () => void }) {
  const form = useAsync(() => getForm(summary.id), [summary.id, summary.status])
  const links = useAsync(() => listLinks(summary.id), [summary.id])
  const [transition, setTransition] = useState<'published' | 'closed' | null>(null)
  const [creatingChannel, setCreatingChannel] = useState<LinkChannel | null>(null)
  const [linkError, setLinkError] = useState<string | null>(null)

  const publicUrl = `${publicBaseFrom(links.data?.[0]?.url)}/f/${summary.slug}`
  const status = form.data?.status ?? summary.status

  async function makeLink(channel: LinkChannel) {
    setCreatingChannel(channel)
    setLinkError(null)
    try {
      await createLink(summary.id, channel)
      links.reload()
    } catch (e) {
      setLinkError(`${CHANNEL_LABELS[channel]} 링크를 만들지 못했습니다: ${errorText(e)}`)
    } finally {
      setCreatingChannel(null)
    }
  }

  return (
    <div className="form-panel">
      <section className="panel-block" aria-label="상태">
        <h3>상태</h3>
        <div className="inline-row">
          <StatusBadge status={status} />
          {status === 'draft' && (
            <Button variant="primary" size="sm" onClick={() => setTransition('published')}>
              게시
            </Button>
          )}
          {status === 'published' && (
            <Button size="sm" onClick={() => setTransition('closed')}>
              마감
            </Button>
          )}
          {status === 'closed' && <span className="muted small">마감된 폼은 신청을 받지 않고 새 링크를 만들 수 없습니다.</span>}
          {status === 'draft' && <span className="muted small">초안은 공개 URL로 열리지 않습니다. 필드 설정을 마친 뒤 게시하세요.</span>}
        </div>
      </section>

      <section className="panel-block" aria-label="공개 URL">
        <h3>공개 URL</h3>
        <div className="inline-row">
          <code className="url" translate="no">{publicUrl}</code>
          <CopyButton text={publicUrl} />
          {status === 'published' && (
            <a className="btn btn-ghost btn-sm" href={publicUrl} target="_blank" rel="noopener noreferrer">
              새 탭에서 열기
            </a>
          )}
        </div>
      </section>

      <section className="panel-block" aria-label="배포 링크">
        <h3>배포 링크 (크리에이터 없음)</h3>
        <ErrorMessage error={links.error} onRetry={links.reload} />
        {linkError && (
          <p className="form-error" role="alert">
            {linkError}
          </p>
        )}
        <TableWrap label="채널별 배포 링크">
          <table className="table table-compact">
            <caption className="visually-hidden">채널별 배포 링크</caption>
            <thead>
              <tr>
                <th scope="col">채널</th>
                <th scope="col">링크</th>
                <th scope="col">UTM (source / medium / campaign)</th>
              </tr>
            </thead>
            <tbody>
              {LINK_CHANNELS.map((channel) => {
                const link = links.data?.find((l) => l.channel === channel && !l.creator)
                return (
                  <tr key={channel}>
                    <td className="nowrap">{CHANNEL_LABELS[channel]}</td>
                    <td>
                      {link ? (
                        <span className="inline-row">
                          <code className="url" translate="no">{link.url}</code>
                          <CopyButton text={link.url} />
                        </span>
                      ) : links.data ? (
                        <Button
                          size="sm"
                          onClick={() => makeLink(channel)}
                          busy={creatingChannel === channel}
                          disabled={status === 'closed' || creatingChannel !== null}
                        >
                          링크 만들기
                        </Button>
                      ) : (
                        <span className="skeleton" aria-hidden="true" />
                      )}
                    </td>
                    <td className="mono small nowrap">
                      {link ? `${link.utmSource} / ${link.utmMedium} / ${link.utmCampaign}` : <span className="muted">-</span>}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </TableWrap>
      </section>

      <section className="panel-block">
        <h3>크리에이터별 배포 링크</h3>
        <CreatorLinks formId={summary.id} closed={status === 'closed'} links={links.data} onCreated={links.reload} />
      </section>

      <section className="panel-block" aria-label="필드 설정">
        <h3>필드 설정</h3>
        <ErrorMessage error={form.error} onRetry={form.reload} />
        {form.data &&
          (form.data.status === 'draft' ? (
            <FieldConfigEditor key={form.data.id} form={form.data} onSaved={form.reload} />
          ) : (
            <>
              <p className="muted small">게시한 뒤에는 필드 설정을 바꿀 수 없습니다.</p>
              <FieldsTable fields={form.data.fieldConfig} caption={`${summary.title} 필드 설정`} />
            </>
          ))}
      </section>

      <ConfirmDialog
        open={transition !== null}
        title={transition === 'published' ? '폼 게시' : '폼 마감'}
        message={
          transition === 'published'
            ? '게시하면 공개 URL로 신청을 받기 시작하고, 제목·동의 문구·필드 설정은 더 이상 바꿀 수 없습니다.'
            : '마감하면 공개 URL과 배포 링크로 더 이상 신청을 받지 않습니다. 다시 열 수 없습니다.'
        }
        confirmLabel={transition === 'published' ? '게시하기' : '마감하기'}
        danger={transition === 'closed'}
        onConfirm={async () => {
          if (!transition) return
          await updateForm(summary.id, { status: transition })
          onChanged()
        }}
        onClose={() => setTransition(null)}
      />
    </div>
  )
}
