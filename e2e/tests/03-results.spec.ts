import { expect, test, type Page } from '@playwright/test'
import { login, openNav } from '../lib/admin'
import { readState, step, type SharedState } from '../lib/steps'

// 시나리오 3. 관리자 화면의 결과. 시나리오 2에서 한 일로 손계산한 값과 정확히 비교한다.
//
// 방문 기록(/f/{slug} 200 = 1건, /l/{code}는 기록하지 않음)
//   | 순서 | 방문자 | 채널      | 신청 |
//   | 1    | A      | instagram | O    |
//   | 2    | B      | threads   | O    |
//   | 3    | C      | youtube   | X    |
//   | 4    | A      | instagram | X (필수 항목 누락으로 래퍼가 막음, 서버에 가지 않음) |
//
// 캠페인 전체
//   방문 visits        = 4 (1~4행)
//   방문자 visitors    = |{A, B, C}| = 3
//   신청 leads         = 2 (A, B)
//   전환 방문자        = |방문자 ∩ 신청자| = |{A, B}| = 2
//   전환율             = 2 / 3 = 0.6667 → "66.7%"
// 채널별(전환은 같은 채널 안의 교집합)
//   인스타그램 방문 2, 방문자 1(A), 신청 1, 전환율 1/1 = 100.0%
//   X          0, 0, 0, 0.0%
//   유튜브     방문 1, 방문자 1(C), 신청 0, 전환율 0/1 = 0.0%
//   스레드     방문 1, 방문자 1(B), 신청 1, 전환율 1/1 = 100.0%
//   직접 유입  0, 0, 0, 0.0%
const AREA = '3. 관리자 결과'

const EXPECTED_KPIS = { 방문: '4', 방문자: '3', 신청: '2', '전환 방문자': '2', 전환율: '66.7%' }
const EXPECTED_CHANNELS = [
  ['인스타그램', '2', '1', '1', '100.0%'],
  ['X', '0', '0', '0', '0.0%'],
  ['유튜브', '1', '1', '0', '0.0%'],
  ['스레드', '1', '1', '1', '100.0%'],
  ['직접 유입', '0', '0', '0', '0.0%'],
]

test.describe.configure({ mode: 'serial' })

let page: Page
let state: SharedState

test.beforeAll(async ({ browser }) => {
  state = readState()
  page = await (await browser.newContext()).newPage()
  await login(page)
})

test.afterAll(async () => {
  await page.context().close()
})

test('캠페인 성과 탭', async () => {
  await step(
    {
      area: AREA,
      name: '캠페인 핵심 지표',
      assertion: '방문 4, 방문자 3, 신청 2, 전환 방문자 2, 전환율 66.7%',
      page,
    },
    async (record) => {
      await openNav(page, '캠페인')
      await page.getByRole('table', { name: '캠페인 목록' }).getByRole('link', { name: state.campaignName! }).click()
      await expect(page.getByRole('tab', { name: '성과' })).toHaveAttribute('aria-selected', 'true')
      const kpis = page.getByRole('list', { name: '핵심 지표' })
      await expect(kpis).toHaveAttribute('aria-busy', 'false')
      const observed: Record<string, string> = {}
      for (const [label, value] of Object.entries(EXPECTED_KPIS)) {
        const item = kpis.getByRole('listitem').filter({ has: page.getByText(label, { exact: true }) })
        await expect(item.locator('.kpi-value')).toHaveText(value)
        observed[label] = (await item.locator('.kpi-value').textContent()) ?? ''
      }
      record(observed)
    },
  )
  await step(
    {
      area: AREA,
      name: '채널별 성과 표',
      assertion: '인스타그램 2/1/1/100.0%, X 0/0/0/0.0%, 유튜브 1/1/0/0.0%, 스레드 1/1/1/100.0%, 직접 유입 0/0/0/0.0%',
      screenshot: '11-stats',
      page,
      fullPage: true,
    },
    async (record) => {
      const rows = page.getByRole('table', { name: '채널별 성과' }).locator('tbody tr')
      await expect(rows).toHaveCount(EXPECTED_CHANNELS.length)
      for (const [i, expected] of EXPECTED_CHANNELS.entries()) {
        await expect(rows.nth(i).locator('td:not(.bar-col)')).toHaveText(expected)
      }
      record(await Promise.all((await rows.all()).map((r) => r.locator('td').allTextContents())))
    },
  )
})

test('명단 탭', async () => {
  await step(
    {
      area: AREA,
      name: '신청자 명단(가린 값)',
      assertion: '총 2명. 이*우 010-****-5678 h***@example.com 인스타그램, 박*연 010-****-5432 s***@example.com 스레드(명단은 가린 값만 보인다)',
      screenshot: '12-leads',
      page,
    },
    async (record) => {
      await page.getByRole('tab', { name: '명단' }).click()
      await expect(page.getByRole('group', { name: '명단 필터' })).toContainText('총 2명')
      const rows = page.getByRole('table', { name: '신청자 명단' }).locator('tbody tr')
      await expect(rows).toHaveCount(2)
      // 원문 보기 뒤에도 같은 줄을 가리키도록 이름이 아니라 채널로 줄을 찾는다.
      const a = rows.filter({ hasText: '인스타그램' })
      await expect(a.locator('td').nth(1)).toHaveText('이*우')
      await expect(a.locator('td').nth(2)).toHaveText('010-****-5678')
      await expect(a.locator('td').nth(3)).toHaveText('h***@example.com')
      await expect(a.locator('td').nth(5)).toHaveText(state.formTitle!)
      const b = rows.filter({ hasText: '스레드' })
      await expect(b.locator('td').nth(1)).toHaveText('박*연')
      await expect(b.locator('td').nth(2)).toHaveText('010-****-5432')
      await expect(b.locator('td').nth(3)).toHaveText('s***@example.com')
      record(await Promise.all((await rows.all()).map((r) => r.locator('td').allTextContents())))
    },
  )
  await step(
    {
      area: AREA,
      name: '원문 보기와 가리기',
      assertion: '이현우 줄의 원문 보기 → 이현우 010-1234-5678 hyunwoo@example.com, 박서연 줄은 가린 채, 가리기 → 다시 이*우',
      page,
    },
    async (record) => {
      const rows = page.getByRole('table', { name: '신청자 명단' }).locator('tbody tr')
      const a = rows.filter({ hasText: '인스타그램' })
      const b = rows.filter({ hasText: '스레드' })
      await a.getByRole('button', { name: '원문 보기' }).click()
      await expect(a.locator('td').nth(1)).toHaveText('이현우')
      await expect(a.locator('td').nth(2)).toHaveText('010-1234-5678')
      await expect(a.locator('td').nth(3)).toHaveText('hyunwoo@example.com')
      await expect(b.locator('td').nth(2)).toHaveText('010-****-5432')
      const revealed = await a.locator('td').allTextContents()
      await a.getByRole('button', { name: '가리기' }).click()
      await expect(a.locator('td').nth(1)).toHaveText('이*우')
      record({ revealed: revealed.slice(1, 4), hidden: await a.locator('td').nth(1).textContent() })
    },
  )
})
