// @vitest-environment node
import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { ADMIN_CSP, devCsp } from './csp.ts'

const nginx = readFileSync(new URL('./nginx.conf', import.meta.url), 'utf8')

describe('관리자 CSP', () => {
  it('nginx는 ADMIN_CSP와 같은 값을 모든 응답에 붙인다(always)', () => {
    const match = nginx.match(/add_header Content-Security-Policy "([^"]+)" always;/)
    expect(match?.[1]).toBe(ADMIN_CSP)
  })

  it('스크립트는 같은 출처만, 인라인·eval은 허용하지 않는다', () => {
    const script = ADMIN_CSP.split('; ').find((d) => d.startsWith('script-src'))
    expect(script).toBe("script-src 'self'")
    expect(ADMIN_CSP).not.toContain('unsafe-eval')
    expect(ADMIN_CSP).toContain("frame-ancestors 'none'")
    expect(ADMIN_CSP).toContain("base-uri 'none'")
  })

  it('개발 서버 CSP는 script-src에 nonce 하나만 더한다', () => {
    expect(devCsp()).toBe(ADMIN_CSP.replace("script-src 'self'", "script-src 'self' 'nonce-lm-admin-dev'"))
  })

  it('location 블록에 add_header가 없어 서버 수준 보안 헤더를 덮어쓰지 않는다', () => {
    const blocks = nginx.split(/\blocation\b/).slice(1)
    for (const block of blocks) expect(block).not.toContain('add_header')
  })

  it('관리자 API 프록시는 2MB까지 받아 1~2MB 업로드도 Spring의 ProblemDetail 413을 받게 한다', () => {
    const api = nginx.slice(nginx.indexOf('location /api/admin/'), nginx.indexOf('}', nginx.indexOf('location /api/admin/')))
    expect(api).toMatch(/client_max_body_size 2m;/)
  })
})
