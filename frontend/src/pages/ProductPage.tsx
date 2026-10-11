import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faArrowLeft, faCartPlus, faChartLine, faCodeCompare, faStore } from '@fortawesome/free-solid-svg-icons'
import { OfferList } from '../components/OfferList'
import { ChainBadge, ErrorMessage, Spinner } from '../components/ui'
import { useToast } from '../components/Toast'
import { useCatalog } from '../context/CatalogContext'
import { useList } from '../context/ListContext'
import { api } from '../lib/api'
import { formatDate, formatPrice, unitLabel } from '../lib/format'
import type { HistoryPoint, ProductDetail } from '../lib/types'

export function ProductPage() {
  const { id } = useParams()
  const { settlement, settlementName } = useCatalog()
  const { add } = useList()
  const toast = useToast()
  const navigate = useNavigate()
  const [product, setProduct] = useState<ProductDetail>()
  const [error, setError] = useState<string>()

  useEffect(() => {
    setError(undefined)
    api.product(Number(id), settlement).then(setProduct).catch((e) => setError(e.message))
  }, [id, settlement])

  if (error) return <ErrorMessage message={error} />
  if (!product) return <Spinner />

  const best = product.stores[0]
  const bestPrice = best ? (best.promoPrice ?? best.price) : undefined
  const keyword = suggestKeyword(product.name, product.categoryName)

  return (
    <div className="space-y-8">
      <button onClick={() => navigate(-1)} className="btn-ghost -ml-3">
        <FontAwesomeIcon icon={faArrowLeft} /> Назад
      </button>

      <section className="card p-6">
        <div className="flex flex-wrap items-start justify-between gap-6">
          <div className="min-w-0 flex-1">
            <div className="mb-2 flex flex-wrap items-center gap-2">
              <ChainBadge name={product.chainName} color={product.chainColor} />
              <Link
                to={`/search?category=${product.categoryId}`}
                className="rounded-lg bg-slate-100 px-2 py-1 text-xs font-semibold text-slate-600 hover:bg-brand-100"
              >
                {product.categoryName}
              </Link>
            </div>
            <h1 className="text-2xl font-extrabold break-words text-slate-900">{product.name}</h1>
            {product.packageUnit && product.packageUnit !== 'pcs' && (
              <p className="mt-1 text-sm text-slate-500">
                {product.packageQty
                  ? `Разфасовка: ${String(product.packageQty).replace('.', ',')} ${unitLabel(product.packageUnit)}`
                  : `Продава се на ${unitLabel(product.packageUnit)}`}
              </p>
            )}
          </div>
          {bestPrice !== undefined && (
            <div className="text-right">
              <div className="text-sm text-slate-500">Най-ниска цена в {settlementName}</div>
              <div className="price text-3xl font-extrabold text-brand-700">{formatPrice(bestPrice)}</div>
              {product.packageQty && product.packageUnit !== 'pcs' && (
                <div className="price text-sm text-slate-500">
                  {formatPrice(bestPrice / product.packageQty)} / {unitLabel(product.packageUnit)}
                </div>
              )}
            </div>
          )}
        </div>
        <div className="mt-5 flex flex-wrap gap-2">
          <button
            className="btn-primary"
            onClick={() => {
              add({ categoryId: product.categoryId, keyword: keyword || undefined })
              toast(`Добавено в списъка: ${[product.categoryName, keyword].filter(Boolean).join(' · ')}`)
            }}
          >
            <FontAwesomeIcon icon={faCartPlus} /> Добави в списъка
          </button>
          <span className="self-center text-xs text-slate-500">
            Ще се търси най-евтиното „{product.categoryName}“{keyword && <> с „{keyword}“</>} във всички вериги
          </span>
        </div>
      </section>

      {product.history.length > 1 && (
        <section className="card p-6">
          <h2 className="mb-4 flex items-center gap-2 font-bold text-slate-900">
            <FontAwesomeIcon icon={faChartLine} className="text-brand-600" /> Ценова история (най-ниска цена)
          </h2>
          <PriceChart points={product.history} />
        </section>
      )}

      <section>
        <h2 className="mb-3 flex items-center gap-2 text-lg font-bold text-slate-900">
          <FontAwesomeIcon icon={faCodeCompare} className="text-brand-600" /> Подобни продукти в други вериги
        </h2>
        {product.alternatives.length ? (
          <OfferList offers={product.alternatives} />
        ) : (
          <p className="text-sm text-slate-500">Няма подобни продукти в други вериги в {settlementName}.</p>
        )}
      </section>

      <section>
        <h2 className="mb-3 flex items-center gap-2 text-lg font-bold text-slate-900">
          <FontAwesomeIcon icon={faStore} className="text-brand-600" /> Цени по магазини в {settlementName}
        </h2>
        {product.stores.length === 0 ? (
          <p className="text-sm text-slate-500">Продуктът не се предлага в {settlementName}.</p>
        ) : (
          <ul className="card divide-y divide-slate-100">
            {product.stores.map((s) => (
              <li key={s.storeId} className="flex items-center justify-between gap-4 px-4 py-3 text-sm">
                <span className="min-w-0 break-words text-slate-700">{s.address}</span>
                <span className="text-right whitespace-nowrap">
                  {s.promoPrice ? (
                    <>
                      <span className="price font-bold text-red-600">{formatPrice(s.promoPrice)}</span>{' '}
                      <span className="price text-xs text-slate-400 line-through">{formatPrice(s.price)}</span>
                    </>
                  ) : (
                    <span className="price font-bold text-slate-900">{formatPrice(s.price)}</span>
                  )}
                </span>
              </li>
            ))}
          </ul>
        )}
        {best && <p className="mt-2 text-xs text-slate-400">Цени към {formatDate(best.priceDate)}</p>}
      </section>
    </div>
  )
}

