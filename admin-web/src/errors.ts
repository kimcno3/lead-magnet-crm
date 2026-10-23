import { ApiError } from './api/client'

/** 화면에 보일 오류 문장. ProblemDetail의 detail을 그대로 쓴다. */
export function errorText(error: unknown): string {
  if (error instanceof ApiError) return error.message
  if (error instanceof TypeError) return '서버에 연결하지 못했습니다. 네트워크 상태를 확인해 주세요'
  if (error instanceof Error) return error.message
  return '알 수 없는 오류가 발생했습니다'
}
