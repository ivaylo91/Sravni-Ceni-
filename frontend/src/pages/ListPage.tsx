import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import {
  faChevronDown, faChevronUp, faCircleCheck, faCircleXmark, faFloppyDisk, faPiggyBank, faPlus,
  faRoute, faStore, faTrash, faTrophy, faCartShopping,
} from '@fortawesome/free-solid-svg-icons'
import { QuantityControl } from '../components/QuantityControl'
import { ChainBadge, EmptyState, ErrorMessage, PageTitle, Spinner } from '../components/ui'
import { useToast } from '../components/Toast'
import { useCatalog } from '../context/CatalogContext'
import { useList } from '../context/ListContext'
import { useAuth } from '../context/AuthContext'
import { api } from '../lib/api'
import { authEnabled } from '../lib/supabase'
import { formatPrice, pluralize, unitLabel } from '../lib/format'
import { groupIcon } from '../lib/icons'
import type { BasketLine, BasketResult, Chain, ListItem, StoreBasket } from '../lib/types'

export function ListPage() {
  const { items, update, remove, clear, savedList } = useList()
  const { settlement, settlementName, category } = useCatalog()
  const [chains, setChains] = useState<Chain[]>([])
  const [excluded, setExcluded] = useState<number[]>([])
  const [result, setResult] = useState<BasketResult>()
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string>()

  useEffect(() => {
    api.chains(settlement).then(setChains).catch(() => setChains([]))
    setExcluded([])
  }, [settlement])

  const chainIds = useMemo(
    () => (excluded.length ? chains.filter((c) => !excluded.includes(c.id)).map((c) => c.id) : undefined),
    [chains, excluded],
  )

  // Преизчисляваме автоматично при всяка промяна (с малко закъснение при натискане на +/−)
  useEffect(() => {
    if (items.length === 0) {
      setResult(undefined)
      return
    }
    let cancelled = false
    const timer = setTimeout(() => {
      setLoading(true)
      setError(undefined)
      api
        .basket(settlement, items, chainIds)
        .then((r) => !cancelled && setResult(r))
        .catch((e) => !cancelled && setError(e.message))
        .finally(() => !cancelled && setLoading(false))
    }, 350)
    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [items, settlement, chainIds])

  const label = (item?: ListItem) => {
    if (!item) return ''
    const name = category(item.categoryId)?.name
    const amount = item.unit ? `${String(item.amount ?? 1).replace('.', ',')} ${unitLabel(item.unit)}` : undefined
    return [name, item.keyword, amount].filter(Boolean).join(' · ')
  }

  return (
    <div>
      <PageTitle
        title={savedList ? savedList.name : 'Моят списък'}
        subtitle={`Най-евтината кошница в ${settlementName}`}
        actions={
          items.length > 0 && (
            <button className="btn-ghost text-red-600" onClick={clear}>
              <FontAwesomeIcon icon={faTrash} /> Изчисти
            </button>
          )
        }
      />

      <div className="grid gap-6 lg:grid-cols-5">
        <div className="space-y-4 lg:col-span-2">
          <AddItemForm />

          {items.length === 0 ? (
            <EmptyState icon={faCartShopping} title="Списъкът е празен">
              Добавете продукти от формата по-горе или от{' '}
              <Link to="/search" className="font-semibold text-brand-700 hover:underline">
                търсенето
              </Link>
              .
            </EmptyState>
          ) : (
            <ul className="card divide-y divide-slate-100">
              {items.map((item) => (
                <li key={item.id} className="flex flex-wrap items-center gap-x-3 gap-y-2 px-4 py-3">
                  <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-brand-50 text-brand-600">
                    <FontAwesomeIcon icon={groupIcon(category(item.categoryId)?.groupIcon)} />
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="truncate font-semibold text-slate-800">
                      {category(item.categoryId)?.name ?? 'Всякакъв продукт'}
                    </div>
                    {item.keyword && <div className="truncate text-xs text-slate-500">съдържа „{item.keyword}“</div>}
                    {item.unit && (
                      <div className="text-xs text-brand-700">
                        сравнява цена за {String(item.amount ?? 1).replace('.', ',')} {unitLabel(item.unit)}
                      </div>
                    )}
                  </div>
                  <QuantityControl item={item} onChange={(patch) => update(item.id, patch)} />
                  <button
                    className="h-8 w-8 text-slate-400 hover:text-red-600"
                    onClick={() => remove(item.id)}
                    aria-label={`Премахни ${label(item)}`}
                  >
                    <FontAwesomeIcon icon={faTrash} />
                  </button>
                </li>
              ))}
            </ul>
          )}

          {items.length > 0 && <SaveListPanel />}

          {chains.length > 1 && (
            <div className="card p-4">
              <h2 className="mb-2 text-sm font-bold text-slate-700">Вериги, в които пазарувате</h2>
              <div className="flex flex-wrap gap-2">
                {chains.map((c) => {
                  const on = !excluded.includes(c.id)
                  return (
                    <button
                      key={c.id}
                      onClick={() => setExcluded((prev) => (on ? [...prev, c.id] : prev.filter((id) => id !== c.id)))}
                      className={`rounded-lg border px-2.5 py-1 text-xs font-semibold transition ${
                        on ? 'border-brand-600 bg-brand-50 text-brand-800' : 'border-slate-200 text-slate-400 line-through'
                      }`}
                      aria-pressed={on}
                    >
                      {c.name}
                    </button>
                  )
                })}
              </div>
            </div>
          )}
        </div>

        <div className="lg:col-span-3">
          {error && <ErrorMessage message={error} />}
          {items.length > 0 && !result && loading && <Spinner label="Търсим най-евтината кошница…" />}
          {result && (
            <div className={`space-y-6 transition-opacity ${loading ? 'opacity-60' : ''}`}>
              <BasketSummary result={result} />
              <ChainComparison result={result} items={items} label={label} />
              <SplitDetails result={result} items={items} label={label} />
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

function AddItemForm() {
  const { groups } = useCatalog()
  const { add } = useList()
  const [categoryId, setCategoryId] = useState('')
  const [keyword, setKeyword] = useState('')

  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (!categoryId && !keyword.trim()) return
    add({ categoryId: categoryId ? Number(categoryId) : undefined, keyword })
    setKeyword('')
  }

  return (
    <form onSubmit={submit} className="card space-y-3 p-4">
      <h2 className="text-sm font-bold text-slate-700">Добави продукт</h2>
      <select className="input" value={categoryId} onChange={(e) => setCategoryId(e.target.value)} aria-label="Категория">
        <option value="">Категория (по избор)</option>
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
      <div className="flex gap-2">
        <input
          className="input"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          placeholder="Марка или дума, напр. „верея“"
          maxLength={100}
          aria-label="Ключова дума"
        />
        <button type="submit" className="btn-primary shrink-0" disabled={!categoryId && !keyword.trim()}>
          <FontAwesomeIcon icon={faPlus} /> Добави
        </button>
      </div>
      <p className="text-xs text-slate-500">
        Избираме най-евтиния продукт от категорията, който съдържа думата, във всеки магазин.
      </p>
    </form>
  )
}

function SaveListPanel() {
  const { session } = useAuth()
  const { items, savedList, setSavedList } = useList()
  const toast = useToast()
  const [name, setName] = useState(savedList?.name ?? '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()

  useEffect(() => setName(savedList?.name ?? ''), [savedList?.name])

  if (!authEnabled) return null
  if (!session) {
    return (
      <p className="text-sm text-slate-500">
        <Link to="/login" className="font-semibold text-brand-700 hover:underline">
          Влезте
        </Link>
        , за да запазите списъка и да го ползвате на всички устройства.
      </p>
    )
  }

  const save = async (asNew: boolean) => {
    if (!name.trim()) return
    setBusy(true)
    setError(undefined)
    try {
      const saved =
        savedList && !asNew ? await api.updateList(savedList.id, name, items) : await api.createList(name, items)
      setSavedList({ id: saved.id, name: saved.name })
      toast('Списъкът е запазен')
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="card space-y-2 p-4">
      <div className="flex gap-2">
        <input
          className="input"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Име на списъка, напр. „Седмично пазаруване“"
          maxLength={120}
          aria-label="Име на списъка"
        />
        <button className="btn-primary shrink-0" onClick={() => save(false)} disabled={busy || !name.trim()}>
          <FontAwesomeIcon icon={faFloppyDisk} /> {savedList ? 'Запази' : 'Запази списъка'}
        </button>
      </div>
      {savedList && (
        <button className="text-xs font-semibold text-brand-700 hover:underline" onClick={() => save(true)} disabled={busy}>
          Запази като нов списък
        </button>
      )}
      {error && <ErrorMessage message={error} />}
    </div>
  )
}

function BasketSummary({ result }: { result: BasketResult }) {
  const best = result.bestStores[0]
  if (!best) {
    return (
      <EmptyState icon={faStore} title="Няма намерени продукти">
        Нито един магазин в избраното населено място не предлага продуктите от списъка.
      </EmptyState>
    )
  }
  return (
    <div className="grid gap-4 sm:grid-cols-3">
      <div className="card border-brand-200 bg-brand-50 p-5 sm:col-span-3">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div className="min-w-0">
            <div className="mb-1 flex items-center gap-2 text-sm font-bold text-brand-800">
              <FontAwesomeIcon icon={faTrophy} /> Най-евтин магазин
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <ChainBadge name={best.chainName} color={best.chainColor} />
              <span className="text-sm break-words text-slate-700">{best.address}</span>
            </div>
            <Coverage found={best.foundCount} total={result.itemCount} />
          </div>
          <div className="price text-3xl font-extrabold text-brand-800">{formatPrice(best.total)}</div>
        </div>
      </div>
      <div className="card p-5">
        <div className="flex items-center gap-2 text-sm font-bold text-slate-600">
          <FontAwesomeIcon icon={faRoute} className="text-brand-600" /> От няколко магазина
        </div>
        <div className="price mt-2 text-2xl font-extrabold text-slate-900">{formatPrice(result.split.total)}</div>
        <div className="text-xs text-slate-500">в {pluralize(result.split.storeCount, 'магазин', 'магазина')}</div>
      </div>
      <div className="card p-5 sm:col-span-2">
        <div className="flex items-center gap-2 text-sm font-bold text-slate-600">
          <FontAwesomeIcon icon={faPiggyBank} className="text-brand-600" /> Спестявате до
        </div>
        <div className="price mt-2 text-2xl font-extrabold text-slate-900">{formatPrice(result.savings)}</div>
        <div className="text-xs text-slate-500">спрямо най-скъпата верига със същите продукти</div>
      </div>
    </div>
  )
}

function Coverage({ found, total }: { found: number; total: number }) {
  const all = found === total
  return (
    <div className={`mt-1 text-xs font-semibold ${all ? 'text-brand-700' : 'text-amber-700'}`}>
      <FontAwesomeIcon icon={all ? faCircleCheck : faCircleXmark} className="mr-1" />
      {all ? 'Всички продукти са налични' : `Налични ${found} от ${total} продукта`}
    </div>
  )
}

interface DetailProps {
  result: BasketResult
  items: ListItem[]
  label: (item?: ListItem) => string
}

function ChainComparison({ result, items, label }: DetailProps) {
  const [open, setOpen] = useState<number>()
  if (result.bestPerChain.length === 0) return null
  const max = Math.max(...result.bestPerChain.map((b) => b.total))

  return (
    <section>
      <h2 className="mb-3 text-lg font-bold text-slate-900">Сравнение по вериги</h2>
      <ul className="card divide-y divide-slate-100">
        {result.bestPerChain.map((b: StoreBasket, i) => (
          <li key={b.storeId} className="px-4 py-3">
            <button
              className="flex w-full items-center gap-3 text-left"
              onClick={() => setOpen(open === b.storeId ? undefined : b.storeId)}
              aria-expanded={open === b.storeId}
            >
              <span className="price w-5 text-sm font-bold text-slate-400">{i + 1}.</span>
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <ChainBadge name={b.chainName} color={b.chainColor} size="sm" />
                  <span className="truncate text-xs text-slate-500">{b.address}</span>
                </div>
                <div className="mt-2 h-2 overflow-hidden rounded-full bg-slate-100">
                  <div
                    className={`h-full rounded-full ${i === 0 ? 'bg-brand-500' : 'bg-slate-300'}`}
                    style={{ width: `${Math.max(4, (b.total / max) * 100)}%` }}
                  />
                </div>
                <Coverage found={b.foundCount} total={result.itemCount} />
              </div>
              <span className="price text-lg font-extrabold text-slate-900">{formatPrice(b.total)}</span>
              <FontAwesomeIcon icon={open === b.storeId ? faChevronUp : faChevronDown} className="text-slate-400" />
            </button>
            {open === b.storeId && (
              <div className="mt-3 ml-8">
                <Lines lines={b.lines} items={items} label={label} />
                {b.missingItems.length > 0 && (
                  <p className="mt-2 text-xs text-amber-700">
                    Липсват: {b.missingItems.map((i) => label(items[i])).join(', ')}
                  </p>
                )}
              </div>
            )}
          </li>
        ))}
      </ul>
    </section>
  )
}

function SplitDetails({ result, items, label }: DetailProps) {
  if (result.split.storeCount < 2) return null
  const byStore = new Map<number, BasketLine[]>()
  result.split.lines.forEach((l) => byStore.set(l.storeId, [...(byStore.get(l.storeId) ?? []), l]))

  return (
    <section>
      <h2 className="mb-1 text-lg font-bold text-slate-900">Пазаруване от няколко магазина</h2>
      <p className="mb-3 text-sm text-slate-500">Всеки продукт от магазина, където е най-евтин.</p>
      <div className="space-y-3">
        {[...byStore.values()].map((lines) => (
          <div key={lines[0].storeId} className="card p-4">
            <div className="mb-2 flex flex-wrap items-center gap-2">
              <span className="text-sm font-bold text-slate-800">{lines[0].chainName}</span>
              <span className="text-xs text-slate-500">{lines[0].address}</span>
            </div>
            <Lines lines={lines} items={items} label={label} />
          </div>
        ))}
      </div>
    </section>
  )
}

function Lines({ lines, items, label }: { lines: BasketLine[]; items: ListItem[]; label: (item?: ListItem) => string }) {
  return (
    <ul className="space-y-1.5 text-sm">
      {lines.map((l) => (
        <li key={l.itemIndex} className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <Link to={`/product/${l.productId}`} className="break-words text-slate-800 hover:text-brand-700 hover:underline">
              {l.productName}
            </Link>
            <div className="text-xs text-slate-400">
              за „{label(items[l.itemIndex])}“ ·{' '}
              {l.packageUnit && l.packageQty == null ? (
                // стока на тегло
                <>
                  {String(items[l.itemIndex]?.amount ?? l.quantity).replace('.', ',')} {unitLabel(l.packageUnit)} ×{' '}
                  {formatPrice(l.price)}/{unitLabel(l.packageUnit)}
                </>
              ) : (
                <>
                  {l.quantity} × {formatPrice(l.price)}
                  {items[l.itemIndex]?.unit && l.packageQty && (
                    <> ({l.quantity} × {String(l.packageQty).replace('.', ',')} {unitLabel(l.packageUnit)})</>
                  )}
                </>
              )}
              {l.promo && <span className="ml-1 font-semibold text-red-600">промоция</span>}
            </div>
          </div>
          <span className="price font-semibold whitespace-nowrap">{formatPrice(l.lineTotal)}</span>
        </li>
      ))}
    </ul>
  )
}
