// 관리자 화면 CSP (ADR 0011). nginx.conf의 add_header와 같은 값이어야 한다(csp.test.ts가 확인한다).
// srcdoc 미리보기 iframe은 이 정책을 상속하므로 업로드 HTML의 스크립트는 관리자 출처에서 실행되지 않는다.
export const ADMIN_CSP = [
  "default-src 'self'",
  "script-src 'self'",
  "style-src 'self' 'unsafe-inline'",
  "img-src 'self' data: https:",
  "font-src 'self' data:",
  "connect-src 'self'",
  "frame-src 'self'",
  "form-action 'self'",
  "base-uri 'none'",
  "frame-ancestors 'none'",
].join('; ')

// Vite dev 서버는 React Fast Refresh 프리앰블을 인라인 스크립트로 넣는다. 개발 때만 고정 nonce로 그것만 허용한다.
export const DEV_NONCE = 'lm-admin-dev'

export function devCsp(): string {
  return ADMIN_CSP.replace("script-src 'self'", `script-src 'self' 'nonce-${DEV_NONCE}'`)
}
