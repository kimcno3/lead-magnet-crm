import { resolve } from 'node:path'
import { expect, test, type ConsoleMessage, type Page } from '@playwright/test'
import { createPublishedForm, login, uploadTemplate } from '../lib/admin'
import { BACKEND_ADMIN_URL, FIXTURES, PUBLIC_URL } from '../lib/env'
import { readState, step, type SharedState } from '../lib/steps'

// 시나리오 4. 실제 브라우저에서 격리 확인. probe.html의 인라인 스크립트가 할 수 있는 일을 시도하고
// 결과를 자기 DOM(<pre id="probe">)에 JSON으로 남긴다. 측정은 별도 캠페인에서 해 시나리오 3의 지표에 섞이지 않게 한다.
const AREA = '4. 보안(실제 브라우저)'
const PUBLIC_CSP =
  "default-src 'none'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; " +
  "font-src 'self' data:; connect-src 'self'; frame-src 'self'; form-action 'none'; base-uri 'none'; frame-ancestors 'none'"

interface ProbeError { ok: false; name?: string; message: string }
interface ProbeResult {
  done: boolean
  origin: string
  violations: { directive: string; blockedURI: string }[]
  parentDocument: ProbeError | { ok: true; value: string }
  cookie: ProbeError | { ok: true; value: string }
  localStorage: ProbeError | { ok: true; value: string }
  fetchAdminAbsolute: ProbeError | { ok: boolean; status: number; type: string }
  fetchAdminRelative: ProbeError | { ok: boolean; status: number; type: string }
  externalImage: ProbeError | { ok: true; width: number }
}

test.describe.configure({ mode: 'serial' })

let state: SharedState
let probeSlug = ''
const probeTemplate = '보안 프로브'
let admin: Page
let templateId = ''

test.beforeAll(async ({ browser }) => {
  state = readState()
  probeSlug = `probe-${state.runId}`
  admin = await (await browser.newContext()).newPage()
  await login(admin)
})

test.afterAll(async () => {
  await admin.context().close()
})

test('관리자 미리보기에서는 업로드 스크립트가 돌지 않는다', async () => {
  await step(
    {
      area: AREA,
      name: '관리자 미리보기 probe 미실행',
      assertion: '관리자 CSP(script-src \'self\')가 srcdoc 미리보기의 인라인 스크립트를 막아 #probe가 초기 문구 "probe: not run" 그대로다',
      screenshot: '13-admin-preview-probe',
      page: admin,
      fullPage: true,
    },
    async (record) => {
      const refused = admin.waitForEvent('console', {
        // Chromium 문구: "Executing inline script violates the following Content Security Policy directive 'script-src 'self''..."
        predicate: (m: ConsoleMessage) => /inline script/i.test(m.text()) && m.text().includes("script-src 'self'"),
        timeout: 15_000,
      })
      templateId = await uploadTemplate(admin, resolve(FIXTURES, 'probe.html'), probeTemplate)
      const preview = admin.locator(`iframe[title="${probeTemplate} 미리보기"]`)
      await expect(preview).toHaveAttribute('sandbox', 'allow-scripts allow-forms')
      const message = await refused
      await expect(preview.contentFrame().locator('#probe')).toHaveText('probe: not run')
      record({ console: message.text() })
    },
  )
  await step(
    { area: AREA, name: 'probe 폼 게시', assertion: '보안 점검 캠페인에 probe 템플릿으로 폼을 만들고 게시한다' },
    async () => {
      await createPublishedForm(admin, {
        campaignName: `보안 점검 ${state.runId}`,
        templateId,
        title: '보안 프로브',
        slug: probeSlug,
      })
    },
  )
})

