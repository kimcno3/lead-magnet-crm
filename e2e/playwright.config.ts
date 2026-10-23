import { defineConfig, devices } from '@playwright/test'

// 전체 시스템(docker compose up)을 대상으로 도는 브라우저 E2E. 순서가 있는 시나리오라 직렬로 돌린다.
export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: [['list'], ['html', { open: 'never' }]],
  globalSetup: './lib/global-setup.ts',
  globalTeardown: './lib/global-teardown.ts',
  use: {
    ...devices['Desktop Chrome'],
    viewport: { width: 1440, height: 900 },
    colorScheme: 'light',
    locale: 'ko-KR',
    timezoneId: 'Asia/Seoul',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { browserName: 'chromium' } }],
})
