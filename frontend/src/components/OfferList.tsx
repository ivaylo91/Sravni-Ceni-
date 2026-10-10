import { Link } from 'react-router-dom'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faCartPlus, faStore, faTag } from '@fortawesome/free-solid-svg-icons'
import { ChainBadge } from './ui'
import { formatPrice, pluralize, unitLabel } from '../lib/format'
import type { ProductOffer } from '../lib/types'
import { useList } from '../context/ListContext'
import { useCatalog } from '../context/CatalogContext'
import { useToast } from './Toast'

interface Props {
  offers: ProductOffer[]
  /** Ключова дума, с която продуктът се добавя в списъка (обикновено търсеният текст). */
  keyword?: string
  /** Подчертава най-ниската цена в списъка. */
  highlightCheapest?: boolean
}

export function OfferList({ offers, keyword, highlightCheapest = true }: Props) {
  const { add } = useList()
  const { category } = useCatalog()
  const toast = useToast()
  const cheapest = Math.min(...offers.map((o) => o.price))

  const addToList = (o: ProductOffer) => {
    add({ categoryId: o.categoryId, keyword })
    const label = [category(o.categoryId)?.name, keyword].filter(Boolean).join(' · ')
    toast(`Добавено в списъка: ${label}`)
  }

  return (
    <ul className="card divide-y divide-slate-100 overflow-hidden">
      {offers.map((o) => {
        const best = highlightCheapest && o.price === cheapest
        return (
          <li key={o.productId} className={`flex items-center gap-3 px-4 py-3 sm:gap-4 ${best ? 'bg-brand-50/60' : ''}`}>
            <div className="min-w-0 flex-1">
              <div className="mb-1 flex flex-wrap items-center gap-2">
                <ChainBadge name={o.chainName} color={o.chainColor} size="sm" />
                {best && (
                  <span className="rounded-md bg-brand-600 px-1.5 py-0.5 text-[11px] font-bold tracking-wide text-white uppercase">
                    Най-евтино
                  </span>
                )}
                {o.promo && (
                  <span className="inline-flex items-center gap-1 rounded-md bg-red-100 px-1.5 py-0.5 text-[11px] font-bold text-red-700">
                    <FontAwesomeIcon icon={faTag} /> Промоция
                  </span>
                )}
              </div>
              <Link
                to={`/product/${o.productId}`}
                className="block font-semibold break-words text-slate-800 hover:text-brand-700 hover:underline"
              >
                {o.name}
              </Link>
              <div className="mt-0.5 flex flex-wrap gap-x-3 text-xs text-slate-500">
                <span>
                  <FontAwesomeIcon icon={faStore} className="mr-1" />
                  {pluralize(o.storeCount, 'магазин', 'магазина')}
                </span>
                {o.unitPrice !== undefined && (
                  <span className="price">
                    {formatPrice(o.unitPrice)} / {unitLabel(o.unit)}
                  </span>
                )}
              </div>
            </div>
            <div className="text-right">
              <div className={`price text-lg font-extrabold ${o.promo ? 'text-red-600' : 'text-slate-900'}`}>
                {formatPrice(o.price)}
              </div>
              {o.promo && o.regularPrice > o.price && (
                <div className="price text-xs text-slate-400 line-through">{formatPrice(o.regularPrice)}</div>
              )}
            </div>
            <button
              onClick={() => addToList(o)}
              className="btn-secondary h-10 w-10 shrink-0 p-0 text-brand-700"
              aria-label={`Добави ${o.name} в списъка`}
              title="Добави в списъка"
            >
              <FontAwesomeIcon icon={faCartPlus} />
            </button>
          </li>
        )
      })}
    </ul>
  )
}
