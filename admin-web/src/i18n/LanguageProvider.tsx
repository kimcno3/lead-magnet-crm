import { useCallback, useLayoutEffect, useMemo, useState, type ReactNode } from 'react'
import { readLang, saveLang } from '../preferences'
import { I18nContext, richText, type I18nValue } from './context'
import { setActiveLang, translate, type Lang } from './translate'

/**
 * 화면 언어(ko 기본, en). 선택은 localStorage lm.lang에 기억하고 <html lang>과 문서 제목을 맞춘다.
 * 모듈의 현재 언어(format.ts·errors.ts가 읽음)를 렌더 전에 바꿔 두어 자식이 같은 언어로 그려지게 한다.
 */
export function LanguageProvider({ children, initialLang }: { children: ReactNode; initialLang?: Lang }) {
  const [lang, setLangState] = useState<Lang>(() => {
    const initial = initialLang ?? readLang()
    setActiveLang(initial)
    return initial
  })

  useLayoutEffect(() => {
    setActiveLang(lang)
    document.documentElement.lang = lang
    document.title = translate('app.title', undefined, lang)
  }, [lang])

  const setLang = useCallback((next: Lang) => {
    saveLang(next)
    setActiveLang(next)
    setLangState(next)
  }, [])

  const value = useMemo<I18nValue>(
    () => ({
      lang,
      setLang,
      t: (key, params) => translate(key, params, lang),
      rich: (key, params, count) => richText(key, params, lang, count),
    }),
    [lang, setLang],
  )
  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>
}
