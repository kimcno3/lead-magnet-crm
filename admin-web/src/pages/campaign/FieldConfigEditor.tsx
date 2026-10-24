import { useState, type FormEvent } from 'react'
import { formatOptions } from '../../format'
import { ApiError } from '../../api/client'
import { updateForm } from '../../api/forms'
import type { Form } from '../../api/types'
import { Button } from '../../components/Button'
import { TableWrap } from '../../components/Table'
import { errorText } from '../../errors'
import { useFocusFirstInvalid } from '../../hooks'
import { useI18n } from '../../i18n/context'

/** draft 폼의 label·required 수정. 백엔드는 빈 라벨을 받지 않으므로 저장 전에 막는다. */
export function FieldConfigEditor({ form, onSaved }: { form: Form; onSaved: () => void }) {
  const { t } = useI18n()
  const { formRef, focusFirstInvalid } = useFocusFirstInvalid()
  const [rows, setRows] = useState(() => form.fieldConfig.map((f) => ({ ...f, label: f.label ?? '' })))
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<{ kind: 'ok' | 'error'; text: string } | null>(null)
  const [blank, setBlank] = useState<Set<string>>(new Set())

  function change(index: number, patch: Partial<{ label: string; required: boolean }>) {
    setMessage(null)
    setRows((prev) => prev.map((r, i) => (i === index ? { ...r, ...patch } : r)))
    if (patch.label !== undefined && patch.label.trim()) {
      const name = rows[index].name
      setBlank((prev) => {
        if (!prev.has(name)) return prev
        const next = new Set(prev)
        next.delete(name)
        return next
      })
    }
  }

  async function save(e: FormEvent) {
    e.preventDefault()
    // 원래 라벨이 있던 필드를 비우면 저장하지 않는다(라벨 없던 필드는 비워 둬도 된다).
    const original = new Map(form.fieldConfig.map((f) => [f.name, f.label]))
    const empty = rows.filter((r) => !r.label.trim() && original.get(r.name)).map((r) => r.name)
    if (empty.length) {
      setBlank(new Set(empty))
      setMessage({ kind: 'error', text: t('fieldConfig.labelRequired') })
      focusFirstInvalid()
      return
    }
    setBusy(true)
    setMessage(null)
    try {
      await updateForm(form.id, {
        fieldConfig: rows.map((r) => ({ name: r.name, label: r.label.trim() || undefined, required: r.required })),
      })
      setMessage({ kind: 'ok', text: t('fieldConfig.saved') })
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
    <form ref={formRef} onSubmit={save} noValidate>
      <TableWrap label={t('forms.fieldConfigOf', { title: form.title })}>
        <table className="table table-compact">
          <caption className="visually-hidden">{t('forms.fieldConfigOf', { title: form.title })}</caption>
          <thead>
            <tr>
              <th scope="col" className="shrink">{t('common.name')}</th>
              <th scope="col" className="shrink">{t('components.fieldType')}</th>
              <th scope="col">{t('components.fieldLabel')}</th>
              <th scope="col" className="shrink">{t('common.required')}</th>
              <th scope="col">{t('components.fieldOptions')}</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r, i) => {
              const invalid = blank.has(r.name)
              const errorId = `label-error-${form.id}-${r.name}`
              return (
                <tr key={r.name}>
                  <td className="mono shrink" translate="no">
                    {r.name}
                  </td>
                  <td className="mono shrink">{r.type}</td>
                  <td className="text-cell">
                    <input
                      type="text"
                      className="input-inline"
                      aria-label={t('fieldConfig.labelOf', { name: r.name })}
                      aria-invalid={invalid || undefined}
                      aria-describedby={invalid ? errorId : undefined}
                      value={r.label}
                      autoComplete="off"
                      onChange={(e) => change(i, { label: e.target.value })}
                    />
                    {invalid && (
                      <p id={errorId} className="field-error">
                        {t('fieldConfig.enterLabel')}
                      </p>
                    )}
                  </td>
                  <td className="shrink">
                    <label className="check">
                      <input
                        type="checkbox"
                        aria-label={t('fieldConfig.requiredOf', { name: r.name })}
                        checked={r.required}
                        onChange={(e) => change(i, { required: e.target.checked })}
                      />
                      <span aria-hidden="true">{t('common.required')}</span>
                    </label>
                  </td>
                  <td className="options">{formatOptions(r.options) || <span className="muted">-</span>}</td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </TableWrap>
      <div className="inline-row form-actions">
        <Button type="submit" size="sm" variant="primary" busy={busy}>
          {t('fieldConfig.save')}
        </Button>
        <span className={message?.kind === 'error' ? 'form-error' : 'muted small'} role="status">
          {message?.text}
        </span>
      </div>
    </form>
  )
}