const STOP_WORDS = new Set(['със', 'без', 'или', 'кутия', 'пакет', 'вакуум', 'опаковка'])

/**
 * Ключова дума за търсене на същия продукт във всички вериги: първите две
 * думи от името, които не са част от името на категорията (обикновено марката).
 */
function suggestKeyword(name: string, categoryName: string) {
  const words = (s: string) => s.toLowerCase().replace(/[^\p{L}\s]/gu, ' ').split(/\s+/).filter((w) => w.length > 2)
  const categoryWords = new Set(words(categoryName))
  const picked = words(name).filter((w) => !categoryWords.has(w) && !STOP_WORDS.has(w)).slice(0, 2)
  return picked.join(' ')
}

function PriceChart({ points }: { points: HistoryPoint[] }) {
  const w = 600
  const h = 160
  const pad = 24
  const values = points.map((p) => p.minPrice)
  const min = Math.min(...values)
  const max = Math.max(...values)
  const range = max - min
  const x = (i: number) => pad + (i / (points.length - 1)) * (w - pad * 2)
  // при непроменена цена линията е по средата
  const y = (v: number) => (range === 0 ? h / 2 : h - pad - ((v - min) / range) * (h - pad * 2))
  const path = points.map((p, i) => `${i ? 'L' : 'M'}${x(i)},${y(p.minPrice)}`).join(' ')

  return (
    <figure>
      {/* preserveAspectRatio="none" – графиката запълва цялата ширина; линията остава с еднаква дебелина */}
      <svg
        viewBox={`0 0 ${w} ${h}`}
        preserveAspectRatio="none"
        className="h-40 w-full"
        role="img"
        aria-label={`Графика на цената: от ${formatPrice(points[0].minPrice)} до ${formatPrice(points[points.length - 1].minPrice)}`}
      >
        <path d={`${path} L${x(points.length - 1)},${h - pad} L${x(0)},${h - pad} Z`} className="fill-brand-100" />
        <path
          d={path}
          className="fill-none stroke-brand-600"
          strokeWidth={2.5}
          strokeLinejoin="round"
          vectorEffect="non-scaling-stroke"
        />
        {points.map((p, i) => (
          <line
            key={p.date}
            x1={x(i)}
            x2={x(i)}
            y1={y(p.minPrice)}
            y2={y(p.minPrice)}
            className="stroke-brand-700"
            strokeWidth={7}
            strokeLinecap="round"
            vectorEffect="non-scaling-stroke"
          >
            <title>
              {formatDate(p.date)}: {formatPrice(p.minPrice)}
            </title>
          </line>
        ))}
      </svg>
      <figcaption className="mt-2 flex justify-between text-xs text-slate-500">
        <span>{formatDate(points[0].date)}</span>
        <span className="price">
          мин. {formatPrice(min)} · макс. {formatPrice(max)}
        </span>
        <span>{formatDate(points[points.length - 1].date)}</span>
      </figcaption>
    </figure>
  )
}
