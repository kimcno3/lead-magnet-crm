/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'
import { devCsp, DEV_NONCE } from './csp.ts'

// 관리자 API는 같은 출처로 프록시한다. 브라우저에는 admin-web 출처 하나만 보이므로
// refresh token 쿠키(SameSite=Strict)가 그대로 동작하고 CORS가 필요 없다.
const backend = process.env.BACKEND_ADMIN_URL ?? 'http://localhost:8080'

export default defineConfig(({ command }) => ({
  plugins: [react()],
  // 개발 서버에서만 nonce를 붙인다. 빌드 결과물에는 인라인 스크립트가 없어 nginx CSP의 script-src 'self'로 충분하다.
  html: command === 'serve' && !process.env.VITEST ? { cspNonce: DEV_NONCE } : undefined,
  server: {
    port: 5173,
    headers: { 'Content-Security-Policy': devCsp() },
    proxy: {
      '/api/admin': backend,
      '/actuator/health': backend,
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test-setup.ts'],
  },
}))
