import type { ReactNode } from 'react'

/** 페이지 제목 줄. 제목은 왼쪽, 주요 행동 버튼은 오른쪽. */
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
