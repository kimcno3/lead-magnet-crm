import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest'
import {
  ApiError,
  apiDownload,
  saveDownload,
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

describe('토큰이 이미 바뀐 뒤의 401', () => {
  it('다른 요청이 refresh를 끝낸 뒤 옛 토큰의 401이 오면 refresh 없이 새 토큰으로 다시 보낸다', async () => {
    let releaseSlow: (r: Response) => void = () => {}
    let slowCalls = 0
    fetchMock.mockImplementation((input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      const auth = new Headers(init?.headers).get('Authorization')
      if (url === '/api/admin/auth/login') return Promise.resolve(token('A1'))
      if (url === '/api/admin/auth/refresh') return Promise.resolve(token('A2'))
      if (url === '/api/admin/slow' && auth === 'Bearer A1') {
        slowCalls += 1
        return new Promise<Response>((resolve) => (releaseSlow = resolve))
      }
      if (auth === 'Bearer A1') return Promise.resolve(json({}, 401))
      return Promise.resolve(json({ url, auth }))
    })

    await login('ops@example.com', 'pw-123456789')
    const slow = apiRequest('/api/admin/slow')
    await vi.waitFor(() => expect(slowCalls).toBe(1))
    // 빠른 요청이 401 → refresh(A2) → 재시도까지 끝낸다.
    await expect(apiRequest('/api/admin/fast')).resolves.toEqual({ url: '/api/admin/fast', auth: 'Bearer A2' })
    // 그 뒤 A1로 보냈던 느린 요청이 401로 돌아온다.
    releaseSlow(json({}, 401))

    await expect(slow).resolves.toEqual({ url: '/api/admin/slow', auth: 'Bearer A2' })
    expect(callsTo(fetchMock, '/api/admin/auth/refresh')).toHaveLength(1)
  })
})

describe('네트워크 오류', () => {
  it('401 뒤 refresh가 네트워크 오류면 세션을 끝내지 않고 오류를 그대로 던진다', async () => {
    const expired = vi.fn()
    const off = onSessionExpired(expired)
    fetchMock
      .mockResolvedValueOnce(token('A1'))
      .mockResolvedValueOnce(json({}, 401))
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))

    await login('ops@example.com', 'pw-123456789')
    const error = await apiRequest('/api/admin/campaigns').catch((e: unknown) => e)

    expect(error).toBeInstanceOf(TypeError)
    expect(error).not.toBeInstanceOf(ApiError)
    expect(hasSession()).toBe(true)
    expect(expired).not.toHaveBeenCalled()
    off()
  })

  it('세션 복원 중 네트워크 오류는 false(로그아웃)가 아니라 거절로 알린다', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'))
    await expect(restoreSession()).rejects.toBeInstanceOf(TypeError)
  })

  it('네트워크 오류 뒤 다시 부르면 refresh를 새로 시도한다', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch')).mockResolvedValueOnce(token('A9'))
    await expect(restoreSession()).rejects.toBeInstanceOf(TypeError)
    await expect(restoreSession()).resolves.toBe(true)
    expect(hasSession()).toBe(true)
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

describe('파일 내려받기', () => {
  function csv(body: string, disposition = 'attachment; filename="leads-campaign-7-20261024-101500.csv"') {
    return new Response(body, { status: 200, headers: { 'Content-Type': 'text/csv;charset=UTF-8', 'Content-Disposition': disposition } })
  }

  it('토큰과 Accept: text/csv를 붙여 Blob과 파일 이름을 돌려준다', async () => {
    fetchMock.mockResolvedValueOnce(token('A1')).mockResolvedValueOnce(csv('\uFEFF신청일\r\n'))
    await login('ops@example.com', 'pw-123456789')

    const file = await apiDownload('/api/admin/campaigns/7/leads.csv', { query: { reveal: 'true', channel: null } })

    const call = fetchMock.mock.calls[1]
    expect(String(call[0])).toBe('/api/admin/campaigns/7/leads.csv?reveal=true')
    expect(authHeader(call)).toBe('Bearer A1')
    expect(new Headers((call[1] as RequestInit).headers).get('Accept')).toContain('text/csv')
    expect(file.filename).toBe('leads-campaign-7-20261024-101500.csv')
    expect(await file.blob.text()).toContain('신청일')
  })

  it('401이면 refresh 후 다시 받는다', async () => {
    fetchMock
      .mockResolvedValueOnce(json({ code: 'unauthorized' }, 401))
      .mockResolvedValueOnce(token('A2'))
      .mockResolvedValueOnce(csv('x', 'attachment'))

    const file = await apiDownload('/api/admin/campaigns/7/leads.csv')

    expect(authHeader(callsTo(fetchMock, '/api/admin/campaigns/7/leads.csv')[1])).toBe('Bearer A2')
    expect(file.filename).toBeNull()
  })

  it('오류 응답은 ProblemDetail로 읽어 ApiError로 던진다', async () => {
    fetchMock.mockResolvedValueOnce(json({ code: 'internal_error', detail: '서버 오류가 발생했습니다' }, 500))

    await expect(apiDownload('/api/admin/campaigns/7/leads.csv')).rejects.toMatchObject({
      status: 500,
      code: 'internal_error',
    })
  })
})

describe('saveDownload', () => {
  it('object URL로 내려받게 하고 약 1초 뒤에 해제한다', () => {
    vi.useFakeTimers()
    const create = vi.fn(() => 'blob:csv')
    const revoke = vi.fn()
    vi.stubGlobal('URL', Object.assign(URL, { createObjectURL: create, revokeObjectURL: revoke }))
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    try {
      saveDownload({ blob: new Blob(['x']), filename: null }, 'fallback.csv')

      expect(click).toHaveBeenCalledTimes(1)
      expect((click.mock.contexts[0] as HTMLAnchorElement).download).toBe('fallback.csv')
      vi.advanceTimersByTime(999)
      expect(revoke).not.toHaveBeenCalled()
      vi.advanceTimersByTime(1)
      expect(revoke).toHaveBeenCalledWith('blob:csv')
    } finally {
      vi.useRealTimers()
    }
  })
})
