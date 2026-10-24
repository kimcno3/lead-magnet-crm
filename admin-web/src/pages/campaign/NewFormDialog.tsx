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
import { useI18n } from '../../i18n/context'

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
  const { t, rich } = useI18n()
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
      title={t('forms.new')}
      onClose={close}
      footer={
        <>
          <Button onClick={close}>{t('common.cancel')}</Button>
          <Button variant="primary" type="submit" form="new-form" busy={busy} disabled={templates.data?.length === 0}>
            {t('common.create')}
          </Button>
        </>
      }
    >
      <form ref={formRef} id="new-form" onSubmit={submit} noValidate>
        <ErrorMessage error={templates.error} onRetry={templates.reload} />
        {templates.data?.length === 0 ? (
          <p className="note">
            {rich('newForm.needTemplate', { link: <Link key="link" to="/templates">{t('newForm.goToTemplates')}</Link> })}
          </p>
        ) : (
          <>
            <Field label={t('newForm.template')} error={errors.templateId}>
              {(p) => (
                <select {...p} value={templateId} onChange={(e) => setTemplateId(e.target.value)} required>
                  <option value="">{templates.loading ? t('common.loading') : t('newForm.selectTemplate')}</option>
                  {list.map((tpl) => (
                    <option key={tpl.id} value={tpl.id}>
                      {t('newForm.templateOption', { name: tpl.name, count: tpl.fieldCount })}
                    </option>
                  ))}
                </select>
              )}
            </Field>
            <Field label={t('forms.title')} error={errors.title}>
              {(p) => <input {...p} type="text" value={title} autoComplete="off" onChange={(e) => setTitle(e.target.value)} required />}
            </Field>
            <Field label={t('newForm.consentText')} error={errors.consentText} hint={t('newForm.consentHint')}>
              {(p) => <textarea {...p} rows={4} autoComplete="off" value={consentText} onChange={(e) => setConsentText(e.target.value)} required />}
            </Field>
            <Field label="slug" optional error={errors.slug} hint={t('newForm.slugHint')}>
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
