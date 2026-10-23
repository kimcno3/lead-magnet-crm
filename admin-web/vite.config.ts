/// <reference types="vitest/config" />
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// 관리자 API는 같은 출처로 프록시한다. 브라우저에는 admin-web 출처 하나만 보이므로
// refresh token 쿠키(SameSite=Strict)가 그대로 동작하고 CORS가 필요 없다.
const backend = process.env.BACKEND_ADMIN_URL ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api/admin': backend,
      '/actuator/health': backend,
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test-setup.ts'],
  },
})
