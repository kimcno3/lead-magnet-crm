// 관리자 API 클라이언트.
// 보안 불변식(CLAUDE.md, ADR 0003·0011):
// - access token은 이 모듈 변수에만 둔다. Web Storage·IndexedDB·쿠키에 쓰지 않는다.
// - refresh token은 HttpOnly 쿠키라 JS가 다루지 않는다. 같은 출처 요청이라 브라우저가 자동으로 싣는다.
// - 로그인·refresh·logout에는 Authorization을 붙이지 않는다(만료 토큰이 있으면 Resource Server가 401을 낸다).
// - API 401이면 refresh를 한 번만 하고(동시 401은 같은 refresh를 기다린다) 원 요청을 다시 보낸다.

import type { FieldError, ProblemDetail, TokenResponse } from './types'

const AUTH_BASE = '/api/admin/auth'

let accessToken: string | null = null
let refreshInFlight: Promise<boolean> | null = null
const expiredListeners = new Set<() => void>()

export class ApiError extends Error {
  readonly status: number
  readonly code: string | undefined
  readonly errors: FieldError[]

  constructor(status: number, problem: ProblemDetail) {
    super(problem.detail || problem.title || `요청을 처리하지 못했습니다 (HTTP ${status})`)
    this.name = 'ApiError'
    this.status = status
    this.code = problem.code
    this.errors = problem.errors ?? []
  }

  /** 필드 이름 → 메시지. 같은 필드에 여러 개면 첫 번째. */
  get fieldErrors(): Record<string, string> {
    const map: Record<string, string> = {}
    for (const e of this.errors) if (!(e.field in map)) map[e.field] = e.message
    return map
  }
}

export function hasSession(): boolean {
  return accessToken !== null
}

/** 메모리의 토큰을 버린다. 구독자에게 알리지 않는다. */
export function clearSession(): void {
  accessToken = null
  refreshInFlight = null
}

/** 세션이 끝났을 때(refresh 실패) 불린다. 반환값으로 구독을 끊는다. */
export function onSessionExpired(listener: () => void): () => void {
  expiredListeners.add(listener)
  return () => expiredListeners.delete(listener)
}

function expireSession() {
  accessToken = null
  for (const listener of expiredListeners) listener()
}

async function readProblem(res: Response): Promise<ProblemDetail> {
  const type = res.headers.get('Content-Type') ?? ''
  if (type.includes('json')) {
    try {
      return (await res.json()) as ProblemDetail
    } catch {
      return {}
    }
  }
  return {}
}

async function authPost(path: string, body?: unknown): Promise<Response> {
  return fetch(`${AUTH_BASE}${path}`, {
    method: 'POST',
    credentials: 'same-origin',
    headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  })
}

export async function login(email: string, password: string): Promise<void> {
  const res = await authPost('/login', { email, password })
  if (!res.ok) throw new ApiError(res.status, await readProblem(res))
  accessToken = ((await res.json()) as TokenResponse).accessToken
}

/**
 * refresh 쿠키로 새 access token을 받는다. 동시에 불려도 요청은 하나다.
 * - 서버가 거절하면(401 등) false: 세션이 끝났다.
 * - 서버에 닿지 못하면(fetch가 던짐) 거절(TypeError): 세션이 끝났는지 알 수 없으므로 토큰을 지우지 않는다.
 */
export function restoreSession(): Promise<boolean> {
  if (!refreshInFlight) {
    const attempt = (async () => {
      const res = await authPost('/refresh')
      if (!res.ok) {
        accessToken = null
        return false
      }
      accessToken = ((await res.json()) as TokenResponse).accessToken
      return true
    })()
    refreshInFlight = attempt
    const settle = () => {
      if (refreshInFlight === attempt) refreshInFlight = null
    }
    attempt.then(settle, settle)
  }
  return refreshInFlight
}

export async function logout(): Promise<void> {
  accessToken = null
  try {
    await authPost('/logout')
  } finally {
    accessToken = null
  }
}

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'PATCH' | 'PUT' | 'DELETE'
  json?: unknown
  formData?: FormData
  query?: Record<string, string | number | null | undefined>
  signal?: AbortSignal
}

function buildUrl(path: string, query?: RequestOptions['query']): string {
  if (!query) return path
  const params = new URLSearchParams()
  for (const [k, v] of Object.entries(query)) {
    if (v !== null && v !== undefined && v !== '') params.set(k, String(v))
  }
  const qs = params.toString()
  return qs ? `${path}?${qs}` : path
}

function send(url: string, options: RequestOptions, token: string | null): Promise<Response> {
  const headers = new Headers({ Accept: 'application/json' })
  if (token) headers.set('Authorization', `Bearer ${token}`)
  let body: BodyInit | undefined
  if (options.formData) {
    body = options.formData
  } else if (options.json !== undefined) {
    headers.set('Content-Type', 'application/json')
    body = JSON.stringify(options.json)
  }
  return fetch(url, {
    method: options.method ?? 'GET',
    headers,
    body,
    credentials: 'same-origin',
    signal: options.signal,
  })
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const url = buildUrl(path, options.query)
  const usedToken = accessToken
  let res = await send(url, options, usedToken)

  if (res.status === 401) {
    // 다른 요청이 이미 토큰을 바꿨으면 refresh 없이 새 토큰으로 다시 보낸다.
    // refresh가 네트워크 오류로 거절되면 그대로 던진다(세션을 끝내지 않는다).
    const refreshed = accessToken !== null && accessToken !== usedToken ? true : await restoreSession()
    if (!refreshed) {
      const problem = await readProblem(res)
      expireSession()
      throw new ApiError(401, problem)
    }
    res = await send(url, options, accessToken)
    if (res.status === 401) {
      const problem = await readProblem(res)
      expireSession()
      throw new ApiError(401, problem)
    }
  }

  if (!res.ok) throw new ApiError(res.status, await readProblem(res))
  if (res.status === 204) return undefined as T
  const type = res.headers.get('Content-Type') ?? ''
  return (type.includes('json') ? await res.json() : await res.text()) as T
}
