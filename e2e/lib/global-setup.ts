import { mkdirSync, rmSync, writeFileSync } from 'node:fs'
import { ADMIN_URL, BACKEND_ADMIN_URL, SCREENSHOT_DIR, STATE_FILE, STEPS_FILE } from './env'

export default async function globalSetup() {
  rmSync(STEPS_FILE, { force: true })
  rmSync(STATE_FILE, { force: true })
  rmSync(SCREENSHOT_DIR, { recursive: true, force: true })
  mkdirSync(SCREENSHOT_DIR, { recursive: true })
  writeFileSync(STEPS_FILE, '')
  for (const url of [`${BACKEND_ADMIN_URL}/actuator/health`, `${ADMIN_URL}/`]) {
    const res = await fetch(url).catch(() => null)
    if (!res?.ok) throw new Error(`${url}에 닿지 못했습니다. 저장소 루트에서 docker compose up -d를 먼저 실행하세요`)
  }
  process.env.E2E_STARTED_AT = new Date().toISOString()
}
