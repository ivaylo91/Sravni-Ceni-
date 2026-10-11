import { useEffect, useState } from 'react'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faMinus, faPlus } from '@fortawesome/free-solid-svg-icons'
import type { AmountUnit, ListItem } from '../lib/types'

const AMOUNT_STEP = 0.5

/**
 * Количество на ред от списъка: брой опаковки (бр.) или желано количество в кг/л.
 * В кг/л кошницата сравнява цената за нужния брой опаковки, а не на една опаковка.
 */
export function QuantityControl({ item, onChange }: { item: ListItem; onChange: (patch: Partial<ListItem>) => void }) {
  const unit = item.unit ?? 'pcs'
  const [text, setText] = useState(formatAmount(item.amount))

  useEffect(() => setText(formatAmount(item.amount)), [item.amount])

  const setAmount = (value: number) => {
    const clamped = Math.min(100, Math.max(0.05, Math.round(value * 100) / 100))
    onChange({ amount: clamped })
  }

  const step = (direction: 1 | -1) => {
    if (unit === 'pcs') onChange({ quantity: Math.min(99, Math.max(1, item.quantity + direction)) })
    else setAmount((item.amount ?? 1) + direction * AMOUNT_STEP)
  }

  const btn = 'h-8 w-8 text-slate-500 hover:text-slate-900 disabled:opacity-30'

  return (
    <div className="flex items-center gap-1.5">
      <div className="flex items-center rounded-xl border border-slate-200">
        <button
          className={btn}
          onClick={() => step(-1)}
          disabled={unit === 'pcs' ? item.quantity <= 1 : (item.amount ?? 1) <= AMOUNT_STEP}
          aria-label="Намали количеството"
        >
          <FontAwesomeIcon icon={faMinus} />
        </button>
        {unit === 'pcs' ? (
          <span className="price w-8 text-center text-sm font-bold">{item.quantity}</span>
        ) : (
          <input
            className="price w-12 bg-transparent text-center text-sm font-bold focus:outline-none"
            inputMode="decimal"
            value={text}
            aria-label="Количество"
            onChange={(e) => setText(e.target.value)}
            onBlur={() => {
              const value = Number(text.replace(',', '.'))
              if (Number.isFinite(value) && value > 0) setAmount(value)
              else setText(formatAmount(item.amount))
            }}
            onKeyDown={(e) => e.key === 'Enter' && (e.target as HTMLInputElement).blur()}
          />
        )}
        <button
          className={btn}
          onClick={() => step(1)}
          disabled={unit === 'pcs' ? item.quantity >= 99 : (item.amount ?? 1) >= 100}
          aria-label="Увеличи количеството"
        >
          <FontAwesomeIcon icon={faPlus} />
        </button>
      </div>
      <select
        value={unit}
        onChange={(e) => {
          const next = e.target.value as AmountUnit | 'pcs'
          onChange(next === 'pcs' ? { unit: undefined, amount: undefined } : { unit: next, amount: item.amount ?? 1 })
        }}
        className="h-8 rounded-lg border border-slate-200 bg-white px-1.5 text-xs font-semibold text-slate-600 focus:border-brand-500 focus:outline-none"
        aria-label="Мярка"
        title="Брой опаковки или количество в кг / л"
      >
        <option value="pcs">бр.</option>
        <option value="kg">кг</option>
        <option value="l">л</option>
      </select>
    </div>
  )
}

function formatAmount(amount?: number) {
  return amount === undefined ? '1' : String(amount).replace('.', ',')
}
