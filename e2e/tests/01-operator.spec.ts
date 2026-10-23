import { resolve } from 'node:path'
import { expect, test, type Page } from '@playwright/test'
import { login, openNav } from '../lib/admin'
import { ADMIN_URL, FIXTURES, PUBLIC_URL } from '../lib/env'
import { step, writeState } from '../lib/steps'

// 시나리오 1. 운영자 흐름: 로그인 → 템플릿 업로드 → 캠페인 → 폼 생성·게시 → 채널 링크 4개.
const AREA = '1. 운영자 흐름'
const CHANNEL_LABELS = { instagram: '인스타그램', x: 'X', youtube: '유튜브', threads: '스레드' } as const

test.describe.configure({ mode: 'serial' })

let page: Page
const templateName = '마케팅 자동화 가이드 신청 폼'
const formTitle = '마케팅 자동화 무료 가이드 신청'
let campaignName = ''
let formSlug = ''
let templateId = ''

test.beforeAll(async ({ browser }) => {
  // 실행마다 새 id. 같은 DB에서 다시 돌려도 캠페인 이름·slug가 겹치지 않는다.
  const { runId } = writeState({ runId: Date.now().toString(36) })
  campaignName = `인스타 자동화 가이드 캠페인 ${runId}`
  formSlug = `guide-${runId}`
  page = await (await browser.newContext()).newPage()
})

test.afterAll(async () => {
  await page.context().close()
})

test('로그인 화면과 로그인', async () => {
  await step(
    { area: AREA, name: '로그인 화면', assertion: '로그인 폼(이메일·비밀번호·로그인 버튼)이 보인다', screenshot: '01-login', page },
    async () => {
      await page.goto(`${ADMIN_URL}/login`)
      await expect(page.getByRole('heading', { name: '운영자 로그인' })).toBeVisible()
      await expect(page.getByLabel('이메일')).toBeVisible()
      await expect(page.getByLabel('비밀번호')).toBeVisible()
      await expect(page.getByRole('button', { name: '로그인' })).toBeEnabled()
    },
  )
  await step({ area: AREA, name: '로그인', assertion: '.env 시드 계정으로 로그인하면 /campaigns로 이동한다' }, async () => {
    await login(page)
  })
})

test('템플릿 업로드', async () => {
  await step(
    {
      area: AREA,
      name: '템플릿 상세(추출 필드·미리보기)',
      assertion: 'ai-form.html 업로드 후 상세 화면에 필드 7개가 추출되고 sandbox 미리보기가 렌더링된다',
      screenshot: '02-template-detail',
      page,
      fullPage: true,
    },
    async (record) => {
      await openNav(page, '템플릿')
      await page.getByRole('button', { name: 'HTML 업로드' }).click()
      const dialog = page.getByRole('dialog', { name: 'HTML 템플릿 업로드' })
      await dialog.getByLabel('HTML 파일').setInputFiles(resolve(FIXTURES, 'ai-form.html'))
      await expect(dialog.getByText('선택한 파일:')).toContainText('ai-form.html')
      await dialog.getByLabel('이름').fill(templateName)
      await dialog.getByRole('button', { name: '업로드' }).click()
      await expect(page).toHaveURL(/\/templates\/\d+$/)
      templateId = /\/templates\/(\d+)$/.exec(page.url())![1]
      await expect(page.getByRole('heading', { name: templateName, level: 1 })).toBeVisible()

      const table = page.getByRole('table', { name: '추출된 필드' })
      const rows = table.locator('tbody tr')
      await expect(rows).toHaveCount(7)
      await expect(rows.nth(0)).toContainText('name')
      await expect(rows.filter({ hasText: 'phone' })).toContainText('필수')
      await expect(rows.filter({ hasText: 'company_size' })).toContainText('select')
      await expect(rows.filter({ hasText: 'interests' })).toContainText('crm, automation, ads')
      // value가 없는 체크박스는 브라우저 기본값 "on"을 선택지로 갖는다.
      await expect(rows.filter({ hasText: 'marketing_agree' })).toContainText('on')

      const preview = page.locator(`iframe[title="${templateName} 미리보기"]`)
      await expect(preview).toHaveAttribute('sandbox', 'allow-scripts allow-forms')
      await expect(preview.contentFrame().getByRole('heading', { name: /마케팅 자동화 가이드/ })).toBeVisible()
      record({ templateId, fields: await rows.locator('td:first-child').allTextContents() })
    },
  )
  await step(
    { area: AREA, name: '템플릿 목록', assertion: '목록에 업로드한 템플릿이 필드 수 7로 보인다', screenshot: '03-template-list', page },
    async () => {
      await openNav(page, '템플릿')
      const row = page.getByRole('table', { name: '템플릿 목록' }).locator('tbody tr').filter({ hasText: templateName }).first()
      await expect(row).toBeVisible()
      await expect(row.locator('td').nth(2)).toHaveText('7')
    },
  )
})

