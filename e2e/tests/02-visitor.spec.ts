import { expect, test, type BrowserContext, type Page } from '@playwright/test'
import { PUBLIC_URL } from '../lib/env'
import { readState, step, type SharedState } from '../lib/steps'

// 시나리오 2. 방문자 흐름. 브라우저 컨텍스트 하나 = 방문자 한 명(lm_vid 쿠키가 따로 생긴다).
//   A: 인스타그램 링크 → 신청 / B: 스레드 링크 → 신청 / C: 유튜브 링크 → 신청 없이 이탈
//   A: 인스타그램 링크로 재방문 → 필수 항목을 비우고 제출(래퍼 검증 오류)
const AREA = '2. 방문자 흐름'

test.describe.configure({ mode: 'serial' })

let state: SharedState
const visitors: Record<'A' | 'B' | 'C', { context: BrowserContext; page: Page }> = {} as never

test.beforeAll(async ({ browser }) => {
  state = readState()
  for (const name of ['A', 'B', 'C'] as const) {
    const context = await browser.newContext()
    visitors[name] = { context, page: await context.newPage() }
  }
})

test.afterAll(async () => {
  for (const v of Object.values(visitors)) await v.context.close()
})

async function visitorId(context: BrowserContext): Promise<string | undefined> {
  return (await context.cookies(PUBLIC_URL)).find((c) => c.name === 'lm_vid')?.value
}

/** 배포 링크를 열고 302 → /f/{slug}?lm={code} → 래퍼가 sandbox iframe을 그리는 것까지 확인한다. */
async function openLink(page: Page, channel: string) {
  const link = state.links![channel]
  const code = link.split('/l/')[1]
  const response = await page.goto(link)
  expect(response, '최종 응답').not.toBeNull()
  const redirect = await response!.request().redirectedFrom()?.response()
  expect(redirect?.status(), '/l/{code} 응답 상태').toBe(302)
  expect(page.url()).toBe(`${PUBLIC_URL}/f/${state.formSlug}?lm=${code}`)
  expect(response!.status()).toBe(200)
  await expect(page.locator('#lm-title')).toHaveText(state.formTitle!)
  const frameEl = page.locator('#lm-frame')
  await expect(frameEl).toBeVisible()
  const frame = frameEl.contentFrame()
  await expect(frame.getByRole('heading', { name: /마케팅 자동화 가이드/ })).toBeVisible()
  return { frame, redirectStatus: redirect!.status(), finalUrl: page.url() }
}

interface Applicant {
  name: string
  phone: string
  email: string
  size: string
  interests: string[]
  marketing: boolean
}

async function fillAndSubmit(page: Page, frame: ReturnType<Page['frameLocator']>, a: Applicant) {
  await frame.getByLabel('이름').fill(a.name)
  await frame.getByLabel('휴대폰 번호').pressSequentially(a.phone.replace(/-/g, ''))
  // 업로드 HTML의 인라인 스크립트(자동 하이픈)가 sandbox 안에서 실제로 돈다.
  await expect(frame.getByLabel('휴대폰 번호')).toHaveValue(a.phone)
  await frame.getByLabel('이메일').fill(a.email)
  await frame.getByLabel('회사 규모').selectOption(a.size)
  for (const label of a.interests) await frame.getByRole('checkbox', { name: label }).check()
  await expect(frame.getByText(`(${a.interests.length}개 선택)`)).toBeVisible()
  if (a.marketing) await frame.getByRole('checkbox', { name: /이벤트·혜택 소식 받기/ }).check()
  await page.getByRole('checkbox', { name: /가이드 발송과 상담 안내/ }).check()
  await frame.getByRole('button', { name: '무료 가이드 받기' }).click()
  await expect(page.locator('#lm-result')).toHaveText('신청이 접수되었습니다. 감사합니다.')
  // 성공하면 래퍼가 lm:result로 알리고 브리지가 폼을 비운다.
  await expect(frame.getByLabel('이름')).toHaveValue('')
}

