import { vi, type Mock } from 'vitest'

export function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json' },
  })
}

type Handler = (url: URL, init: RequestInit | undefined) => Response | Promise<Response> | undefined

/** 경로별 가짜 백엔드. 처리하지 않은 요청은 404 ProblemDetail. */
export function mockBackend(handler: Handler): Mock {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(String(input), 'http://admin.test')
    if (url.pathname === '/api/admin/auth/refresh') {
      const r = handler(url, init)
      return r ?? json({ accessToken: 'T', tokenType: 'Bearer', expiresIn: 900 })
    }
    if (url.pathname === '/api/admin/auth/me') {
      return handler(url, init) ?? json({ id: 1, email: 'ops@example.com' })
    }
    return (await handler(url, init)) ?? json({ code: 'not_found', detail: '없음' }, 404)
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

export function requestsTo(fetchMock: Mock, pathname: string): URL[] {
  return fetchMock.mock.calls
    .map((c) => new URL(String(c[0]), 'http://admin.test'))
    .filter((u) => u.pathname === pathname)
}
