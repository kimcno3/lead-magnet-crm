import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname } from 'node:path'
import { RESULTS_FILE, STEPS_FILE } from './env'
import type { StepRecord } from './steps'

export default async function globalTeardown() {
  const steps: StepRecord[] = readFileSync(STEPS_FILE, 'utf8')
    .split('\n')
    .filter(Boolean)
    .map((line) => JSON.parse(line))
  const summary = {
    suite: 'lead-magnet-crm browser E2E (Playwright, Chromium)',
    startedAt: process.env.E2E_STARTED_AT ?? null,
    finishedAt: new Date().toISOString(),
    total: steps.length,
    passed: steps.filter((s) => s.status === 'pass').length,
    failed: steps.filter((s) => s.status === 'fail').length,
    steps,
  }
  mkdirSync(dirname(RESULTS_FILE), { recursive: true })
  writeFileSync(RESULTS_FILE, JSON.stringify(summary, null, 2) + '\n')
}
