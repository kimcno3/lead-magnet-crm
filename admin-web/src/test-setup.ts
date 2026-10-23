import '@testing-library/jest-dom/vitest'
import { cleanup, configure } from '@testing-library/react'
import { afterEach } from 'vitest'

// 병렬 실행 부하에서 find*가 기본 1초를 넘는 일이 있어 넉넉히 둔다.
configure({ asyncUtilTimeout: 3000 })

afterEach(() => {
  cleanup()
})
