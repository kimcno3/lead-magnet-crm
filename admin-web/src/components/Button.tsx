import type { ButtonHTMLAttributes, Ref } from 'react'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger'
  size?: 'md' | 'sm'
  busy?: boolean
  ref?: Ref<HTMLButtonElement>
}

export function Button({ variant = 'secondary', size = 'md', busy = false, className, disabled, type, children, ...rest }: ButtonProps) {
  const classes = ['btn', `btn-${variant}`, size === 'sm' ? 'btn-sm' : '', className ?? ''].filter(Boolean).join(' ')
  return (
    <button
      type={type ?? 'button'}
      className={classes}
      disabled={disabled || busy}
      aria-busy={busy || undefined}
      {...rest}
    >
      {children}
    </button>
  )
}
