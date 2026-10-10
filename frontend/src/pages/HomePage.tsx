import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faCartShopping, faListCheck, faScaleBalanced, faTrophy } from '@fortawesome/free-solid-svg-icons'
import { SearchBox } from '../components/SearchBox'
import { ErrorMessage } from '../components/ui'
import { useCatalog } from '../context/CatalogContext'
import { api } from '../lib/api'
import { formatDate, formatNumber } from '../lib/format'
import { groupIcon } from '../lib/icons'
import type { Stats } from '../lib/types'

const QUICK = ['прясно мляко', 'хляб', 'яйца', 'сирене', 'олио', 'банани']

export function HomePage() {
  const { groups, settlementName, error } = useCatalog()
  const [stats, setStats] = useState<Stats>()

  useEffect(() => {
    api.stats().then(setStats).catch(() => {})
  }, [])

  return (
    <div className="space-y-12">
      <section className="relative overflow-hidden rounded-3xl bg-gradient-to-br from-brand-700 via-brand-600 to-emerald-500 px-6 py-12 text-white sm:px-12 sm:py-16">
        <div className="relative max-w-2xl">
          <p className="mb-3 inline-block rounded-full bg-white/15 px-3 py-1 text-sm font-semibold">
            Цени в {settlementName} · данни от КЗП
          </p>
          <h1 className="text-3xl leading-tight font-extrabold tracking-tight sm:text-5xl">
            Пазарувайте умно.
            <br />
            Намерете най-евтината кошница.
          </h1>
          <p className="mt-4 text-lg text-white/85">
            Сравнете цените в Kaufland, Lidl, Billa, Фантастико, T Market и други вериги и вижте къде ще платите най-малко
            за целия си списък.
          </p>
          <div className="mt-8">
            <SearchBox large />
          </div>
          <div className="mt-4 flex flex-wrap gap-2">
            {QUICK.map((q) => (
              <Link
                key={q}
                to={`/search?q=${encodeURIComponent(q)}`}
                className="rounded-full bg-white/15 px-3 py-1 text-sm font-semibold hover:bg-white/25"
              >
                {q}
              </Link>
            ))}
          </div>
        </div>
        <FontAwesomeIcon
          icon={faCartShopping}
          className="pointer-events-none absolute -right-10 -bottom-10 hidden text-[18rem] text-white/10 md:block"
        />
      </section>

      {stats && stats.prices > 0 && (
        <section className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          {[
            ['Вериги', formatNumber(stats.chains)],
            ['Магазини', formatNumber(stats.stores)],
            ['Продукта', formatNumber(stats.products)],
            ['Обновено', stats.lastUpdate ? formatDate(stats.lastUpdate) : '–'],
          ].map(([label, value]) => (
            <div key={label} className="card px-5 py-4">
              <div className="text-sm text-slate-500">{label}</div>
              <div className="price mt-1 text-xl font-extrabold text-slate-900 sm:text-2xl">{value}</div>
            </div>
          ))}
        </section>
      )}

      <section>
        <h2 className="mb-2 text-xl font-extrabold text-slate-900">Как работи</h2>
        <div className="grid gap-4 md:grid-cols-3">
          {[
            [faScaleBalanced, 'Сравнете цените', 'Потърсете продукт и вижте най-ниската му цена във всяка верига във вашия град.'],
            [faListCheck, 'Направете списък', 'Добавете продукти с един клик. Списъкът се пази в браузъра, а с профил – и в облака.'],
            [faTrophy, 'Спестете', 'Ще ви покажем най-евтиния магазин за целия списък и колко спестявате.'],
          ].map(([icon, title, text], i) => (
            <div key={i} className="card p-5">
              <FontAwesomeIcon icon={icon as typeof faTrophy} className="text-2xl text-brand-600" />
              <h3 className="mt-3 font-bold text-slate-900">{title as string}</h3>
              <p className="mt-1 text-sm text-slate-500">{text as string}</p>
            </div>
          ))}
        </div>
      </section>

      <section>
        <h2 className="mb-4 text-xl font-extrabold text-slate-900">Категории от потребителската кошница</h2>
        {error && <ErrorMessage message={error} />}
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {groups.map((g) => (
            <div key={g.id} className="card p-5">
              <h3 className="mb-3 flex items-center gap-2 font-bold text-slate-900">
                <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-brand-50 text-brand-600">
                  <FontAwesomeIcon icon={groupIcon(g.icon)} />
                </span>
                {g.name}
              </h3>
              <div className="flex flex-wrap gap-1.5">
                {g.categories.map((c) => (
                  <Link
                    key={c.id}
                    to={`/search?category=${c.id}`}
                    className="rounded-lg bg-slate-100 px-2 py-1 text-xs font-medium text-slate-700 hover:bg-brand-100 hover:text-brand-800"
                  >
                    {c.name}
                  </Link>
                ))}
              </div>
            </div>
          ))}
        </div>
      </section>
    </div>
  )
}
