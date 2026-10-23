import type { ReactNode } from 'react'

/** 좁은 화면에서 가로 스크롤되는 표 래퍼. 스크롤 영역도 키보드로 닿게 tabIndex를 준다. */
export function TableWrap({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="table-wrap" role="region" aria-label={label} tabIndex={0}>
      {children}
    </div>
  )
}

/** 로딩 중 표 모양을 미리 잡아 두는 행. */
export function SkeletonRows({ columns, rows = 4 }: { columns: number; rows?: number }) {
  return (
    <>
      {Array.from({ length: rows }, (_, r) => (
        <tr key={r} className="skeleton-row" aria-hidden="true">
          {Array.from({ length: columns }, (_, c) => (
            <td key={c}>
              <span className="skeleton" />
            </td>
          ))}
        </tr>
      ))}
    </>
  )
}

export function PageHeader({ title, actions, children }: { title: ReactNode; actions?: ReactNode; children?: ReactNode }) {
  return (
    <header className="page-head">
      <div className="page-title">
        <h1>{title}</h1>
        {children}
      </div>
      {actions && <div className="page-actions">{actions}</div>}
    </header>
  )
}
