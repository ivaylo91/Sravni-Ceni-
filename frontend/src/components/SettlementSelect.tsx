import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faLocationDot } from '@fortawesome/free-solid-svg-icons'
import { useCatalog } from '../context/CatalogContext'

const TOP = 8

/** Избор на населено място – първо най-големите градове, после по азбучен ред. */
export function SettlementSelect({ className = '' }: { className?: string }) {
  const { settlements, settlement, setSettlement } = useCatalog()
  const top = settlements.slice(0, TOP)
  const rest = [...settlements.slice(TOP)].sort((a, b) => a.name.localeCompare(b.name, 'bg'))

  return (
    <label className={`relative flex items-center ${className}`}>
      <span className="sr-only">Населено място</span>
      <FontAwesomeIcon icon={faLocationDot} className="pointer-events-none absolute left-3 text-brand-600" />
      <select
        value={settlement}
        onChange={(e) => setSettlement(e.target.value)}
        className="w-full appearance-none rounded-xl border border-slate-300 bg-white py-2 pr-8 pl-9 text-sm font-semibold text-slate-700 focus:border-brand-500 focus:ring-2 focus:ring-brand-200 focus:outline-none"
      >
        {settlements.length === 0 && <option value={settlement}>Зареждане…</option>}
        <optgroup label="Най-големи градове">
          {top.map((s) => (
            <option key={s.code} value={s.code}>
              {s.name}
            </option>
          ))}
        </optgroup>
        <optgroup label="Всички населени места">
          {rest.map((s) => (
            <option key={s.code} value={s.code}>
              {s.name} ({s.storeCount})
            </option>
          ))}
        </optgroup>
      </select>
      <span className="pointer-events-none absolute right-3 text-xs text-slate-400">▼</span>
    </label>
  )
}
