import { appendFileSync, existsSync, readFileSync, writeFileSync } from 'node:fs'
import { relative, resolve } from 'node:path'
import { test, type Page } from '@playwright/test'
import { REPO_DIR, SCREENSHOT_DIR, STATE_FILE, STEPS_FILE } from './env'

export interface StepRecord {
  id: number
  area: string
  name: string
  status: 'pass' | 'fail'
  assertion: string
  screenshot: string | null
  durationMs: number
  observed?: unknown
  error?: string
}

let counter = 0
function nextId(): number {
  // 스펙 파일이 바뀌어도 번호가 이어지도록 기록 파일의 줄 수에서 센다.
  counter = existsSync(STEPS_FILE) ? readFileSync(STEPS_FILE, 'utf8').split('\n').filter(Boolean).length + 1 : 1
  return counter
}

interface StepOptions {
  area: string
  name: string
  assertion: string
  /** 'NN-name' 형식. 성공·실패 모두 이 이름으로 찍는다. */
  screenshot?: string
  page?: Page
  fullPage?: boolean
}

/** 시나리오 한 단계. 실행 결과를 docs/e2e/results.json용으로 기록하고 화면을 남긴다. */
export async function step<T>(opts: StepOptions, body: (record: (observed: unknown) => void) => Promise<T>): Promise<T> {
  const id = nextId()
  const started = Date.now()
  let observed: unknown
  const write = (status: 'pass' | 'fail', error?: string, shot?: string | null) => {
    const rec: StepRecord = {
      id,
      area: opts.area,
      name: opts.name,
      status,
      assertion: opts.assertion,
      screenshot: shot ?? null,
      durationMs: Date.now() - started,
      ...(observed !== undefined ? { observed } : {}),
      ...(error ? { error } : {}),
    }
    appendFileSync(STEPS_FILE, JSON.stringify(rec) + '\n')
  }
  const shoot = async (): Promise<string | null> => {
    if (!opts.screenshot || !opts.page) return null
    const file = resolve(SCREENSHOT_DIR, `${opts.screenshot}.png`)
    try {
      await opts.page.screenshot({ path: file, fullPage: opts.fullPage ?? false, animations: 'disabled', timeout: 10_000 })
      return relative(REPO_DIR, file)
    } catch {
      return null
    }
  }
  return test.step(`${opts.area} › ${opts.name}`, async () => {
    try {
      const result = await body((o) => {
        observed = o
      })
      write('pass', undefined, await shoot())
      return result
    } catch (e) {
      write('fail', e instanceof Error ? e.message.split('\n').slice(0, 6).join('\n') : String(e), await shoot())
      throw e
    }
  })
}

/** 스펙 파일 사이에 넘기는 값(slug, 링크 URL 등). */
export interface SharedState {
  runId: string
  campaignId?: number
  campaignName?: string
  templateName?: string
  formSlug?: string
  formTitle?: string
  links?: Record<string, string>
}

export function readState(): SharedState {
  if (!existsSync(STATE_FILE)) throw new Error('앞 단계(01-operator)의 상태가 없습니다. 전체 스위트를 순서대로 실행하세요')
  return JSON.parse(readFileSync(STATE_FILE, 'utf8'))
}

export function writeState(patch: Partial<SharedState>): SharedState {
  const current: SharedState = existsSync(STATE_FILE) ? JSON.parse(readFileSync(STATE_FILE, 'utf8')) : { runId: Date.now().toString(36) }
  const next = { ...current, ...patch }
  writeFileSync(STATE_FILE, JSON.stringify(next, null, 2))
  return next
}
