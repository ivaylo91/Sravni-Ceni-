import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faCartPlus, faMagnifyingGlass } from '@fortawesome/free-solid-svg-icons'
import { SearchBox } from '../components/SearchBox'
import { OfferList } from '../components/OfferList'
import { EmptyState, ErrorMessage, PageTitle, Spinner } from '../components/ui'
import { useToast } from '../components/Toast'
import { useCatalog } from '../context/CatalogContext'
import { useList } from '../context/ListContext'
import { api, type SortOrder } from '../lib/api'
import type { Chain, ProductOffer } from '../lib/types'

const SORTS: [SortOrder, string][] = [
  ['PRICE', 'Най-ниска цена'],
  ['UNIT_PRICE', 'Цена за кг / л'],
  ['NAME', 'Име'],
]

export function SearchPage() {
  const [params, setParams] = useSearchParams()
  const q = params.get('q') ?? ''
  const categoryId = params.get('category') ? Number(params.get('category')) : undefined
  const chainId = params.get('chain') ? Number(params.get('chain')) : undefined
  const sort = (params.get('sort') as SortOrder) ?? 'PRICE'

  const { settlement, settlementName, groups, category } = useCatalog()
  const { add } = useList()
  const toast = useToast()
  const [offers, setOffers] = useState<ProductOffer[]>([])
  const [chains, setChains] = useState<Chain[]>([])
  const [page, setPage] = useState(0)
  const [hasMore, setHasMore] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string>()

  const hasQuery = q.trim() !== '' || categoryId !== undefined

  useEffect(() => {
    api.chains(settlement).then(setChains).catch(() => setChains([]))
  }, [settlement])

  useEffect(() => {
    setPage(0)
    if (!hasQuery) {
      setOffers([])
      return
    }
    let cancelled = false
    setLoading(true)
    setError(undefined)
    api
      .search({ q, settlement, category: categoryId, chain: chainId, sort })
      .then((res) => {
        if (cancelled) return
        setOffers(res.items)
        setHasMore(res.hasMore)
      })
      .catch((e) => !cancelled && setError(e.message))
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [q, settlement, categoryId, chainId, sort, hasQuery])

  const loadMore = () => {
    const next = page + 1
    setLoading(true)
    api
      .search({ q, settlement, category: categoryId, chain: chainId, sort, page: next })
      .then((res) => {
        setOffers((prev) => [...prev, ...res.items])
        setHasMore(res.hasMore)
        setPage(next)
      })
      .catch((e) => setError(e.message))
      .finally(() => setLoading(false))
  }

  const setParam = (key: string, value?: string) => {
    const next = new URLSearchParams(params)
    if (value) next.set(key, value)
    else next.delete(key)
    setParams(next)
  }

  const cat = category(categoryId)
  const title = cat ? cat.name : q ? `„${q}“` : 'Сравни цени'

  return (
    <div>
      <PageTitle
        title={title}
        subtitle={hasQuery ? `Най-ниските цени по вериги в ${settlementName}` : 'Потърсете продукт или изберете категория'}
        actions={
          cat && (
            <button
              className="btn-primary"
              onClick={() => {
                add({ categoryId: cat.id, keyword: q || undefined })
                toast(`Добавено в списъка: ${cat.name}`)
              }}
            >
              <FontAwesomeIcon icon={faCartPlus} /> Добави „{cat.name}“ в списъка
            </button>
          )
        }
      />

      <div className="mb-4">
        <SearchBox initial={q} />
      </div>

      <div className="mb-6 grid gap-3 sm:grid-cols-3">
        <select
          className="input"
          value={categoryId ?? ''}
          onChange={(e) => setParam('category', e.target.value)}
          aria-label="Категория"
        >
          <option value="">Всички категории</option>
          {groups.map((g) => (
            <optgroup key={g.id} label={g.name}>
              {g.categories.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </optgroup>
          ))}
        </select>
        <select className="input" value={chainId ?? ''} onChange={(e) => setParam('chain', e.target.value)} aria-label="Верига">
          <option value="">Всички вериги</option>
          {chains.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name} ({c.storeCount})
            </option>
          ))}
        </select>
        <select className="input" value={sort} onChange={(e) => setParam('sort', e.target.value)} aria-label="Подреждане">
          {SORTS.map(([value, label]) => (
            <option key={value} value={value}>
              Подреди: {label}
            </option>
          ))}
        </select>
      </div>

      {error && <ErrorMessage message={error} />}

      {!hasQuery ? (
        <EmptyState icon={faMagnifyingGlass} title="Какво търсите?">
          Въведете име на продукт (напр. „кисело мляко“, „олио бисер“) или изберете категория.
        </EmptyState>
      ) : loading && offers.length === 0 ? (
        <Spinner />
      ) : offers.length === 0 && !error ? (
        <EmptyState icon={faMagnifyingGlass} title="Няма намерени продукти">
          Опитайте с по-кратка дума или изберете друго населено място.
        </EmptyState>
      ) : (
        <>
          <OfferList offers={offers} keyword={q || undefined} />
          {hasMore && (
            <div className="mt-6 text-center">
              <button className="btn-secondary" onClick={loadMore} disabled={loading}>
                {loading ? 'Зареждане…' : 'Покажи още'}
              </button>
            </div>
          )}
        </>
      )}
    </div>
  )
}
