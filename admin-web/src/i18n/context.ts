import { createContext, useContext, type ReactNode } from 'react'
import { activeLang, template, translate, type Lang, type TKey, type TParams } from './translate'

export interface I18nValue {
  lang: Lang
  setLang: (lang: Lang) => void
  t: (key: TKey, params?: TParams) => string
  /** 자리표시에 React 노드(링크, 강조)를 넣는다. count를 주면 영어 단수 문구(`키_one`)를 고른다. */
  rich: (key: TKey, params: Record<string, ReactNode>, count?: number) => ReactNode[]
}

// 공급자 없이 렌더링해도(단위 테스트) 현재 언어(기본 한국어)로 동작한다.
export const I18nContext = createContext<I18nValue>({
  lang: activeLang(),
  setLang: () => {},
  t: (key, params) => translate(key, params),
  rich: (key, params, count) => richText(key, params, activeLang(), count),
})

export function useI18n(): I18nValue {
  return useContext(I18nContext)
}

export function richText(key: TKey, params: Record<string, ReactNode>, lang: Lang, count?: number): ReactNode[] {
  // count는 단수·복수 문구 고르기에만 쓴다. 자리표시는 params의 노드로 바꾼다.
  const text = template(key, count === undefined ? undefined : { count }, lang)
  // split에 캡처 그룹을 쓰면 홀수 자리에 자리표시 이름이 온다.
  return text.split(/\{(\w+)\}/).map((part, i) => (i % 2 === 1 && part in params ? params[part] : part))
}
