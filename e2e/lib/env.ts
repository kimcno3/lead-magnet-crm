import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

export const E2E_DIR = resolve(dirname(fileURLToPath(import.meta.url)), '..')
export const REPO_DIR = resolve(E2E_DIR, '..')
export const SCREENSHOT_DIR = resolve(REPO_DIR, 'docs/e2e/screenshots')
export const RESULTS_FILE = resolve(REPO_DIR, 'docs/e2e/results.json')
export const STEPS_FILE = resolve(E2E_DIR, '.steps.jsonl')
export const STATE_FILE = resolve(E2E_DIR, '.state.json')
export const FIXTURES = resolve(E2E_DIR, 'fixtures')

export const ADMIN_URL = process.env.E2E_ADMIN_URL ?? 'http://localhost:5173'
export const PUBLIC_URL = process.env.E2E_PUBLIC_URL ?? 'http://localhost:8081'
export const BACKEND_ADMIN_URL = process.env.E2E_BACKEND_ADMIN_URL ?? 'http://localhost:8080'

/** 루트 .env를 읽는다. 자격 증명은 커밋된 파일에 두지 않고 실행 시점에만 읽는다. */
function readDotEnv(): Record<string, string> {
  let text: string
  try {
    text = readFileSync(resolve(REPO_DIR, '.env'), 'utf8')
  } catch {
    return {}
  }
  const values: Record<string, string> = {}
  for (const line of text.split(/\r?\n/)) {
    const m = /^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$/.exec(line)
    if (!m) continue
    values[m[1]] = m[2].replace(/^(['"])(.*)\1$/, '$2')
  }
  return values
}

export function adminCredentials(): { email: string; password: string } {
  const env = { ...readDotEnv(), ...process.env }
  const email = env.ADMIN_SEED_EMAIL
  const password = env.ADMIN_SEED_PASSWORD
  if (!email || !password) throw new Error('루트 .env에 ADMIN_SEED_EMAIL, ADMIN_SEED_PASSWORD가 있어야 합니다')
  return { email, password }
}
