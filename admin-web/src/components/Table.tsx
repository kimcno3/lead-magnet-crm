import type { ReactNode } from 'react'

/** 좁은 화면에서 가로 스크롤되는 표 래퍼. 스크롤 영역도 키보드로 닿게 tabIndex를 준다. */
export function TableWrap({ label, children, className }: { label: string; children: ReactNode; className?: string }) {
  return (
    <div className={className ? `table-wrap ${className}` : 'table-wrap'} role="region" aria-label={label} tabIndex={0}>
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
