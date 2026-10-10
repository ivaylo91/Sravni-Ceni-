import type { ReactNode } from 'react'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faCircleExclamation, faSpinner, type IconDefinition } from '@fortawesome/free-solid-svg-icons'
import { textOn } from '../lib/format'

export function Spinner({ label = 'Зареждане…' }: { label?: string }) {
  return (
    <div className="flex items-center justify-center gap-3 py-12 text-slate-500" role="status">
      <FontAwesomeIcon icon={faSpinner} spin />
      <span>{label}</span>
    </div>
  )
}

export function ErrorMessage({ message }: { message: string }) {
  return (
    <div className="flex items-start gap-3 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800" role="alert">
      <FontAwesomeIcon icon={faCircleExclamation} className="mt-0.5" />
      <span>{message}</span>
    </div>
  )
}

export function EmptyState({ icon, title, children }: { icon: IconDefinition; title: string; children?: ReactNode }) {
  return (
    <div className="card flex flex-col items-center px-6 py-14 text-center">
      <div className="mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-brand-50 text-2xl text-brand-600">
        <FontAwesomeIcon icon={icon} />
      </div>
      <h2 className="text-lg font-bold text-slate-800">{title}</h2>
      {children && <div className="mt-2 max-w-md text-sm text-slate-500">{children}</div>}
    </div>
  )
}

export function ChainBadge({ name, color, size = 'md' }: { name: string; color: string | null; size?: 'sm' | 'md' }) {
  return (
    <span
      className={`inline-flex shrink-0 items-center rounded-lg font-bold whitespace-nowrap ${
        size === 'sm' ? 'px-2 py-0.5 text-xs' : 'px-2.5 py-1 text-sm'
      }`}
      style={{ backgroundColor: color ?? '#475569', color: textOn(color) }}
    >
      {name}
    </span>
  )
}

export function PageTitle({ title, subtitle, actions }: { title: string; subtitle?: ReactNode; actions?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 className="text-2xl font-extrabold tracking-tight text-slate-900 sm:text-3xl">{title}</h1>
        {subtitle && <p className="mt-1 text-slate-500">{subtitle}</p>}
      </div>
      {actions}
    </div>
  )
}
