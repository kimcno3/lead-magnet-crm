import { ApiError } from './api/client'

/** 화면에 보일 오류 문장. ProblemDetail의 detail을 그대로 쓴다. */
export function errorText(error: unknown): string {
  if (error instanceof ApiError) return error.message
  if (error instanceof TypeError) return '서버에 연결하지 못했습니다. 네트워크 상태를 확인해 주세요'
  if (error instanceof Error) return error.message
  return '알 수 없는 오류가 발생했습니다'
}

/** 로그인 시도 제한(429, ADR 0021) 안내. Retry-After 초를 분으로 올려 보여준다. */
export function throttledText(retryAfterSeconds: number | null): string {
  if (retryAfterSeconds === null || retryAfterSeconds <= 0) return '잠시 후 다시 시도해 주세요'
  return `잠시 후 다시 시도해 주세요(약 ${Math.ceil(retryAfterSeconds / 60)}분)`
}
