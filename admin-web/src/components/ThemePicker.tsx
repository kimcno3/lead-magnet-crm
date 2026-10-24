import { useState } from 'react'
import { useI18n } from '../i18n/context'
import type { TKey } from '../i18n/translate'
import { readTheme, saveTheme, type ThemePreference } from '../preferences'

const THEME_OPTIONS: { value: ThemePreference; label: TKey }[] = [
  { value: 'system', label: 'layout.themeSystem' },
  { value: 'light', label: 'layout.themeLight' },
  { value: 'dark', label: 'layout.themeDark' },
]

/** 화면 테마 선택(시스템·라이트·다크). 고르면 바로 적용하고 이 브라우저에 기억한다. */
export function ThemePicker() {
  const { t } = useI18n()
  const [theme, setTheme] = useState(readTheme)
  return (
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
  )
}