test('공개 폼 sandbox 안에서 probe 실행', async ({ browser }) => {
  const context = await browser.newContext()
  const page = await context.newPage()
  const consoleMessages: string[] = []
  const failedRequests: string[] = []
  const externalRequests: string[] = []
  const externalResponses: string[] = []
  page.on('console', (m) => consoleMessages.push(`[${m.type()}] ${m.text()}`))
  page.on('requestfailed', (r) => failedRequests.push(`${r.method()} ${r.url()} → ${r.failure()?.errorText}`))
  // Chromium은 CSP로 막힌 요청도 'request' 이벤트를 낸 뒤 'requestfailed'(errorText "csp")로 끝낸다.
  // 그래서 "example.com에 닿았는가"는 응답 이벤트가 없는지와 실패 사유가 csp인지로 판단한다.
  page.on('request', (r) => {
    if (r.url().includes('example.com')) externalRequests.push(r.url())
  })
  page.on('response', (r) => {
    if (r.url().includes('example.com')) externalResponses.push(`${r.status()} ${r.url()}`)
  })

  try {
    const response = await page.goto(`${PUBLIC_URL}/f/${probeSlug}`)
    let probe!: ProbeResult

    await step(
      {
        area: AREA,
        name: 'probe 결과(공개 폼)',
        assertion:
          'parent.document·document.cookie·localStorage 접근이 SecurityError, 관리자 API fetch 두 개 실패, 외부 이미지는 CSP img-src 위반(securitypolicyviolation)으로 차단되어 example.com 응답이 없다(요청 실패 사유 csp)',
        screenshot: '14-probe-results',
        page,
        fullPage: true,
      },
      async (record) => {
        const frame = page.locator('#lm-frame').contentFrame()
        await expect(frame.locator('#probe')).toContainText('"done": true')
        probe = JSON.parse((await frame.locator('#probe').textContent()) ?? '{}')
        record({ probe, consoleMessages, failedRequests, externalRequests, externalResponses })

        expect(probe.origin, '불투명 출처').toBe('null')
        for (const key of ['parentDocument', 'cookie', 'localStorage'] as const) {
          expect(probe[key].ok, `${key} 접근`).toBe(false)
          expect((probe[key] as ProbeError).name, `${key} 오류 이름`).toBe('SecurityError')
        }
        expect(probe.fetchAdminAbsolute.ok, 'fetch http://localhost:8080/api/admin/auth/me').toBe(false)
        expect(probe.fetchAdminRelative.ok, "fetch('/api/admin/auth/me')").toBe(false)
        expect(probe.externalImage.ok, '외부 이미지').toBe(false)
        expect(probe.violations.some((v) => v.directive === 'img-src' && v.blockedURI.startsWith('https://example.com')), 'img-src 위반 이벤트').toBe(true)
        expect(probe.violations.some((v) => v.directive === 'connect-src' && v.blockedURI.startsWith(BACKEND_ADMIN_URL)), 'connect-src 위반 이벤트').toBe(true)
        expect(externalResponses, 'example.com에서 온 응답').toEqual([])
        expect(failedRequests, 'example.com 요청은 CSP로 실패').toContain('GET https://example.com/pixel.gif?leak=1 → csp')
      },
    )

    await step(
      {
        area: AREA,
        name: 'iframe sandbox 속성',
        assertion: '공개 폼 iframe의 sandbox 속성이 정확히 "allow-scripts allow-forms"(allow-same-origin 없음)',
      },
      async (record) => {
        const sandbox = await page.locator('#lm-frame').getAttribute('sandbox')
        record({ sandbox })
        expect(sandbox).toBe('allow-scripts allow-forms')
      },
    )

    await step(
      {
        area: AREA,
        name: '/f/{slug} 응답 헤더',
        assertion: 'CSP가 공개 정책과 정확히 같고 X-Frame-Options: DENY, X-Content-Type-Options: nosniff',
      },
      async (record) => {
        const headers = await response!.allHeaders()
        record({
          status: response!.status(),
          'content-security-policy': headers['content-security-policy'],
          'x-frame-options': headers['x-frame-options'],
          'x-content-type-options': headers['x-content-type-options'],
        })
        expect(response!.status()).toBe(200)
        expect(headers['content-security-policy']).toBe(PUBLIC_CSP)
        expect(headers['x-frame-options']).toBe('DENY')
        expect(headers['x-content-type-options']).toBe('nosniff')
      },
    )
  } finally {
    await context.close()
  }
})

test('포트 격리', async ({ browser }) => {
  const context = await browser.newContext()
  const page = await context.newPage()
  try {
    await step(
      {
        area: AREA,
        name: 'public 출처에서 관리자 API',
        assertion: `브라우저로 ${PUBLIC_URL}/api/admin/auth/me를 열면 404`,
        screenshot: '15-public-port-admin-api-404',
        page,
      },
      async (record) => {
        const res = await page.goto(`${PUBLIC_URL}/api/admin/auth/me`)
        record({ status: res?.status(), body: await res?.text() })
        expect(res?.status()).toBe(404)
      },
    )
    await step(
      {
        area: AREA,
        name: 'admin 출처에서 공개 폼',
        assertion: `브라우저로 ${BACKEND_ADMIN_URL}/f/{slug}를 열면 404`,
        screenshot: '16-admin-port-public-form-404',
        page,
      },
      async (record) => {
        const res = await page.goto(`${BACKEND_ADMIN_URL}/f/${probeSlug}`)
        record({ status: res?.status(), body: await res?.text() })
        expect(res?.status()).toBe(404)
      },
    )
  } finally {
    await context.close()
  }
})