test('캠페인 생성', async () => {
  await step(
    { area: AREA, name: '캠페인 목록', assertion: '새 캠페인을 만들면 목록에 진행 중 상태로 보인다', screenshot: '04-campaign-list', page },
    async (record) => {
      await openNav(page, '캠페인')
      await page.getByRole('button', { name: '새 캠페인' }).click()
      const dialog = page.getByRole('dialog', { name: '새 캠페인' })
      await dialog.getByLabel('이름').fill(campaignName)
      await dialog.getByLabel('설명').fill('인스타·스레드·유튜브 크리에이터 트래픽을 리드로 전환')
      await dialog.getByRole('button', { name: '만들기' }).click()
      await expect(page).toHaveURL(/\/campaigns\/\d+\?tab=forms$/)
      const campaignId = Number(/\/campaigns\/(\d+)/.exec(page.url())![1])
      writeState({ campaignId, campaignName })
      record({ campaignId })

      await openNav(page, '캠페인')
      const row = page.getByRole('table', { name: '캠페인 목록' }).locator('tbody tr').filter({ hasText: campaignName })
      await expect(row).toContainText('진행 중')
    },
  )
})

test('폼 생성과 게시', async () => {
  await step(
    {
      area: AREA,
      name: '폼 생성·게시',
      assertion: '템플릿으로 폼을 만들고 확인 대화상자에서 게시하면 상태가 게시됨이 된다',
      screenshot: '05-form-published',
      page,
    },
    async () => {
      await page.getByRole('table', { name: '캠페인 목록' }).getByRole('link', { name: campaignName }).click()
      await page.getByRole('tab', { name: '폼' }).click()
      await page.getByRole('button', { name: '새 폼' }).first().click()
      const dialog = page.getByRole('dialog', { name: '새 폼' })
      await expect(dialog.getByLabel('템플릿').locator(`option[value="${templateId}"]`)).toHaveCount(1)
      await dialog.getByLabel('템플릿').selectOption(templateId)
      await dialog.getByLabel('제목').fill(formTitle)
      await dialog.getByLabel('동의 문구').fill('가이드 발송과 상담 안내를 위해 이름·연락처·이메일을 수집하며, 신청일로부터 1년간 보관합니다.')
      await dialog.getByLabel('slug').fill(formSlug)
      await dialog.getByRole('button', { name: '만들기' }).click()
      await expect(dialog).toBeHidden()

      const status = page.getByRole('region', { name: '상태' })
      await expect(status).toContainText('초안')
      await status.getByRole('button', { name: '게시', exact: true }).click()
      const confirm = page.getByRole('dialog', { name: '폼 게시' })
      await expect(confirm).toContainText('게시하면 공개 URL로 신청을 받기 시작하고')
      await confirm.getByRole('button', { name: '게시하기' }).click()
      await expect(confirm).toBeHidden()
      await expect(status).toContainText('게시됨')
      await expect(page.getByRole('region', { name: '공개 URL' })).toContainText(`${PUBLIC_URL}/f/${formSlug}`)
      writeState({ formSlug, formTitle, templateName })
    },
  )
})

test('채널별 링크 4개', async () => {
  await step(
    {
      area: AREA,
      name: '채널 링크 생성',
      assertion: '인스타그램·X·유튜브·스레드 링크가 모두 http://localhost:8081/l/{code} 형식으로 표에 보인다',
      screenshot: '06-links',
      page,
    },
    async (record) => {
      const table = page.getByRole('table', { name: '채널별 배포 링크' })
      const makeButtons = table.getByRole('button', { name: '링크 만들기' })
      for (let remaining = 4; remaining > 0; remaining--) {
        await expect(makeButtons).toHaveCount(remaining)
        await makeButtons.first().click()
        await expect(makeButtons).toHaveCount(remaining - 1)
      }
      const links: Record<string, string> = {}
      for (const [channel, label] of Object.entries(CHANNEL_LABELS)) {
        const row = table.locator('tbody tr').filter({ has: page.getByRole('cell', { name: label, exact: true }) })
        const url = (await row.locator('code.url').textContent())?.trim() ?? ''
        expect(url).toMatch(new RegExp(`^${PUBLIC_URL.replace(/[.:/]/g, '\\$&')}/l/[A-Za-z0-9_-]+$`))
        await expect(row.locator('td').nth(2)).toContainText(channel)
        links[channel] = url
      }
      expect(new Set(Object.values(links)).size).toBe(4)
      writeState({ links })
      record(links)
      await table.scrollIntoViewIfNeeded()
    },
  )
})
