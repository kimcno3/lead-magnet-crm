import { useI18n } from './context'
import { LANGS, type Lang } from './translate'

const NAMES: Record<Lang, string> = { ko: '한국어', en: 'English' }

/** 언어 선택. 라벨과 언어 이름은 어느 언어에서든 읽을 수 있게 두 언어를 함께 쓴다. */
export function LanguagePicker() {
  const { lang, setLang, t } = useI18n()
  return (
    <label className="theme-picker">
      <span>{t('layout.language')}</span>
      <select value={lang} onChange={(e) => setLang(e.target.value as Lang)}>
        {LANGS.map((l) => (
          <option key={l} value={l} lang={l}>
            {NAMES[l]}
          </option>
        ))}
      </select>
    </label>
  )
}
