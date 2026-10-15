import type { ReactNode } from 'react'
import type { PurchaseStatus } from '../api/types'
import { formatInr } from '../utils/money'

export function Loading({ label = 'Loading…' }: { label?: string }) {
  return <p className="state-message" role="status">{label}</p>
}

export function ErrorMessage({ children }: { children: ReactNode }) {
  return <p className="error-message" role="alert">{children}</p>
}

export function EmptyState({ children }: { children: ReactNode }) {
  return <p className="empty-state">{children}</p>
}

export function MoneyValue({ amount }: { amount: string | number }) {
  return <span>{formatInr(amount)}</span>
}

export function StatusBadge({ status }: { status: PurchaseStatus | string }) {
  return <span className={`status-badge status-${status.toLowerCase()}`}>{status.replaceAll('_', ' ')}</span>
}

export function SectionHeading({
  eyebrow,
  title,
  children,
}: {
  eyebrow: string
  title: string
  children?: ReactNode
}) {
  return (
    <div className="section-heading">
      <div>
        <p className="eyebrow">{eyebrow}</p>
        <h2>{title}</h2>
      </div>
      {children}
    </div>
  )
}
