import { useState } from 'react'
import { NavLink, Outlet } from 'react-router'
import { useAuth } from '../auth/context'
import {
  readSidebarCollapsed,
  readTheme,
  saveSidebarCollapsed,
  saveTheme,
  type ThemePreference,
} from '../preferences'
import { Button } from './Button'

const THEME_OPTIONS: { value: ThemePreference; label: string }[] = [
  { value: 'system', label: '시스템 설정' },
  { value: 'light', label: '라이트' },
  { value: 'dark', label: '다크' },
]

export function Layout() {
  const { operator, signOut } = useAuth()
  const [busy, setBusy] = useState(false)
  const [collapsed, setCollapsed] = useState(readSidebarCollapsed)
  const [theme, setTheme] = useState(readTheme)

  const toggleSidebar = () => {
    setCollapsed((prev) => {
      saveSidebarCollapsed(!prev)
      return !prev
    })
  }

  return (
    <div className={collapsed ? 'shell is-collapsed' : 'shell'}>
      <a className="skip-link" href="#main">
        본문으로 건너뛰기
      </a>
      <aside className="sidebar">
        <div className="sidebar-head">
          <p className="brand" translate="no">리드마그넷 CRM</p>
          <button
            type="button"
            className="sidebar-toggle"
            aria-expanded={!collapsed}
            aria-controls="sidebar-body"
            aria-label={collapsed ? '메뉴 펼치기' : '메뉴 접기'}
            title={collapsed ? '메뉴 펼치기' : '메뉴 접기'}
            onClick={toggleSidebar}
          >
            <span aria-hidden="true">{collapsed ? '»' : '«'}</span>
          </button>
        </div>
        <div id="sidebar-body" className="sidebar-body" hidden={collapsed}>
          <nav aria-label="주 메뉴">
            <ul className="nav">
              <li>
                <NavLink to="/campaigns">캠페인</NavLink>
              </li>
              <li>
                <NavLink to="/creators">크리에이터</NavLink>
              </li>
              <li>
                <NavLink to="/templates">템플릿</NavLink>
              </li>
              <li>
                <NavLink to="/audit-logs">감사 로그</NavLink>
              </li>
            </ul>
          </nav>
          <div className="sidebar-foot">
            <label className="theme-picker">
              <span>화면 테마</span>
              <select
                value={theme}
                onChange={(e) => {
                  const next = e.target.value as ThemePreference
                  setTheme(next)
                  saveTheme(next)
                }}
              >
                {THEME_OPTIONS.map((o) => (
                  <option key={o.value} value={o.value}>
                    {o.label}
                  </option>
                ))}
              </select>
            </label>
            <p className="operator" title={operator?.email} translate="no">
              {operator?.email}
            </p>
            <Button
              size="sm"
              variant="ghost"
              busy={busy}
              onClick={async () => {
                setBusy(true)
                try {
                  await signOut()
                } catch {
                  // 서버 로그아웃이 실패해도 signOut이 메모리 세션을 지웠다. 화면은 로그인으로 넘어간다.
                } finally {
                  setBusy(false)
                }
              }}
            >
              로그아웃
            </Button>
          </div>
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
