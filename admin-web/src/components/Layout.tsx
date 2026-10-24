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
import { useI18n } from '../i18n/context'
import { LanguagePicker } from '../i18n/LanguagePicker'
import type { TKey } from '../i18n/translate'
import { Button } from './Button'

const THEME_OPTIONS: { value: ThemePreference; label: TKey }[] = [
  { value: 'system', label: 'layout.themeSystem' },
  { value: 'light', label: 'layout.themeLight' },
  { value: 'dark', label: 'layout.themeDark' },
]

export function Layout() {
  const { operator, signOut } = useAuth()
  const { t } = useI18n()
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
        {t('layout.skipToContent')}
      </a>
      <aside className="sidebar">
        <div className="sidebar-head">
          <p className="brand" translate="no">{t('app.brand')}</p>
          <button
            type="button"
            className="sidebar-toggle"
            aria-expanded={!collapsed}
            aria-controls="sidebar-body"
            aria-label={collapsed ? t('layout.expandMenu') : t('layout.collapseMenu')}
            title={collapsed ? t('layout.expandMenu') : t('layout.collapseMenu')}
            onClick={toggleSidebar}
          >
            <span aria-hidden="true">{collapsed ? '»' : '«'}</span>
          </button>
        </div>
        <div id="sidebar-body" className="sidebar-body" hidden={collapsed}>
          <nav aria-label={t('nav.main')}>
            <ul className="nav">
              <li>
                <NavLink to="/campaigns">{t('nav.campaigns')}</NavLink>
              </li>
              <li>
                <NavLink to="/creators">{t('nav.creators')}</NavLink>
              </li>
              <li>
                <NavLink to="/templates">{t('nav.templates')}</NavLink>
              </li>
              <li>
                <NavLink to="/audit-logs">{t('nav.auditLogs')}</NavLink>
              </li>
            </ul>
          </nav>
          <div className="sidebar-foot">
            <label className="theme-picker">
              <span>{t('layout.theme')}</span>
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
                    {t(o.label)}
                  </option>
                ))}
              </select>
            </label>
            <LanguagePicker />
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
              {t('layout.signOut')}
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
