import { useState } from 'react'
import { NavLink, Outlet } from 'react-router'
import { useAuth } from '../auth/context'
import { Button } from './Button'

export function Layout() {
  const { operator, signOut } = useAuth()
  const [busy, setBusy] = useState(false)

  return (
    <div className="shell">
      <a className="skip-link" href="#main">
        본문으로 건너뛰기
      </a>
      <aside className="sidebar">
        <p className="brand">리드마그넷 CRM</p>
        <nav aria-label="주 메뉴">
          <ul className="nav">
            <li>
              <NavLink to="/campaigns">캠페인</NavLink>
            </li>
            <li>
              <NavLink to="/templates">템플릿</NavLink>
            </li>
          </ul>
        </nav>
        <div className="sidebar-foot">
          <p className="operator" title={operator?.email}>
            {operator?.email}
          </p>
          <Button
            size="sm"
            variant="ghost"
            busy={busy}
            onClick={async () => {
              setBusy(true)
              await signOut()
            }}
          >
            로그아웃
          </Button>
        </div>
      </aside>
      <main id="main" className="main" tabIndex={-1}>
        <div className="content">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
