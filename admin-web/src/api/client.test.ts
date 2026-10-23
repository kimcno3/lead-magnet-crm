import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest'
import {
  ApiError,
  apiRequest,
  clearSession,
  hasSession,
  login,
  logout,
  onSessionExpired,
  restoreSession,
} from './client'

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json' },
  })
}

function token(value: string) {
  return json({ accessToken: value, tokenType: 'Bearer', expiresIn: 900 })
}

function authHeader(call: unknown[]): string | null {
  const init = call[1] as RequestInit | undefined
  return new Headers(init?.headers).get('Authorization')
}

function callsTo(fetchMock: Mock, path: string) {
  return fetchMock.mock.calls.filter((c) => String(c[0]).startsWith(path))
}

let fetchMock: Mock

beforeEach(() => {
  clearSession()
  fetchMock = vi.fn()
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('토큰 보관', () => {
  it('로그인 후 받은 access token을 Authorization 헤더에 붙인다', async () => {
    fetchMock.mockResolvedValueOnce(token('A1')).mockResolvedValueOnce(json([]))

    await login('ops@example.com', 'pw-123456789')
    await apiRequest('/api/admin/campaigns')

    expect(authHeader(fetchMock.mock.calls[1])).toBe('Bearer A1')
    expect(hasSession()).toBe(true)
  })

  it('로그인·refresh·logout 요청에는 Authorization 헤더를 붙이지 않는다', async () => {
    fetchMock
      .mockResolvedValueOnce(token('A1'))
      .mockResolvedValueOnce(token('A2'))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))

    await login('ops@example.com', 'pw-123456789')
    await restoreSession()
    await logout()

    expect(fetchMock.mock.calls.map((c) => String(c[0]))).toEqual([
      '/api/admin/auth/login',
      '/api/admin/auth/refresh',
      '/api/admin/auth/logout',
    ])
    for (const call of fetchMock.mock.calls) expect(authHeader(call)).toBeNull()
    expect(hasSession()).toBe(false)
  })

  it('localStorage·sessionStorage에 아무것도 쓰지 않는다', async () => {
    const local = vi.spyOn(window.localStorage, 'setItem')
    const session = vi.spyOn(window.sessionStorage, 'setItem')
    const proto = vi.spyOn(Storage.prototype, 'setItem')
    fetchMock
      .mockResolvedValueOnce(token('A1'))
      .mockResolvedValueOnce(json({}, 401))
      .mockResolvedValueOnce(token('A2'))
      .mockResolvedValueOnce(json([]))

    await login('ops@example.com', 'pw-123456789')
    await apiRequest('/api/admin/campaigns')

    expect(local).not.toHaveBeenCalled()
    expect(session).not.toHaveBeenCalled()
    expect(proto).not.toHaveBeenCalled()
    expect(document.cookie).toBe('')
  })
})

