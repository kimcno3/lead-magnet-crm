import { useState, type FormEvent } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/context'
import { Button } from '../components/Button'
import { errorText } from '../errors'
import { Field } from '../components/Field'

const BAD_CREDENTIALS = '이메일 또는 비밀번호가 올바르지 않습니다'

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
      if (err instanceof ApiError && err.status === 401) setError(BAD_CREDENTIALS)
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
        <p className="login-brand">리드마그넷 CRM</p>
        <h1>운영자 로그인</h1>
        <form onSubmit={onSubmit} noValidate>
          <Field label="이메일" error={fieldErrors.email}>
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
          <Field label="비밀번호" error={fieldErrors.password}>
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
            {submitting ? '로그인 중…' : '로그인'}
          </Button>
        </form>
        {status === 'restoring' && (
          <p className="muted small" role="status">
            이전 세션을 확인하는 중…
          </p>
        )}
      </div>
    </main>
  )
}
