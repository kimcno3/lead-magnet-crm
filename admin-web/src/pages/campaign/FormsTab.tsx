import { Fragment, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { ApiError } from '../../api/client'
import { createForm, getForm, updateForm } from '../../api/forms'
import { createLink, listLinks } from '../../api/links'
import { listTemplates } from '../../api/templates'
import { LINK_CHANNELS, type Campaign, type Form, type FormSummary, type LinkChannel } from '../../api/types'
import { StatusBadge } from '../../components/Badge'
import { Button } from '../../components/Button'
import { ConfirmDialog } from '../../components/ConfirmDialog'
import { CopyButton } from '../../components/CopyButton'
import { Dialog } from '../../components/Dialog'
import { EmptyState } from '../../components/EmptyState'
import { ErrorMessage } from '../../components/ErrorMessage'
import { errorText } from '../../errors'
import { Field } from '../../components/Field'
import { FieldsTable } from '../../components/FieldsTable'
import { TableWrap } from '../../components/Table'
import { formatDateTime } from '../../format'
import { useAsync } from '../../hooks'
import { CHANNEL_LABELS, publicBaseFrom } from '../../labels'

export function FormsTab({ campaign, onChanged }: { campaign: Campaign; onChanged: () => void }) {
  const [openId, setOpenId] = useState<number | null>(null)
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
                      <td className="mono">{f.slug}</td>
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
          <code className="url">{publicUrl}</code>
          <CopyButton text={publicUrl} />
          {status === 'published' && (
            <a className="btn btn-ghost btn-sm" href={publicUrl} target="_blank" rel="noopener noreferrer">
              새 탭에서 열기
            </a>
          )}
        </div>
      </section>

      <section className="panel-block" aria-label="배포 링크">
        <h3>배포 링크</h3>
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
                const link = links.data?.find((l) => l.channel === channel)
                return (
                  <tr key={channel}>
                    <td className="nowrap">{CHANNEL_LABELS[channel]}</td>
                    <td>
                      {link ? (
                        <span className="inline-row">
                          <code className="url">{link.url}</code>
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
                    <td className="mono small">
                      {link ? `${link.utmSource} / ${link.utmMedium} / ${link.utmCampaign}` : <span className="muted">-</span>}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </TableWrap>
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

function FieldConfigEditor({ form, onSaved }: { form: Form; onSaved: () => void }) {
  const [rows, setRows] = useState(() => form.fieldConfig.map((f) => ({ ...f, label: f.label ?? '' })))
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<{ kind: 'ok' | 'error'; text: string } | null>(null)

  function change(index: number, patch: Partial<{ label: string; required: boolean }>) {
    setMessage(null)
    setRows((prev) => prev.map((r, i) => (i === index ? { ...r, ...patch } : r)))
  }

  async function save(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setMessage(null)
    try {
      await updateForm(form.id, {
        fieldConfig: rows.map((r) => ({ name: r.name, label: r.label.trim() || undefined, required: r.required })),
      })
      setMessage({ kind: 'ok', text: '필드 설정을 저장했습니다' })
      onSaved()
    } catch (err) {
      // 백엔드는 fieldConfig 하나로 오류를 묶고 메시지에 필드 이름을 넣는다.
      const detail = err instanceof ApiError ? err.fieldErrors.fieldConfig : undefined
      setMessage({ kind: 'error', text: detail ?? errorText(err) })
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={save}>
      <TableWrap label={`${form.title} 필드 설정`}>
        <table className="table table-compact">
          <caption className="visually-hidden">{form.title} 필드 설정</caption>
          <thead>
            <tr>
              <th scope="col">이름</th>
              <th scope="col">유형</th>
              <th scope="col">라벨</th>
              <th scope="col">필수</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r, i) => (
                <tr key={r.name}>
                  <td className="mono">{r.name}</td>
                  <td className="mono">{r.type}</td>
                  <td>
                    <input
                      type="text"
                      className="input-inline"
                      aria-label={`${r.name} 라벨`}
                      value={r.label}
                      autoComplete="off"
                      onChange={(e) => change(i, { label: e.target.value })}
                    />
                  </td>
                  <td>
                    <label className="check">
                      <input type="checkbox" checked={r.required} onChange={(e) => change(i, { required: e.target.checked })} />
                      <span>필수</span>
                    </label>
                  </td>
                </tr>
            ))}
          </tbody>
        </table>
      </TableWrap>
      <div className="inline-row form-actions">
        <Button type="submit" size="sm" variant="primary" busy={busy}>
          필드 설정 저장
        </Button>
        <span className={message?.kind === 'error' ? 'form-error' : 'muted small'} role="status">
          {message?.text}
        </span>
      </div>
    </form>
  )
}

function NewFormDialog({
  open,
  campaignId,
  onClose,
  onCreated,
}: {
  open: boolean
  campaignId: number
  onClose: () => void
  onCreated: (form: Form) => void
}) {
  const templates = useAsync(() => (open ? listTemplates() : Promise.resolve(null)), [open])
  const [templateId, setTemplateId] = useState('')
  const [title, setTitle] = useState('')
  const [consentText, setConsentText] = useState('')
  const [slug, setSlug] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  function close() {
    setTemplateId('')
    setTitle('')
    setConsentText('')
    setSlug('')
    setErrors({})
    setFormError(null)
    onClose()
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setErrors({})
    setFormError(null)
    try {
      const created = await createForm(campaignId, {
        templateId: templateId ? Number(templateId) : null,
        title: title.trim(),
        consentText: consentText.trim(),
        slug: slug.trim() || null,
      })
      setTemplateId('')
      setTitle('')
      setConsentText('')
      setSlug('')
      onCreated(created)
    } catch (err) {
      if (err instanceof ApiError && err.code === 'slug_taken') setErrors({ slug: err.message })
      else if (err instanceof ApiError && err.errors.length) setErrors(err.fieldErrors)
      else setFormError(errorText(err))
    } finally {
      setBusy(false)
    }
  }

  const list = templates.data ?? []
  return (
    <Dialog
      open={open}
      title="새 폼"
      onClose={close}
      footer={
        <>
          <Button onClick={close}>취소</Button>
          <Button variant="primary" type="submit" form="new-form" busy={busy} disabled={templates.data?.length === 0}>
            만들기
          </Button>
        </>
      }
    >
      <form id="new-form" onSubmit={submit} noValidate>
        <ErrorMessage error={templates.error} onRetry={templates.reload} />
        {templates.data?.length === 0 ? (
          <p className="note">
            먼저 HTML 템플릿을 등록해야 합니다. <Link to="/templates">템플릿 화면으로 이동</Link>
          </p>
        ) : (
          <>
            <Field label="템플릿" error={errors.templateId}>
              {(p) => (
                <select {...p} value={templateId} onChange={(e) => setTemplateId(e.target.value)} required>
                  <option value="">{templates.loading ? '불러오는 중…' : '템플릿 선택'}</option>
                  {list.map((t) => (
                    <option key={t.id} value={t.id}>
                      {t.name} (필드 {t.fieldCount}개)
                    </option>
                  ))}
                </select>
              )}
            </Field>
            <Field label="제목" error={errors.title}>
              {(p) => <input {...p} type="text" value={title} autoComplete="off" onChange={(e) => setTitle(e.target.value)} required />}
            </Field>
            <Field label="동의 문구" error={errors.consentText} hint="신청자가 체크해야 하는 개인정보 수집·이용 동의 문구입니다">
              {(p) => <textarea {...p} rows={4} value={consentText} onChange={(e) => setConsentText(e.target.value)} required />}
            </Field>
            <Field label="slug" optional error={errors.slug} hint="공개 주소 /f/{slug}. 비우면 자동으로 만듭니다. 소문자·숫자·하이픈 3~64자">
              {(p) => (
                <input
                  {...p}
                  type="text"
                  value={slug}
                  autoComplete="off"
                  spellCheck={false}
                  autoCapitalize="none"
                  onChange={(e) => setSlug(e.target.value)}
                />
              )}
            </Field>
          </>
        )}
        {formError && (
          <p className="form-error" role="alert">
            {formError}
          </p>
        )}
      </form>
    </Dialog>
  )
}
