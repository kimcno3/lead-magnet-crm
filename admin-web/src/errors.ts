import { ApiError } from './api/client'
import { t } from './i18n/translate'

/** 화면에 보일 오류 문장. ProblemDetail의 detail을 그대로 쓴다(서버 문구라 번역하지 않는다). */
export function errorText(error: unknown): string {
  if (error instanceof ApiError) return error.message
  if (error instanceof TypeError) return t('errors.network')
  if (error instanceof Error) return error.message
  return t('errors.unknown')
}

/** 로그인 시도 제한(429, ADR 0021) 안내. Retry-After 초를 분으로 올려 보여준다. */
export function throttledText(retryAfterSeconds: number | null): string {
  if (retryAfterSeconds === null || retryAfterSeconds <= 0) return t('errors.throttled')
  const minutes = Math.ceil(retryAfterSeconds / 60)
  return t('errors.throttledMinutes', { minutes, count: minutes })
}
