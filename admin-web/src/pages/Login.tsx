import { useState, type FormEvent } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/context'
import { Button } from '../components/Button'
import { errorText, throttledText } from '../errors'
import { Field } from '../components/Field'
import { useI18n } from '../i18n/context'
import { LanguagePicker } from '../i18n/LanguagePicker'

function targetFrom(state: unknown): string {
  const from = (state as { from?: unknown } | null)?.from
  // 같은 앱 안의 경로만 허용한다(//evil.example 같은 프로토콜 상대 주소 차단).
  return typeof from === 'string' && from.startsWith('/') && !from.startsWith('//') && from !== '/login'
    ? from
    : '/campaigns'
}

export function LoginPage() {
  const { status, signIn } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const { t } = useI18n()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)
  const target = targetFrom(location.state)

  if (status === 'authenticated' && !submitting) return <Navigate to={target} replace />

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setSubmitting(true)
    setError(null)
    setFieldErrors({})
    try {
      await signIn(email.trim(), password)
      navigate(target, { replace: true })
    } catch (err) {
      setPassword('')
      if (err instanceof ApiError && err.status === 401) setError(t('login.badCredentials'))
      else if (err instanceof ApiError && err.status === 429) setError(throttledText(err.retryAfterSeconds))
      else if (err instanceof ApiError && err.status === 422) {
        setFieldErrors(err.fieldErrors)
        setError(err.message)
      } else setError(errorText(err))
      setSubmitting(false)
    }
  }

  return (
    <main className="login">
      <div className="login-panel">
        <p className="login-brand">{t('app.brand')}</p>
        <h1>{t('login.title')}</h1>
        <form onSubmit={onSubmit} noValidate>
          <Field label={t('login.email')} error={fieldErrors.email}>
            {(p) => (
              <input
                {...p}
                type="email"
                name="email"
                autoComplete="username"
                spellCheck={false}
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
            )}
          </Field>
          <Field label={t('login.password')} error={fieldErrors.password}>
            {(p) => (
              <input
                {...p}
                type="password"
                name="password"
                autoComplete="current-password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            )}
          </Field>
          {error && (
            <p className="form-error" role="alert">
              {error}
            </p>
          )}
          <Button type="submit" variant="primary" busy={submitting} className="btn-block">
            {submitting ? t('login.submitting') : t('login.submit')}
          </Button>
        </form>
        {status === 'restoring' && (
          <p className="muted small" role="status">
            {t('login.restoring')}
          </p>
        )}
        <div className="login-lang">
          <LanguagePicker />
        </div>
      </div>
    </main>
  )
}