describe('401 처리', () => {
  it('401이면 refresh를 한 번 하고 새 토큰으로 원 요청을 다시 보낸다', async () => {
    fetchMock
      .mockResolvedValueOnce(token('A1'))
      .mockResolvedValueOnce(json({ code: 'unauthorized' }, 401))
      .mockResolvedValueOnce(token('A2'))
      .mockResolvedValueOnce(json([{ id: 1 }]))

    await login('ops@example.com', 'pw-123456789')
    const result = await apiRequest<{ id: number }[]>('/api/admin/campaigns')

    expect(result).toEqual([{ id: 1 }])
    expect(callsTo(fetchMock, '/api/admin/auth/refresh')).toHaveLength(1)
    const retried = fetchMock.mock.calls[3]
    expect(String(retried[0])).toBe('/api/admin/campaigns')
    expect(authHeader(retried)).toBe('Bearer A2')
  })

  it('재시도한 요청이 또 401이면 refresh를 다시 하지 않고 세션을 끝낸다', async () => {
    const expired = vi.fn()
    const off = onSessionExpired(expired)
    fetchMock
      .mockResolvedValueOnce(token('A1'))
      .mockResolvedValueOnce(json({}, 401))
      .mockResolvedValueOnce(token('A2'))
      .mockResolvedValueOnce(json({ code: 'unauthorized', detail: '인증이 필요합니다' }, 401))

    await login('ops@example.com', 'pw-123456789')
    await expect(apiRequest('/api/admin/campaigns')).rejects.toBeInstanceOf(ApiError)

    expect(callsTo(fetchMock, '/api/admin/auth/refresh')).toHaveLength(1)
    expect(hasSession()).toBe(false)
    expect(expired).toHaveBeenCalledTimes(1)
    off()
  })

  it('동시에 난 401 두 개는 refresh 하나를 함께 기다린다', async () => {
    let releaseRefresh: (r: Response) => void = () => {}
    fetchMock.mockImplementation((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      const auth = new Headers(init?.headers).get('Authorization')
      if (url === '/api/admin/auth/login') return Promise.resolve(token('A1'))
      if (url === '/api/admin/auth/refresh') return new Promise<Response>((resolve) => (releaseRefresh = resolve))
      if (auth === 'Bearer A1') return Promise.resolve(json({}, 401))
      return Promise.resolve(json({ url }))
    })

    await login('ops@example.com', 'pw-123456789')
    const a = apiRequest('/api/admin/campaigns')
    const b = apiRequest('/api/admin/templates')
    await vi.waitFor(() => expect(callsTo(fetchMock, '/api/admin/auth/refresh')).toHaveLength(1))
    releaseRefresh(token('A2'))

    await expect(a).resolves.toEqual({ url: '/api/admin/campaigns' })
    await expect(b).resolves.toEqual({ url: '/api/admin/templates' })
    expect(callsTo(fetchMock, '/api/admin/auth/refresh')).toHaveLength(1)
  })

  it('refresh가 실패하면 로그아웃 상태가 되고 구독자에게 알린다', async () => {
    const expired = vi.fn()
    const off = onSessionExpired(expired)
    fetchMock
      .mockResolvedValueOnce(token('A1'))
      .mockResolvedValueOnce(json({}, 401))
      .mockResolvedValueOnce(json({ code: 'invalid_refresh_token' }, 401))

    await login('ops@example.com', 'pw-123456789')
    const error = await apiRequest('/api/admin/campaigns').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(401)
    expect(hasSession()).toBe(false)
    expect(expired).toHaveBeenCalledTimes(1)
    off()
  })

  it('세션 복원(refresh)이 실패하면 false를 돌려준다', async () => {
    fetchMock.mockResolvedValueOnce(json({ code: 'invalid_refresh_token' }, 401))
    await expect(restoreSession()).resolves.toBe(false)
    expect(hasSession()).toBe(false)
  })
})

describe('ProblemDetail', () => {
  it('detail, code, errors를 ApiError로 옮긴다', async () => {
    fetchMock.mockResolvedValueOnce(token('A1')).mockResolvedValueOnce(
      json(
        {
          status: 422,
          detail: '입력값이 올바르지 않습니다',
          code: 'validation_failed',
          errors: [{ field: 'name', message: 'name이 필요합니다' }],
        },
        422,
      ),
    )
    await login('ops@example.com', 'pw-123456789')
    const error = (await apiRequest('/api/admin/campaigns', { method: 'POST', json: {} }).catch(
      (e: unknown) => e,
    )) as ApiError

    expect(error.status).toBe(422)
    expect(error.code).toBe('validation_failed')
    expect(error.message).toBe('입력값이 올바르지 않습니다')
    expect(error.fieldErrors).toEqual({ name: 'name이 필요합니다' })
  })

  it('로그인 401은 refresh를 시도하지 않고 invalid_credentials로 끝난다', async () => {
    fetchMock.mockResolvedValueOnce(json({ code: 'invalid_credentials', detail: 'x' }, 401))
    const error = (await login('ops@example.com', 'wrong').catch((e: unknown) => e)) as ApiError
    expect(error.code).toBe('invalid_credentials')
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('JSON이 아닌 오류 응답도 상태 코드로 메시지를 만든다', async () => {
    fetchMock.mockResolvedValueOnce(token('A1')).mockResolvedValueOnce(new Response('<html>bad gateway</html>', { status: 502 }))
    await login('ops@example.com', 'pw-123456789')
    const error = (await apiRequest('/api/admin/campaigns').catch((e: unknown) => e)) as ApiError
    expect(error.status).toBe(502)
    expect(error.message).toContain('502')
  })
})
