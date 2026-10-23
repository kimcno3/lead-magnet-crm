import { useRef, type KeyboardEvent } from 'react'

export interface TabItem<K extends string> {
  key: K
  label: string
}

interface TabsProps<K extends string> {
  items: readonly TabItem<K>[]
  active: K
  onChange: (key: K) => void
  idPrefix: string
  label: string
}

/** WAI-ARIA tabs. 좌우 화살표·Home·End로 이동한다. 패널은 호출부가 `${idPrefix}-panel-${key}`로 렌더링한다. */
export function Tabs<K extends string>({ items, active, onChange, idPrefix, label }: TabsProps<K>) {
  const refs = useRef<(HTMLButtonElement | null)[]>([])

  function onKeyDown(e: KeyboardEvent, index: number) {
    let next = -1
    if (e.key === 'ArrowRight') next = (index + 1) % items.length
    else if (e.key === 'ArrowLeft') next = (index - 1 + items.length) % items.length
    else if (e.key === 'Home') next = 0
    else if (e.key === 'End') next = items.length - 1
    if (next < 0) return
    e.preventDefault()
    onChange(items[next].key)
    refs.current[next]?.focus()
  }

  return (
    <div className="tabs" role="tablist" aria-label={label}>
      {items.map((item, i) => (
        <button
          key={item.key}
          ref={(el) => {
            refs.current[i] = el
          }}
          type="button"
          role="tab"
          id={`${idPrefix}-tab-${item.key}`}
          aria-selected={item.key === active}
          aria-controls={`${idPrefix}-panel-${item.key}`}
          tabIndex={item.key === active ? 0 : -1}
          className="tab"
          onClick={() => onChange(item.key)}
          onKeyDown={(e) => onKeyDown(e, i)}
        >
          {item.label}
        </button>
      ))}
    </div>
  )
}
