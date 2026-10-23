import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'

export interface AsyncState<T> {
  data: T | undefined
  error: unknown
  loading: boolean
  reload: () => void
}

/**
 * deps가 바뀌거나 reload를 부르면 다시 불러온다. 다시 불러오는 동안 이전 data는 남겨 두고(흐리게 표시),
 * 늦게 도착한 이전 요청의 응답은 버린다. loading은 상태가 아니라 "마지막 응답의 키 ≠ 현재 키"로 계산한다.
 */
export function useAsync<T>(load: () => Promise<T>, deps: readonly unknown[]): AsyncState<T> {
  const loadRef = useRef(load)
  useLayoutEffect(() => {
    loadRef.current = load
  })
  const [nonce, setNonce] = useState(0)
  const key = `${JSON.stringify(deps)}#${nonce}`
  const [state, setState] = useState<{ key: string | null; data: T | undefined; error: unknown }>({
    key: null,
    data: undefined,
    error: undefined,
  })

  useEffect(() => {
    let current = true
    loadRef.current().then(
      (data) => {
        if (current) setState({ key, data, error: undefined })
      },
      (error: unknown) => {
        if (current) setState((s) => ({ key, data: s.data, error }))
      },
    )
    return () => {
      current = false
    }
  }, [key])

  const reload = useCallback(() => setNonce((n) => n + 1), [])
  const settled = state.key === key
  return { data: state.data, error: settled ? state.error : undefined, loading: !settled, reload }
}

/**
 * 제출 실패 후 첫 오류 입력(aria-invalid="true")으로 포커스를 옮긴다.
 * 오류 상태와 같은 배치로 tick을 올리고, 커밋이 끝난 뒤 effect에서 찾으므로 렌더 순서와 경쟁하지 않는다.
 */
export function useFocusFirstInvalid() {
  const formRef = useRef<HTMLFormElement>(null)
  const [tick, setTick] = useState(0)
  useEffect(() => {
    if (tick > 0) formRef.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus()
  }, [tick])
  const focusFirstInvalid = useCallback(() => setTick((t) => t + 1), [])
  return { formRef, focusFirstInvalid }
}
