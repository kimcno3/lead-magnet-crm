import { render } from '@testing-library/react'
import type { ReactElement } from 'react'
import { MemoryRouter, Route, Routes, type InitialEntry } from 'react-router'
import { AuthProvider } from '../auth/AuthProvider'
import { RequireAuth } from '../auth/RequireAuth'

/** 로그인된 상태(refresh 성공)로 한 화면을 렌더링한다. */
export function renderPage(path: string, element: ReactElement, entry: InitialEntry = path) {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={[entry]}>
        <Routes>
          <Route path={path} element={<RequireAuth>{element}</RequireAuth>} />
          <Route path="/login" element={<p>로그인 화면</p>} />
          <Route path="*" element={<p>다른 화면</p>} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  )
}
