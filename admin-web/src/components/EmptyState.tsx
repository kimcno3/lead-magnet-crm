import type { ReactNode } from 'react'

export function EmptyState({ message, action }: { message: string; action?: ReactNode }) {
  return (
    <div className="empty">
      <p>{message}</p>
      {action}
    </div>
  )
}
