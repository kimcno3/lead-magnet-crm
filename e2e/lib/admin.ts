import { expect, type Page } from '@playwright/test'
import { ADMIN_URL, adminCredentials } from './env'

/** 관리자 화면에 로그인한다. access token은 메모리에만 있으므로 페이지(컨텍스트)마다 한 번 한다. */
export async function login(page: Page): Promise<void> {
  const { email, password } = adminCredentials()
  await page.goto(`${ADMIN_URL}/login`)
  await page.getByLabel('이메일').fill(email)
  await page.getByLabel('비밀번호').fill(password)
  await page.getByRole('button', { name: '로그인' }).click()
  await expect(page).toHaveURL(/\/campaigns$/)
  await expect(page.getByRole('heading', { name: '캠페인', level: 1 })).toBeVisible()
}

/** 앱 안에서 이동한다(전체 새로고침 대신 메뉴 링크로 이동해 세션 복원을 거치지 않는다). */
export async function openNav(page: Page, name: '캠페인' | '템플릿'): Promise<void> {
  await page.getByRole('navigation', { name: '주 메뉴' }).getByRole('link', { name }).click()
  await expect(page.getByRole('heading', { name, level: 1 })).toBeVisible()
}

/** 템플릿 화면에서 HTML을 올리고 상세 화면으로 이동한 뒤 템플릿 id를 돌려준다. */
export async function uploadTemplate(page: Page, file: string, name: string): Promise<string> {
  await openNav(page, '템플릿')
  await page.getByRole('button', { name: 'HTML 업로드' }).click()
  const dialog = page.getByRole('dialog', { name: 'HTML 템플릿 업로드' })
  await dialog.getByLabel('HTML 파일').setInputFiles(file)
  await dialog.getByLabel('이름').fill(name)
  await dialog.getByRole('button', { name: '업로드' }).click()
  await expect(page).toHaveURL(/\/templates\/\d+$/)
  await expect(page.getByRole('heading', { name, level: 1 })).toBeVisible()
  return /\/templates\/(\d+)$/.exec(page.url())![1]
}

/** 캠페인을 만들고 폼을 만들어 게시한다. 게시 후 폼 탭에 머문다. */
export async function createPublishedForm(
  page: Page,
  opts: { campaignName: string; templateId: string; title: string; slug: string },
): Promise<void> {
  await openNav(page, '캠페인')
  await page.getByRole('button', { name: '새 캠페인' }).click()
  const campaignDialog = page.getByRole('dialog', { name: '새 캠페인' })
  await campaignDialog.getByLabel('이름').fill(opts.campaignName)
  await campaignDialog.getByRole('button', { name: '만들기' }).click()
  await expect(page).toHaveURL(/\/campaigns\/\d+\?tab=forms$/)
  await page.getByRole('button', { name: '새 폼' }).first().click()
  const formDialog = page.getByRole('dialog', { name: '새 폼' })
  await expect(formDialog.getByLabel('템플릿').locator(`option[value="${opts.templateId}"]`)).toHaveCount(1)
  await formDialog.getByLabel('템플릿').selectOption(opts.templateId)
  await formDialog.getByLabel('제목').fill(opts.title)
  await formDialog.getByLabel('동의 문구').fill('보안 점검용 폼입니다.')
  await formDialog.getByLabel('slug').fill(opts.slug)
  await formDialog.getByRole('button', { name: '만들기' }).click()
  await expect(formDialog).toBeHidden()
  const status = page.getByRole('region', { name: '상태' })
  await status.getByRole('button', { name: '폼 게시하기' }).click()
  await page.getByRole('dialog', { name: '폼 게시' }).getByRole('button', { name: '게시하기' }).click()
  await expect(status).toContainText('게시됨')
}