test('방문자 A: 인스타그램 링크로 방문해 신청', async () => {
  const { page, context } = visitors.A
  let frame!: Awaited<ReturnType<typeof openLink>>['frame']
  await step(
    {
      area: AREA,
      name: '방문자 A 인스타그램 링크 방문',
      assertion: '/l/{code}가 302로 /f/{slug}?lm={code}에 도착하고 래퍼가 sandbox iframe에 폼을 그린다',
      screenshot: '07-visitor-a-form',
      page,
      fullPage: true,
    },
    async (record) => {
      const opened = await openLink(page, 'instagram')
      frame = opened.frame
      record({ redirectStatus: opened.redirectStatus, finalUrl: opened.finalUrl, lm_vid: await visitorId(context) })
    },
  )
  await step(
    {
      area: AREA,
      name: '방문자 A 신청 성공',
      assertion: 'iframe 안 폼을 채우고 래퍼 동의 체크 후 제출하면 "신청이 접수되었습니다. 감사합니다."가 보인다',
      screenshot: '08-visitor-a-success',
      page,
      fullPage: true,
    },
    async () => {
      await fillAndSubmit(page, frame, {
        name: '이현우',
        phone: '010-1234-5678',
        email: 'hyunwoo@example.com',
        size: 'small',
        interests: ['자동화'],
        marketing: true,
      })
    },
  )
})

test('방문자 B: 스레드 링크로 방문해 신청', async () => {
  const { page, context } = visitors.B
  await step(
    {
      area: AREA,
      name: '방문자 B 스레드 링크 방문·신청',
      assertion: '스레드 링크로 방문해 신청하면 성공 메시지가 보인다',
      screenshot: '09-visitor-b-success',
      page,
      fullPage: true,
    },
    async (record) => {
      const { frame } = await openLink(page, 'threads')
      await fillAndSubmit(page, frame, {
        name: '박서연',
        phone: '010-9876-5432',
        email: 'seoyeon@example.com',
        size: 'solo',
        interests: ['CRM 운영', '광고 최적화'],
        marketing: false,
      })
      record({ lm_vid: await visitorId(context) })
    },
  )
})

test('방문자 C: 유튜브 링크로 방문만 하고 이탈', async () => {
  const { page, context } = visitors.C
  await step(
    { area: AREA, name: '방문자 C 유튜브 링크 방문(미신청)', assertion: '유튜브 링크로 폼이 열리고 아무 것도 제출하지 않는다' },
    async (record) => {
      await openLink(page, 'youtube')
      await expect(page.locator('#lm-result')).toBeEmpty()
      record({ lm_vid: await visitorId(context) })
    },
  )
})

test('방문자 A: 재방문과 필수 항목 누락 제출', async () => {
  const { page, context } = visitors.A
  await step(
    {
      area: AREA,
      name: '방문자 A 재방문·필수 항목 누락',
      assertion: '같은 lm_vid로 재방문하고, 이름을 비워 제출하면 래퍼가 "입력 내용을 확인해 주세요."와 이름 항목 오류를 보여 준다',
      screenshot: '10-validation-error',
      page,
      fullPage: true,
    },
    async (record) => {
      const before = await visitorId(context)
      const { frame } = await openLink(page, 'instagram')
      expect(await visitorId(context), '재방문은 같은 방문자 쿠키를 쓴다').toBe(before)
      await frame.getByLabel('휴대폰 번호').pressSequentially('01055556666')
      await frame.getByLabel('회사 규모').selectOption('mid')
      await page.getByRole('checkbox', { name: /가이드 발송과 상담 안내/ }).check()
      await frame.getByRole('button', { name: '무료 가이드 받기' }).click()
      const result = page.locator('#lm-result')
      await expect(result).toHaveClass(/error/)
      await expect(result.locator('p')).toHaveText('입력 내용을 확인해 주세요.')
      await expect(result.locator('li')).toHaveText([/^이름\*? 항목을 입력해 주세요\.$/])
      record({ lm_vid: before, message: await result.innerText() })
    },
  )
})
