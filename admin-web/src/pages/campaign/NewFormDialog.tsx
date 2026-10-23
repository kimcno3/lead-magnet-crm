import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { ApiError } from '../../api/client'
import { createForm } from '../../api/forms'
import { listTemplates } from '../../api/templates'
import type { Form } from '../../api/types'
import { Button } from '../../components/Button'
import { Dialog } from '../../components/Dialog'
import { ErrorMessage } from '../../components/ErrorMessage'
import { Field } from '../../components/Field'
import { errorText } from '../../errors'
import { useAsync, useFocusFirstInvalid } from '../../hooks'

/** 캠페인에 새 폼을 만든다. 템플릿 fields가 폼 fieldConfig의 스냅샷이 된다(ADR 0013). */
export function NewFormDialog({
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
  const { formRef, focusFirstInvalid } = useFocusFirstInvalid()
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
      focusFirstInvalid()
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
      <form ref={formRef} id="new-form" onSubmit={submit} noValidate>
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
              {(p) => <textarea {...p} rows={4} autoComplete="off" value={consentText} onChange={(e) => setConsentText(e.target.value)} required />}
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
