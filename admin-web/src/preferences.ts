// 화면 설정(테마, 사이드바 접기, 언어)은 이 브라우저에만 기억한다. 토큰·개인정보가 아니므로 localStorage를 써도 된다
// (ADR 0011: 토큰만 메모리 보관). 저장소가 막힌 환경(사생활 보호 모드 등)에서는 기본값으로 동작한다.

import type { Lang } from './i18n/translate'

export type ThemePreference = 'system' | 'light' | 'dark'

const THEME_KEY = 'lm.theme'
const SIDEBAR_KEY = 'lm.sidebar-collapsed'
const LANG_KEY = 'lm.lang'

function read(key: string): string | null {
  try {
    return window.localStorage.getItem(key)
  } catch {
    return null
  }
}

function write(key: string, value: string): void {
  try {
    window.localStorage.setItem(key, value)
  } catch {
    // 저장하지 못해도 이번 화면에는 적용된다
  }
}

export function readTheme(): ThemePreference {
  const value = read(THEME_KEY)
  return value === 'light' || value === 'dark' ? value : 'system'
}

/** 테마를 문서에 적용한다. system이면 data-theme을 지워 OS 설정을 따른다. */
export function applyTheme(theme: ThemePreference): void {
  const root = document.documentElement
  if (theme === 'system') {
    root.removeAttribute('data-theme')
  } else {
    root.setAttribute('data-theme', theme)
  }
}

export function saveTheme(theme: ThemePreference): void {
  write(THEME_KEY, theme)
  applyTheme(theme)
}

export function readSidebarCollapsed(): boolean {
  return read(SIDEBAR_KEY) === '1'
}

export function saveSidebarCollapsed(collapsed: boolean): void {
  write(SIDEBAR_KEY, collapsed ? '1' : '0')
}

/**
 * 저장된 언어. 저장값이 없거나 모르는 값이면 한국어다. 제품이 한국어 우선이라 브라우저 언어(navigator.language)가
 * ko*가 아니어도 한국어로 시작하고, 운영자가 직접 고른 경우에만 영어가 된다.
 */
export function readLang(): Lang {
  return read(LANG_KEY) === 'en' ? 'en' : 'ko'
}

export function saveLang(lang: Lang): void {
  write(LANG_KEY, lang)
}
