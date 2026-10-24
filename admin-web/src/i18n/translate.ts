// 사전 조회와 자리표시 치환. React 밖(format.ts, errors.ts, api/client.ts)에서도 쓰므로 현재 언어를 모듈에 둔다.
// 현재 언어는 LanguageProvider만 바꾼다(setActiveLang).
import { en } from './en'
import { ko, type Dict } from './ko'

export type Lang = 'ko' | 'en'
export const LANGS: readonly Lang[] = ['ko', 'en']

/** "nav.campaigns"처럼 사전의 문자열 끝까지 내려가는 점 경로. */
type Paths<T> = {
  [K in keyof T & string]: T[K] extends string ? K : `${K}.${Paths<T[K]>}`
}[keyof T & string]

export type TKey = Paths<Dict>
export type TParams = Record<string, string | number>

const DICTS: Record<Lang, Dict> = { ko, en }

let active: Lang = 'ko'

export function activeLang(): Lang {
  return active
}

export function setActiveLang(lang: Lang): void {
  active = lang
}

/** 날짜·숫자 포맷용 로케일. */
export function localeOf(lang: Lang = active): string {
  return lang === 'en' ? 'en-US' : 'ko-KR'
}

/** 점 경로로 사전을 내려간다. 감사 행동처럼 키 자체에 점이 있는 경우("lead.list")도 찾는다. */
function lookup(node: unknown, key: string): string | undefined {
  if (node === null || typeof node !== 'object') return undefined
  const record = node as Record<string, unknown>
  const direct = record[key]
  if (typeof direct === 'string') return direct
  const parts = key.split('.')
  for (let i = 1; i < parts.length; i++) {
    const found = lookup(record[parts.slice(0, i).join('.')], parts.slice(i).join('.'))
    if (found !== undefined) return found
  }
  return undefined
}

/**
 * 키의 문구를 돌려준다. params.count가 숫자 1이고 `키_one`이 있으면 그 문구를 쓴다(영어 단수).
 * 모르는 키는 키 자체를 돌려준다(화면에서 바로 드러나게).
 */
export function template(key: TKey, params?: TParams, lang: Lang = active): string {
  const dict = DICTS[lang]
  const one = params?.count === 1 ? lookup(dict, `${key}_one`) : undefined
  return one ?? lookup(dict, key) ?? key
}

/** 숫자 값은 그 언어의 자릿수 구분으로 쓴다(1,520). 구분 없이 쓸 값(보존 일수 등)은 문자열로 넘긴다. */
function paramText(value: string | number, lang: Lang): string {
  return typeof value === 'number' ? value.toLocaleString(localeOf(lang)) : value
}

export function translate(key: TKey, params?: TParams, lang: Lang = active): string {
  const text = template(key, params, lang)
  if (!params) return text
  return text.replace(/\{(\w+)\}/g, (match, name: string) => (name in params ? paramText(params[name], lang) : match))
}

/** React 밖에서 쓰는 짧은 이름. 현재 언어로 번역한다. */
export const t = (key: TKey, params?: TParams) => translate(key, params)
