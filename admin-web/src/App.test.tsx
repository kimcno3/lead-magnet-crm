import { render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './App'

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('App', () => {
  it('백엔드 헬스체크가 UP이면 정상으로 표시한다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ status: 'UP' }))))
    render(<App />)
    expect(await screen.findByText('정상')).toBeInTheDocument()
  })

  it('백엔드에 연결하지 못하면 연결 실패로 표시한다', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('network')))
    render(<App />)
    expect(await screen.findByText('연결 실패')).toBeInTheDocument()
  })
})
