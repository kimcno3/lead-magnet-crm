import { useState } from 'react'
import { NavLink, Outlet } from 'react-router'
import { useI18n } from '../i18n/context'
import { readSidebarCollapsed, saveSidebarCollapsed } from '../preferences'

/** 톱니바퀴 아이콘(설정). currentColor로 테마 색을 따른다. */
function GearIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">
      <circle cx="12" cy="12" r="3" />
      <path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z" />
    </svg>
  )
}

export function Layout() {
  const { t } = useI18n()
  const [collapsed, setCollapsed] = useState(readSidebarCollapsed)

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
        </div>
        {/* 설정은 사이드바를 접어도 닿도록 본문 영역 밖, 맨 아래에 둔다 */}
        <div className="sidebar-foot">
          <NavLink to="/settings" className="settings-link" aria-label={t('nav.settings')} title={t('nav.settings')}>
            <GearIcon />
            <span className="settings-text">{t('nav.settings')}</span>
          </NavLink>
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
